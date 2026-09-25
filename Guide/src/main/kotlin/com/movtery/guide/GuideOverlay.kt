package com.movtery.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * 激活引导流的覆盖层：解析锚点、绘制遮罩与镂空、裁决点击、展示引导内容。
 * 遮罩由唯一的 [Scrim] 层绘制，与步骤内容相互独立
 */
@Composable
internal fun GuideOverlay(
    controller: GuideController,
    state: GuideState.Active,
    registry: GuideRegistry,
    fadeAlpha: Float,
    animations: GuideAnimations
) {
    val entry = state.entry

    // 观察注册表版本，解析当前步骤的锚点边界
    val version = registry.version
    val anchors = remember(entry.key, version) { registry.rectsFor(entry.key) }

    // 回写锚点，供应用侧通过 GuideState.Active.anchors 观察就绪情况
    LaunchedEffect(state.index, anchors) {
        // 介绍步骤无锚点，不回写
        if (!entry.isIntro) controller.updateAnchors(state.index, anchors)
    }

    // 锚点缺失时，轮询询问容器侧注册的定位能力直到锚点就绪
    // 容器可能比引导步骤更晚组合，故反复询问
    LaunchedEffect(state.index) {
        if (entry.isIntro) return@LaunchedEffect
        var handled = 0
        while (registry.rectsFor(entry.key).isEmpty() && handled < 6) {
            if (registry.requestScroll(entry.key)) handled++
            delay(600.milliseconds)
        }
    }

    var contentRect by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val gapPx = with(density) { GuideDefaults.contentGap.toPx() }
    val paddingPx = with(density) { GuideDefaults.screenPadding.toPx() }
    val radiusPx = with(density) { controller.holeRadius.toPx() }
    val borderWidthPx = with(density) { controller.holeBorderWidth.toPx() }
    val layoutDirection = LocalLayoutDirection.current

    CompositionLocalProvider(LocalContentColor provides controller.colors.content) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .sharePointerInputWithSiblings()
        ) {
            val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
            val anchorsVisible = anchorsVisibleIn(anchors, containerSize)

            // 锚点存在但未完全入视口时，请求滚动容器将其带入视口
            LaunchedEffect(state.index, anchorsVisible) {
                if (!entry.isIntro && anchors.isNotEmpty() && !anchorsVisible) {
                    registry.bringIntoView(entry.key)
                }
            }

            val ready = entry.isIntro || anchors.isNotEmpty()
            // 等待期间不存在引导内容，点击放行区域一并失效
            if (!ready && contentRect != null) contentRect = null

            // 介绍步骤与未入视口的锚点固定居中展示，入视口后按策略求解
            val sideHint = registry.sideHintFor(entry.key)
            val effectivePlacement = when {
                entry.isIntro || !anchorsVisible -> GuidePlacement.Fixed(Alignment.Center)
                else -> resolvePlacement(entry.placement, sideHint, anchors)
            }

            Scrim(
                controller = controller,
                holes = anchors,
                ready = ready,
                fadeAlpha = fadeAlpha,
                radiusPx = radiusPx,
                borderWidthPx = borderWidthPx,
                contentRect = contentRect,
                isIntro = entry.isIntro,
                nodeClick = entry.nodeClick,
                advanceOnScrimClick = entry.advanceOnScrimClick,
                animations = animations,
                onNext = controller::next
            )

            GuideCardContainer(
                controller = controller,
                state = state,
                anchors = anchors,
                placement = effectivePlacement,
                visible = ready,
                fadeAlpha = fadeAlpha,
                animations = animations,
                layoutDirection = layoutDirection,
                gapPx = gapPx,
                paddingPx = paddingPx,
                onPlaced = { rect -> if (contentRect != rect) contentRect = rect }
            )
        }
    }
}

/**
 * 遮罩层
 */
@Composable
private fun Scrim(
    controller: GuideController,
    holes: List<Rect>,
    ready: Boolean,
    fadeAlpha: Float,
    radiusPx: Float,
    borderWidthPx: Float,
    contentRect: Rect?,
    isIntro: Boolean,
    nodeClick: NodeClickMode,
    advanceOnScrimClick: Boolean,
    animations: GuideAnimations,
    onNext: () -> Unit
) {
    val currentHoles by rememberUpdatedState(holes)
    val currentReady by rememberUpdatedState(ready)
    val currentContentRect by rememberUpdatedState(contentRect)
    val currentIsIntro by rememberUpdatedState(isIntro)
    val currentNodeClick by rememberUpdatedState(nodeClick)
    val currentAdvanceOnScrim by rememberUpdatedState(advanceOnScrimClick)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentColors by rememberUpdatedState(controller.colors)

    val holeSpec: FiniteAnimationSpec<Float> = if (animations.enabled) {
        spring(
            dampingRatio = animations.spatialSpring.dampingRatio,
            stiffness = animations.spatialSpring.stiffness
        )
    } else {
        snap()
    }
    val animatedHoles = holes.mapIndexed { index, rect ->
        key(index) { animateHoleRect(rect, holeSpec) }
    }
    val currentAnimatedHoles by rememberUpdatedState(animatedHoles)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val scrim = currentColors.scrim
                drawRect(scrim.copy(alpha = scrim.alpha * fadeAlpha))
                if (currentReady) {
                    currentAnimatedHoles.forEach { hole ->
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = hole.topLeft,
                            size = hole.size,
                            cornerRadius = CornerRadius(radiusPx),
                            blendMode = BlendMode.Clear
                        )
                        if (borderWidthPx > 0f) {
                            // 描边整体落在镂空外侧，不侵入被引导组件
                            val border = hole.inflate(borderWidthPx / 2f)
                            drawRoundRect(
                                color = currentColors.holeBorder,
                                topLeft = border.topLeft,
                                size = border.size,
                                cornerRadius = CornerRadius(radiusPx + borderWidthPx / 2f),
                                style = Stroke(width = borderWidthPx)
                            )
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val startPos = down.position
                    val hole = currentHoles.firstOrNull { it.contains(startPos) }
                    val inContent = currentContentRect?.contains(startPos) == true

                    // 引导内容自身与放行模式的锚点只观察不消费，其余一律拦截
                    val observeOnly = !currentIsIntro && (inContent ||
                            (hole != null && currentNodeClick == NodeClickMode.PassThrough))
                    if (!observeOnly) down.consume()

                    var pressed = true
                    var isTap = true
                    val slop = viewConfiguration.touchSlop

                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        var up = false
                        event.changes.forEach { change ->
                            if (change.id == down.id) {
                                if (!change.pressed && change.previousPressed) {
                                    up = true
                                } else if (abs(change.position.x - startPos.x) > slop ||
                                    abs(change.position.y - startPos.y) > slop
                                ) {
                                    isTap = false
                                }
                            }
                            if (!observeOnly) change.consume()
                        }
                        if (up) pressed = false
                    }

                    if (isTap) {
                        when {
                            currentIsIntro -> currentOnNext()
                            inContent -> Unit
                            hole != null -> currentOnNext()
                            currentAdvanceOnScrim -> currentOnNext()
                        }
                    }
                }
            }
    )
}

/**
 * 镂空矩形的弹性随动：锚点位置变化时聚光灯平滑跟随
 */
@Composable
private fun animateHoleRect(target: Rect, spec: FiniteAnimationSpec<Float>): Rect {
    val left by animateFloatAsState(target.left, spec, label = "holeLeft")
    val top by animateFloatAsState(target.top, spec, label = "holeTop")
    val right by animateFloatAsState(target.right, spec, label = "holeRight")
    val bottom by animateFloatAsState(target.bottom, spec, label = "holeBottom")
    return Rect(left, top, right, bottom)
}

/**
 * 引导内容容器，测量内容并按放置策略求解目标位置，
 * 位置以弹性动画随步骤移动，步骤内容切换时淡入淡出
 */
@Composable
private fun GuideCardContainer(
    controller: GuideController,
    state: GuideState.Active,
    anchors: List<Rect>,
    placement: GuidePlacement,
    visible: Boolean,
    fadeAlpha: Float,
    animations: GuideAnimations,
    layoutDirection: LayoutDirection,
    gapPx: Float,
    paddingPx: Float,
    onPlaced: (Rect) -> Unit
) {
    val entry = state.entry
    val sideState = remember { mutableStateOf<GuideSide?>(null) }
    // 首次求解前为 null，此时直接摆放求解结果，避免内容从原点飞入
    val targetState = remember { mutableStateOf<IntOffset?>(null) }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (animations.enabled) {
            spring(
                dampingRatio = animations.fadeSpring.dampingRatio,
                stiffness = animations.fadeSpring.stiffness
            )
        } else {
            snap()
        },
        label = "guideContentAlpha"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fadeAlpha }
    ) {
        val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val scope = remember(anchors, containerSize, controller) {
            GuideScope(anchors, containerSize, sideState, controller)
        }
        val animatedOffset = targetState.value?.let { target ->
            animateIntOffsetAsState(
                targetValue = target,
                animationSpec = if (animations.enabled) {
                    spring(
                        dampingRatio = animations.spatialSpring.dampingRatio,
                        stiffness = animations.spatialSpring.stiffness
                    )
                } else {
                    snap()
                },
                label = "guideCardOffset"
            )
        }

        Layout(
            content = {
                AnimatedContent(
                    targetState = entry,
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                    transitionSpec = {
                        val spec: FiniteAnimationSpec<Float> = if (animations.enabled) {
                            spring(
                                dampingRatio = animations.fadeSpring.dampingRatio,
                                stiffness = animations.fadeSpring.stiffness
                            )
                        } else {
                            snap()
                        }
                        fadeIn(spec) togetherWith fadeOut(spec)
                    },
                    label = "guideCardContent"
                ) { stepEntry -> stepEntry.content(scope) }
            }
        ) { measurables, constraints ->
            val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
            val placeables = measurables.map { it.measure(loose) }
            val contentSize = IntSize(
                placeables.maxOf { it.width },
                placeables.maxOf { it.height }
            )
            // 锚点未就绪时保持上次求解的位置，仅随 alpha 淡出
            val solved = if (entry.isIntro || anchors.isNotEmpty()) {
                solvePlacement(
                    placement = placement,
                    anchors = anchors,
                    contentSize = contentSize,
                    containerSize = containerSize,
                    layoutDirection = layoutDirection,
                    gapPx = gapPx,
                    paddingPx = paddingPx
                ).also {
                    if (sideState.value != it.side) sideState.value = it.side
                    if (targetState.value != it.offset) targetState.value = it.offset
                    onPlaced(
                        Rect(
                            it.offset.x.toFloat(),
                            it.offset.y.toFloat(),
                            (it.offset.x + contentSize.width).toFloat(),
                            (it.offset.y + contentSize.height).toFloat()
                        )
                    )
                }
            } else {
                null
            }

            val offset = animatedOffset?.value ?: solved?.offset ?: IntOffset.Zero
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { it.place(offset.x, offset.y) }
            }
        }
    }
}
