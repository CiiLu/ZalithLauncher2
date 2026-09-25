package com.movtery.guide

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 放置策略求解结果
 */
internal data class PlacementResult(
    val offset: IntOffset,
    val side: GuideSide?
)

/**
 * 全部锚点的包围盒
 */
internal fun List<Rect>.boundsUnion(): Rect {
    if (isEmpty()) return Rect.Zero
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    forEach { rect ->
        left = min(left, rect.left)
        top = min(top, rect.top)
        right = max(right, rect.right)
        bottom = max(bottom, rect.bottom)
    }
    return Rect(left, top, right, bottom)
}

/**
 * 求解引导内容的摆放位置：
 * 以锚点包围盒的中心（多个锚点时即其中心点）为参照，内容尽可能贴近该点；
 * 不遮挡锚点、不出屏为硬约束，可行解中取内容中心距参照点最近者；
 * 全部不可行时按「锚点交叠面积 → 出屏量 → 距参照点距离」字典序兜底
 */
internal fun solvePlacement(
    placement: GuidePlacement,
    anchors: List<Rect>,
    contentSize: IntSize,
    containerSize: IntSize,
    layoutDirection: LayoutDirection,
    gapPx: Float,
    paddingPx: Float
): PlacementResult {
    if (placement is GuidePlacement.Fixed) {
        val base = placement.alignment.align(contentSize, containerSize, layoutDirection)
        return PlacementResult(base + placement.offset, side = null)
    }

    val width = contentSize.width.toFloat()
    val height = contentSize.height.toFloat()
    val bounds = anchors.boundsUnion()
    val anchorCenterX = bounds.left + bounds.width / 2f
    val anchorCenterY = bounds.top + bounds.height / 2f

    fun clamp(x: Float, y: Float): Pair<Float, Float> {
        val maxX = (containerSize.width - paddingPx - width).coerceAtLeast(paddingPx)
        val maxY = (containerSize.height - paddingPx - height).coerceAtLeast(paddingPx)
        return x.coerceIn(paddingPx, maxX) to y.coerceIn(paddingPx, maxY)
    }

    fun candidate(side: GuideSide): Pair<Float, Float> {
        val centerX = bounds.left + (bounds.width - width) / 2f
        val centerY = bounds.top + (bounds.height - height) / 2f
        val startLeft = layoutDirection == LayoutDirection.Ltr
        return when (side) {
            GuideSide.Above -> centerX to (bounds.top - gapPx - height)
            GuideSide.Below -> centerX to (bounds.bottom + gapPx)
            GuideSide.Start -> {
                val x = if (startLeft) bounds.left - gapPx - width else bounds.right + gapPx
                x to centerY
            }
            GuideSide.End -> {
                val x = if (startLeft) bounds.right + gapPx else bounds.left - gapPx - width
                x to centerY
            }
        }
    }

    val sides = listOf(GuideSide.Below, GuideSide.Above, GuideSide.Start, GuideSide.End)
    val ordered: List<GuideSide> = when (val p = placement) {
        is GuidePlacement.PreferSide -> listOf(p.side) + (sides - p.side)
        else -> sides
    }

    fun evaluate(side: GuideSide): Evaluated {
        val (x, y) = clamp(candidate(side).first, candidate(side).second)
        val rect = Rect(x, y, x + width, y + height)
        val overlap = anchors.fold(0f) { acc, anchor -> acc + anchor.overlapArea(rect) }
        val out = max(0f, paddingPx - x) + max(0f, x + width - (containerSize.width - paddingPx)) +
                max(0f, paddingPx - y) + max(0f, y + height - (containerSize.height - paddingPx))
        val distance = hypot(rect.center.x - anchorCenterX, rect.center.y - anchorCenterY).toDouble()
        return Evaluated(side, x, y, overlap, out, distance)
    }

    val candidates = ordered.map(::evaluate)
    val feasible = candidates.filter { it.overlap <= 0f && it.out <= 0f }
    val chosen = when {
        placement is GuidePlacement.PreferSide &&
                candidates.first().overlap <= 0f && candidates.first().out <= 0f -> candidates.first()
        feasible.isNotEmpty() -> feasible.minBy { it.distance }
        else -> candidates.minWith(compareBy({ it.overlap }, { it.out }, { it.distance }))
    }
    return PlacementResult(IntOffset(chosen.x.roundToInt(), chosen.y.roundToInt()), chosen.side)
}

/**
 * 单个候选位置
 */
private data class Evaluated(
    val side: GuideSide,
    val x: Float,
    val y: Float,
    val overlap: Float,
    val out: Float,
    val distance: Double
)

private fun Rect.overlapArea(other: Rect): Float {
    val left = maxOf(left, other.left)
    val top = maxOf(top, other.top)
    val right = minOf(right, other.right)
    val bottom = minOf(bottom, other.bottom)
    if (right <= left || bottom <= top) return 0f
    return (right - left) * (bottom - top)
}
