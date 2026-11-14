package com.streamtv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Where the glass highlight should sit: the bounds of the focused tile, or null. */
@Stable
class GlassFocus {
    var target by mutableStateOf<Rect?>(null)
}

/** Reports this tile's bounds to [glass] while it is focused. Place it before any scale. */
fun Modifier.glassTarget(glass: GlassFocus, focused: Boolean): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mine by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(focused) {
        if (focused) {
            coords?.takeIf { it.isAttached }?.boundsInRoot()?.let { mine = it; glass.target = it }
        } else if (mine != null && glass.target == mine) {
            glass.target = null
        }
    }
    onGloballyPositioned {
        coords = it
        if (focused) { val rect = it.boundsInRoot(); mine = rect; glass.target = rect }
    }
}

/**
 * A sheet of glass that glides from tile to tile as focus moves. Put it in a Box on top of
 * the grid whose tiles use [glassTarget].
 */
@Composable
fun GlassFocusOverlay(glass: GlassFocus, radius: Dp = 14.dp, grow: Dp = 6.dp) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var last by remember { mutableStateOf<Rect?>(null) }
    val target = glass.target
    if (target != null) last = target
    val rect = last
    val glide = spring<Float>(dampingRatio = .82f, stiffness = 420f)
    val x by animateFloatAsState(rect?.left ?: 0f, glide, label = "glassX")
    val y by animateFloatAsState(rect?.top ?: 0f, glide, label = "glassY")
    val w by animateFloatAsState(rect?.width ?: 0f, glide, label = "glassW")
    val h by animateFloatAsState(rect?.height ?: 0f, glide, label = "glassH")
    val alpha by animateFloatAsState(if (target != null) 1f else 0f, tween(180), label = "glassAlpha")
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        if (rect != null && alpha > 0f) {
            val pad = with(density) { grow.toPx() }
            val shape = RoundedCornerShape(radius + grow)
            Box(
                Modifier
                    .offset { IntOffset((x - origin.x - pad).roundToInt(), (y - origin.y - pad).roundToInt()) }
                    .size(with(density) { (w + pad * 2).toDp() }, with(density) { (h + pad * 2).toDp() })
                    .alpha(alpha)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x14FFFFFF), Color(0x24FFFFFF))))
                    .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0x59FFFFFF))), shape)
            )
        }
    }
}
