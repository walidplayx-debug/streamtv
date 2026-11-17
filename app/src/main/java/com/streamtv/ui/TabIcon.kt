package com.streamtv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The small line icons of the top tabs, drawn on a 24-unit grid so they stay sharp at any size:
 * a TV for Live TV, a magnifier for Search, a film frame for Movies, stacked screens for Series and
 * a gear for Settings.
 */
@Composable
fun TabIcon(tab: String, color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
            drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = line)
        fun DrawScope.seg(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = 2f * u, cap = StrokeCap.Round)
        when (tab) {
            "Live TV" -> { box(3f, 7f, 18f, 13f); seg(8f, 3f, 12f, 7f); seg(16f, 3f, 12f, 7f) }
            "Search" -> { drawCircle(color, 7f * u, p(11f, 11f), style = line); seg(16.2f, 16.2f, 20.5f, 20.5f) }
            "Movies" -> {
                box(3f, 4f, 18f, 16f); seg(7f, 4f, 7f, 20f); seg(17f, 4f, 17f, 20f)
                seg(3f, 9f, 7f, 9f); seg(3f, 15f, 7f, 15f); seg(17f, 9f, 21f, 9f); seg(17f, 15f, 21f, 15f)
            }
            "Series" -> { box(3f, 9f, 18f, 12f); seg(6f, 6f, 18f, 6f); seg(8.5f, 3f, 15.5f, 3f) }
            else -> {
                drawCircle(color, 3.2f * u, p(12f, 12f), style = line)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    seg(12f + 6.2f * cos(a).toFloat(), 12f + 6.2f * sin(a).toFloat(), 12f + 9f * cos(a).toFloat(), 12f + 9f * sin(a).toFloat())
                }
            }
        }
    }
}
