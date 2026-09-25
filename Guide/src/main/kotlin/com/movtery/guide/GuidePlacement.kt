package com.movtery.guide

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset

/**
 * 引导内容相对锚点的方位
 */
enum class GuideSide {
    Above, Below, Start, End
}

/**
 * 引导内容相对锚点的摆放策略
 */
sealed class GuidePlacement {
    /**
     * 自动求解：不遮挡锚点、不出屏为硬约束，可行解中取最靠近屏幕中心者
     */
    data object Auto : GuidePlacement()

    /**
     * 指定方向偏好，该方向可行时优先采用，否则回落自动求解
     */
    data class PreferSide(val side: GuideSide) : GuidePlacement()

    /**
     * 完全由对齐方式与偏移决定，不做约束检查
     */
    data class Fixed(
        val alignment: Alignment,
        val offset: IntOffset = IntOffset.Zero
    ) : GuidePlacement()

    companion object {
        val Above: GuidePlacement = PreferSide(GuideSide.Above)
        val Below: GuidePlacement = PreferSide(GuideSide.Below)
        val Start: GuidePlacement = PreferSide(GuideSide.Start)
        val End: GuidePlacement = PreferSide(GuideSide.End)
    }
}
