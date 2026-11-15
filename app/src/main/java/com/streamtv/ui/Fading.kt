package com.streamtv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lets a scrolling list melt away at an edge instead of being cut by a hard line.
 * An edge only fades while there is more content beyond it, so the first and last rows
 * are fully visible when the list is at its start or end.
 */
fun Modifier.fadingEdges(top: Boolean, bottom: Boolean, height: Dp = 30.dp): Modifier = composed {
    val topAmount by animateFloatAsState(if (top) 1f else 0f, tween(180), label = "fadeTop")
    val bottomAmount by animateFloatAsState(if (bottom) 1f else 0f, tween(180), label = "fadeBottom")
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val band = height.toPx().coerceAtMost(size.height / 2f)
            if (topAmount > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 1f - topAmount), Color.Black), startY = 0f, endY = band
                    ),
                    topLeft = Offset.Zero, size = Size(size.width, band), blendMode = BlendMode.DstIn
                )
            }
            if (bottomAmount > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomAmount)),
                        startY = size.height - band, endY = size.height
                    ),
                    topLeft = Offset(0f, size.height - band), size = Size(size.width, band), blendMode = BlendMode.DstIn
                )
            }
        }
}
