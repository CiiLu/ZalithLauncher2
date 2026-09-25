package com.movtery.guide

import androidx.compose.runtime.Composable

/**
 * 点击引导锚点（镂空区域）时的处理方式
 */
enum class NodeClickMode {
    /**
     * 事件由引导层消费，仅推进步骤，锚点组件本身不响应
     */
    Intercept,
    /**
     * 事件放行，锚点组件照常响应，点击结束后推进步骤
     */
    PassThrough
}

/**
 * 一个引导步骤：锚点 [key]、推进配置与内容布局
 */
class GuideEntry internal constructor(
    val key: GuideKey,
    val nodeClick: NodeClickMode,
    val advanceOnScrimClick: Boolean,
    val placement: GuidePlacement,
    val content: @Composable (GuideScope) -> Unit
)
