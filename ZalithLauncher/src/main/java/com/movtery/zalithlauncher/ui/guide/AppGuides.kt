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

package com.movtery.zalithlauncher.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.guide.GuideController
import com.movtery.guide.rememberGuide
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.EventViewModel

private const val TAG = "AppGuides"

/**
 * @param mainScreen 启动器主界面的引导
 */
class AppGuides(
    val mainScreen: GuideController
) {
    /**
     * 启动指定组的引导流，启动成功后记录进度
     * @return 是否成功启动（流为空或已有其他流激活时失败）
     */
    fun start(group: GuideKeys.Keys): Boolean {
        val controller = when (group) {
            GuideKeys.Main -> mainScreen
        }
        val started = controller.start()
        if (started) {
            GuideProgress.markPlayed(group)
            Logger.info(TAG, "Guide started: ${GuideProgress.keyOf(group)}")
        }
        return started
    }
}

@Composable
fun rememberAppGuides(eventViewModel: EventViewModel): AppGuides {
    val mainScreen = rememberGuide(holeRadius = 28.dp) {
        intro {
            GuideCard(
                title = stringResource(R.string.guide_main_welcome_title, BuildKeys.LAUNCHER_NAME),
                text = stringResource(R.string.guide_main_welcome_text)
            )
        }
        entry(GuideKeys.Main.Step.Account) {
            GuideCard(stringResource(R.string.guide_main_account))
        }
        entry(GuideKeys.Main.Step.VersionList) {
            GuideCard(stringResource(R.string.guide_main_version_list))
        }
        entry(GuideKeys.Main.Step.CardDrag) {
            GuideCard(
                title = stringResource(R.string.guide_main_card_drag_title),
                text = stringResource(R.string.guide_main_card_drag_text)
            )
        }
        entry(GuideKeys.Main.Step.CardTip) {
            GuideCard(
                title = stringResource(R.string.guide_main_card_tip_title),
                text = stringResource(
                    R.string.guide_main_card_tip_text,
                    stringResource(R.string.home_add_version_card)
                )
            )
        }
    }
    val guides = remember(mainScreen) {
        AppGuides(mainScreen)
    }

    LaunchedEffect(guides) {
        eventViewModel.events.collect { event ->
            if (event is EventViewModel.Event.StartGuide) {
                guides.start(event.group)
            }
        }
    }

    return guides
}

/**
 * 发布启动引导事件
 */
fun EventViewModel.sendStartGuide(group: GuideKeys.Keys) {
    sendEvent(EventViewModel.Event.StartGuide(group))
}

/**
 * 发布启动引导事件，已播放过的引导将被忽略
 */
fun EventViewModel.sendStartGuideOnce(group: GuideKeys.Keys) {
    if (GuideProgress.isPlayed(group)) return
    sendStartGuide(group)
}

/**
 * 主界面引导的文本卡片
 */
@Composable
private fun GuideCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Box(
        modifier = modifier
            .widthIn(max = 360.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(all = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            title?.let { str ->
                Text(
                    text = str,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
