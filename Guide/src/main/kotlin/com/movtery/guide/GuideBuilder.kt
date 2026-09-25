package com.movtery.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [rememberGuide] 的步骤收集器
 */
class GuideBuilder internal constructor() {
    internal val entries = mutableListOf<GuideEntry>()

    /**
     * 声明一个引导步骤，声明顺序即步骤顺序
     */
    fun entry(
        key: GuideKey,
        nodeClick: NodeClickMode = NodeClickMode.Intercept,
        advanceOnScrimClick: Boolean = true,
        placement: GuidePlacement = GuidePlacement.Auto,
        content: @Composable (GuideScope) -> Unit
    ) {
        entries += GuideEntry(key, nodeClick, advanceOnScrimClick, placement, content)
    }
}

/**
 * 构建并记住一条引导流
 *
 * @param colors 引导层配色：遮罩、内容默认色、镂空边缘
 * @param holeRadius 镂空与边框的圆角半径
 * @param holeBorderWidth 镂空边缘描边宽度，0 表示无边框
 */
@Composable
fun rememberGuide(
    colors: GuideColors = GuideDefaults.colors,
    holeRadius: Dp = GuideDefaults.holeRadius,
    holeBorderWidth: Dp = 0.dp,
    backBehavior: GuideBack = GuideBack.Block,
    builder: GuideBuilder.() -> Unit
): GuideController = remember(colors, holeRadius, holeBorderWidth, backBehavior) {
    val result = GuideBuilder().apply(builder)
    GuideController(result.entries.toList(), colors, holeRadius, holeBorderWidth, backBehavior)
}
