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

package com.movtery.text2speech_bridge;

import com.mojang.text2speech.Narrator;

import java.nio.charset.StandardCharsets;

/**
 * 基于安卓系统语音合成的复述实现
 */
public final class AndroidNarrator implements Narrator {
    private static final AndroidNarrator INSTANCE = new AndroidNarrator();

    private static volatile boolean bridgeLoaded;

    static {
        try {
            System.loadLibrary("pojavexec");
            bridgeLoaded = true;
            System.out.println("Narrator bridge: libpojavexec loaded, narrator narration is available");
        } catch (Throwable t) {
            bridgeLoaded = false;
            System.err.println("Narrator bridge: libpojavexec unavailable, narrator narration is disabled");
            t.printStackTrace();
        }
    }

    private static volatile boolean relayLogged;

    public static AndroidNarrator getInstance() {
        return INSTANCE;
    }

    private AndroidNarrator() {
    }

    @Override
    public boolean active() {
        if (!bridgeLoaded) return false;
        try {
            return nativeIsActive();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void say(String message) {
        say(message, false);
    }

    @Override
    public void say(String message, boolean interrupt) {
        say(message, interrupt, 1.0F);
    }

    @Override
    public void say(String message, boolean interrupt, float volume) {
        if (!bridgeLoaded || message == null || message.isEmpty()) return;
        if (!relayLogged) {
            relayLogged = true;
            System.out.println("Narrator bridge: first speech request received (interrupt=" + interrupt + ", volume=" + volume + "), relaying to the Android side");
        }
        try {
            // 以 UTF-8 字节传递，规避 JNI NewStringUTF 对增补平面字符的改造问题
            nativeSpeak(message.getBytes(StandardCharsets.UTF_8), interrupt, volume);
        } catch (Throwable t) {
            System.err.println("Narrator bridge: native speech relay failed");
            t.printStackTrace();
        }
    }

    @Override
    public void clear() {
        if (!bridgeLoaded) return;
        try {
            nativeClear();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void destroy() {
        if (!bridgeLoaded) return;
        try {
            nativeDestroy();
        } catch (Throwable ignored) {
        }
    }

    private static native void nativeSpeak(byte[] message, boolean interrupt, float volume);

    private static native void nativeClear();

    private static native void nativeDestroy();

    private static native boolean nativeIsActive();
}
