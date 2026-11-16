package com.streamtv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Where a list is scrolled to: the share of it on screen, and how far down it is (0 top, 1 bottom). */
private data class ScrollSpot(val shown: Float, val progress: Float)

/** [fits]: how many items one screen holds. Counted from the item size, not from what is on screen,
 *  so the thumb keeps its length when a grid glides past its last row. */
private fun spot(first: Int, fits: Int, total: Int): ScrollSpot? {
    // Nothing to show when everything fits on the screen.
    if (total <= 0 || fits <= 0 || fits >= total) return null
    val shown = fits.toFloat() / total
    val progress = (first.toFloat() / (total - fits).coerceAtLeast(1)).coerceIn(0f, 1f)
    return ScrollSpot(shown, progress)
}

/** A thin, faint bar on the side of a grid that shows where in the list the viewer is. */
@Composable
fun ScrollIndicator(state: LazyGridState, modifier: Modifier = Modifier) {
    val current by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val items = info.visibleItemsInfo
            val firstItem = items.firstOrNull()
            if (firstItem == null) null else {
                val stride = (firstItem.size.height + info.mainAxisItemSpacing).coerceAtLeast(1)
                val columns = items.count { it.row == firstItem.row }.coerceAtLeast(1)
                val rows = (info.viewportSize.height / stride).coerceAtLeast(1)
                spot(state.firstVisibleItemIndex, rows * columns, info.totalItemsCount)
            }
        }
    }
    ScrollBar(current, modifier)
}

@Composable
fun ScrollIndicator(state: LazyListState, modifier: Modifier = Modifier) {
    val current by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val firstItem = info.visibleItemsInfo.firstOrNull()
            if (firstItem == null) null else {
                val stride = (firstItem.size + info.mainAxisItemSpacing).coerceAtLeast(1)
                spot(state.firstVisibleItemIndex, (info.viewportSize.height / stride).coerceAtLeast(1), info.totalItemsCount)
            }
        }
    }
    ScrollBar(current, modifier)
}

@Composable
fun ScrollIndicator(state: ScrollState, modifier: Modifier = Modifier) {
    val current by remember(state) {
        derivedStateOf {
            val max = state.maxValue
            if (max <= 0 || max == Int.MAX_VALUE || state.viewportSize <= 0) null
            else ScrollSpot(
                shown = state.viewportSize.toFloat() / (state.viewportSize + max),
                progress = state.value.toFloat() / max
            )
        }
    }
    ScrollBar(current, modifier)
}

@Composable
private fun ScrollBar(spot: ScrollSpot?, modifier: Modifier) {
    // The thumb glides to its new place instead of jumping row by row.
    val progress by animateFloatAsState(spot?.progress ?: 0f, spring(dampingRatio = .9f, stiffness = 300f), label = "scrollProgress")
    val shown by animateFloatAsState(spot?.shown ?: 1f, spring(dampingRatio = .9f, stiffness = 300f), label = "scrollShown")
    val alpha by animateFloatAsState(if (spot == null) 0f else 1f, label = "scrollAlpha")
    if (alpha == 0f) return
    BoxWithConstraints(modifier.width(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f * alpha))) {
        val thumb = maxHeight * shown.coerceIn(.08f, 1f)
        Box(
            Modifier.fillMaxWidth().height(thumb).offset(y = (maxHeight - thumb) * progress)
                .clip(CircleShape).background(Color(0xFFF2683F).copy(alpha = .7f * alpha))
        )
    }
}
