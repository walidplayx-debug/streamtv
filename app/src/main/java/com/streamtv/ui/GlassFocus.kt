package com.streamtv.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.geometry.lerp
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
    /** Identifies the focused tile, so a scroll (same tile, new place) is not mistaken for a move. */
    var owner by mutableStateOf<Any?>(null)
}

/** Reports this tile's bounds to [glass] while it is focused. Place it before any scale. */
fun Modifier.glassTarget(glass: GlassFocus, focused: Boolean, key: Any): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(focused) {
        if (focused) {
            coords?.takeIf { it.isAttached }?.boundsInRoot()?.let { glass.owner = key; glass.target = it }
        } else if (glass.owner == key) {
            glass.owner = null
            glass.target = null
        }
    }
    onGloballyPositioned {
        coords = it
        if (focused && glass.owner == key) glass.target = it.boundsInRoot()
    }
}

/**
 * A light sheet of glass that glides from tile to tile as focus moves. Put it in a clipping
 * Box on top of the grid whose tiles use [glassTarget].
 */
@Composable
fun GlassFocusOverlay(glass: GlassFocus, radius: Dp = 14.dp, grow: Dp = 5.dp) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = glass.target
    val owner = glass.owner
    // The glass travels from where it was drawn last to the newly focused tile.
    val travel = remember { Animatable(1f) }
    var from by remember { mutableStateOf<Rect?>(null) }
    val drawn = remember { arrayOfNulls<Rect>(1) }
    LaunchedEffect(owner) {
        if (owner == null) {
            from = null
        } else {
            from = drawn[0]
            travel.snapTo(0f)
            travel.animateTo(1f, spring(dampingRatio = .86f, stiffness = 520f))
        }
    }
    val start = from
    val rect = when {
        target == null -> null
        start == null -> target
        else -> lerp(start, target, travel.value.coerceIn(0f, 1f))
    }
    SideEffect { drawn[0] = if (target == null) null else rect }
    val alpha by animateFloatAsState(if (target != null) 1f else 0f, tween(160), label = "glassAlpha")
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        if (rect != null) {
            val pad = with(density) { grow.toPx() }
            val shape = RoundedCornerShape(radius + grow)
            Box(
                Modifier
                    .offset { IntOffset((rect.left - origin.x - pad).roundToInt(), (rect.top - origin.y - pad).roundToInt()) }
                    .size(with(density) { (rect.width + pad * 2).toDp() }, with(density) { (rect.height + pad * 2).toDp() })
                    .alpha(alpha)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x08FFFFFF), Color(0x12FFFFFF))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x33FFFFFF))), shape)
            )
        }
    }
}
