/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.bridge

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.annotation.Keep
import com.movtery.zalithlauncher.context.GlobalContext
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger
import java.util.ArrayDeque

private const val TAG = "TTSBridge"

/** 语音合成引擎尚未完成初始化 */
private const val STATUS_INITIALIZING = -2
/** 等待引擎初始化期间最多缓存的待播报条数，超出时丢弃最早的 */
private const val MAX_PENDING = 32

private data class PendingSpeech(val text: String, val interrupt: Boolean, val volume: Float)

@Keep
object TTSBridge {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var initStatus: Int = STATUS_INITIALIZING
    private var engineCreationRequested: Boolean = false
    private var candidateEngines: List<String> = emptyList()
    private var requestedEngine: String? = null
    private val triedEngines = mutableSetOf<String>()
    private val pending = ArrayDeque<PendingSpeech>()
    private var lastRate: Float = Float.MIN_VALUE
    private var speakCount: Int = 0

    @Keep
    @JvmStatic
    fun speak(message: ByteArray, interrupt: Boolean, volume: Float) {
        if (!AllSettings.ttsBridgeEnabled.getValue()) return

        val text = runCatching { String(message, Charsets.UTF_8) }.getOrNull() ?: return
        if (text.isBlank()) return

        val item = PendingSpeech(text, interrupt, volume)
        synchronized(lock) {
            ensureEngine()
            when (initStatus) {
                TextToSpeech.SUCCESS -> speakNow(item)
                STATUS_INITIALIZING -> {
                    pending.addLast(item)
                    while (pending.size > MAX_PENDING) pending.removeFirst()
                }
                else -> Unit
            }
        }
    }

    @Keep
    @JvmStatic
    fun stop() {
        synchronized(lock) {
            pending.clear()
            tts?.runCatching { stop() }
        }
    }

    @Keep
    @JvmStatic
    fun shutdown() {
        synchronized(lock) {
            pending.clear()
            tts?.runCatching {
                stop()
                shutdown()
            }
            tts = null
            initStatus = STATUS_INITIALIZING
            engineCreationRequested = false
            candidateEngines = emptyList()
            requestedEngine = null
            triedEngines.clear()
            lastRate = Float.MIN_VALUE
        }
    }

    private fun ensureEngine() {
        if (engineCreationRequested) return
        engineCreationRequested = true
        Logger.info(TAG, "Requesting system text-to-speech engine")

        mainHandler.post {
            val instance = runCatching {
                TextToSpeech(GlobalContext.applicationContext) { status -> handleEngineInit(status) }
            }.getOrNull()

            synchronized(lock) {
                if (instance == null) {
                    Logger.warning(TAG, "Failed to construct TextToSpeech instance")
                    initStatus = TextToSpeech.ERROR
                    return@synchronized
                }
                tts = instance
                candidateEngines = runCatching { instance.engines.map { it.name } }.getOrDefault(emptyList())
                val defaultEngine = runCatching { instance.defaultEngine }.getOrNull()
                Logger.info(TAG, "TextToSpeech instance created, default engine=$defaultEngine, installed engines=$candidateEngines")
            }
        }
    }

    private fun handleEngineInit(status: Int) {
        val notRegistered = synchronized(lock) { tts == null }
        if (notRegistered) {
            //初始化回调可能抢在创建代码块登记实例之前到达，推迟到主线程队列尾保证引擎列表已就绪
            mainHandler.post { handleEngineInit(status) }
            return
        }

        val usedEngine = synchronized(lock) {
            requestedEngine ?: tts?.runCatching { defaultEngine }?.getOrNull() ?: "system-default"
        }
        if (status == TextToSpeech.SUCCESS) {
            synchronized(lock) {
                initStatus = TextToSpeech.SUCCESS
                Logger.info(TAG, "Text-to-speech engine ready: engine=$usedEngine")
                drainPending()
            }
            return
        }

        synchronized(lock) {
            triedEngines.add(usedEngine)
            Logger.warning(TAG, "Text-to-speech engine init failed: engine=$usedEngine status=$status, tried=$triedEngines")
            val fallback = candidateEngines.firstOrNull { it !in triedEngines }
            if (fallback == null) {
                initStatus = TextToSpeech.ERROR
                pending.clear()
                val hint = if (candidateEngines.isEmpty()) {
                    "no TTS engine is visible on this device; check the system text-to-speech settings"
                } else {
                    "all installed engines failed"
                }
                Logger.warning(TAG, "No usable text-to-speech engine ($hint), speech requests will be dropped")
                return@synchronized
            }
            //默认引擎不可用时，依次尝试设备上已安装的其他语音引擎
            recreateWithEngine(fallback)
        }
    }

    private fun recreateWithEngine(engine: String) {
        Logger.info(TAG, "Retrying text-to-speech with engine: $engine")
        val failed = tts
        mainHandler.post {
            failed?.runCatching { shutdown() }

            val instance = runCatching {
                TextToSpeech(GlobalContext.applicationContext, { status -> handleEngineInit(status) }, engine)
            }.getOrNull()

            synchronized(lock) {
                if (instance == null) {
                    triedEngines.add(engine)
                    Logger.warning(TAG, "Failed to construct TextToSpeech instance for engine $engine")
                    initStatus = TextToSpeech.ERROR
                    return@synchronized
                }
                tts = instance
                requestedEngine = engine
            }
        }
    }

    private fun drainPending() {
        pending.forEach { speakNow(it) }
        pending.clear()
    }

    private fun speakNow(item: PendingSpeech) {
        val engine = tts ?: return

        val rate = AllSettings.ttsSpeechRate.getValue() / 100.0F
        if (rate != lastRate) {
            engine.setSpeechRate(rate)
            lastRate = rate
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, item.volume.coerceIn(0.0F, 1.0F))
        }
        val queueMode = if (item.interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

        speakCount++
        if (speakCount <= 3 || speakCount % 50 == 0) {
            Logger.info(TAG, "Speaking #$speakCount (interrupt=${item.interrupt}): ${item.text.take(48)}")
        }
        runCatching {
            engine.speak(item.text, queueMode, params, null)
        }.onFailure { Logger.warning(TAG, "TextToSpeech.speak failed", it) }
    }
}
