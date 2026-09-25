package com.movtery.guide

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 引导锚点注册表，由 [GuideHost] 持有；
 * 记录当前组合中各 [GuideKey] 的锚点边界（根坐标），版本号随任意锚点变化递增
 */
internal class GuideRegistry {
    private val nodes = HashMap<GuideKey, MutableList<GuideNode>>()
    private val versionState = mutableIntStateOf(0)

    /**
     * 注册表版本号，引导层读取它以感知锚点变化
     */
    val version: Int
        get() = versionState.intValue

    fun attach(node: GuideNode) {
        nodes.getOrPut(node.key) { mutableListOf() }.add(node)
        bump()
    }

    fun detach(node: GuideNode) {
        val list = nodes[node.key] ?: return
        if (list.remove(node)) {
            if (list.isEmpty()) nodes.remove(node.key)
            bump()
        }
    }

    fun updateBounds(node: GuideNode, bounds: Rect) {
        if (node.bounds != bounds) {
            node.bounds = bounds
            bump()
        }
    }

    fun updatePreferSide(node: GuideNode, side: GuideSide?) {
        if (node.preferSide != side) {
            node.preferSide = side
            bump()
        }
    }

    /**
     * 指定 key 的全部锚点边界
     */
    fun rectsFor(key: GuideKey): List<Rect> = nodes[key].orEmpty().map(GuideNode::bounds)

    /**
     * 指定 key 的方向推荐，仅恰好一个锚点节点声明推荐时生效；多锚点时忽略推荐
     */
    fun sideHintFor(key: GuideKey): GuideSide? =
        nodes[key]?.singleOrNull()?.preferSide

    private fun bump() {
        versionState.intValue++
    }
}

/**
 * 一个被标记的引导锚点
 */
internal class GuideNode internal constructor(internal val key: GuideKey) {
    internal var bounds: Rect = Rect.Zero
    internal var preferSide: GuideSide? = null
}

/**
 * 引导锚点注册表，由 [GuideHost] 向其内容树提供；无画布时为 null
 */
internal val LocalGuideRegistry = compositionLocalOf<GuideRegistry?> { null }

/**
 * 将组件标记为引导锚点；多个组件标记同一个 [key] 时，作为同一步骤的一组锚点
 * @param preferSide 方向推荐：引导内容优先展示在组件该侧；多锚点或该侧装不下时回落自动求解
 */
fun Modifier.guideNode(key: GuideKey, preferSide: GuideSide? = null): Modifier = composed {
    val registry = LocalGuideRegistry.current ?: return@composed this
    val node = remember(key) { GuideNode(key) }
    DisposableEffect(key, registry) {
        registry.attach(node)
        onDispose { registry.detach(node) }
    }
    SideEffect { registry.updatePreferSide(node, preferSide) }
    onGloballyPositioned { coordinates ->
        registry.updateBounds(node, coordinates.boundsInRoot())
    }
}
