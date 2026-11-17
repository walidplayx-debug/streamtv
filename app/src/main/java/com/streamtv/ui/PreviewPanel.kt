package com.streamtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import com.streamtv.data.Channel
import com.streamtv.data.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The preview on Live TV: the channel picked with OK plays in a glass card where the category list
 * was, while the grid stays free to browse. OK on the picture (or OK again on the same tile) opens it
 * full screen; Back puts the list back. Stops when the app leaves the screen.
 */
@Composable
fun PreviewPanel(
    channel: Channel, programs: List<EpgProgram>, favorite: Boolean,
    onFullScreen: () -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember { Players.create(context) }
    var buffering by remember { mutableStateOf(true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(channel.key) {
        buffering = true
        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.playWhenReady = true
    }
    // Nothing plays behind the Home screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_START -> player.prepare()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val program = playerProgram(channel, programs)
    var pictureFocused by remember { mutableStateOf(false) }
    Column(modifier) {
        // A clean picture with square corners, like a screen; a white outline only when focused.
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                .border(if (pictureFocused) 2.dp else 0.dp, if (pictureFocused) Color.White else Color.Transparent)
                .onFocusChanged { pictureFocused = it.isFocused }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFullScreen)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        keepScreenOn = true
                        useController = false
                        isFocusable = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { it.player = player }
            )
            if (buffering) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(24.dp), color = Color.White.copy(alpha = .9f),
                    trackColor = Color.White.copy(alpha = .14f), strokeWidth = 2.dp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                t("LIVE"), Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFFE5484D)).padding(horizontal = 6.dp, vertical = 1.dp),
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(channelDisplayName(channel.name), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(t(channel.group.ifBlank { "Other" }), program?.title).joinToString(" · "),
                color = Color(0xFFC9CBD2), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            val clock = SimpleDateFormat("HH:mm", Locale.US)
            if (program != null) {
                val span = (program.endEpochMillis - program.startEpochMillis).coerceAtLeast(1L)
                val done = ((System.currentTimeMillis() - program.startEpochMillis).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth(.82f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f))) {
                    Box(Modifier.fillMaxWidth(done).fillMaxHeight().clip(CircleShape).background(Color.White))
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(
                (if (program != null) "${clock.format(Date(program.startEpochMillis))} – ${clock.format(Date(program.endEpochMillis))} · " else "") +
                    t("Press OK again for full screen"),
                color = Color(0xFF8E9097), fontSize = 10.sp, maxLines = 2, lineHeight = 13.sp
            )
        }
    }
}

