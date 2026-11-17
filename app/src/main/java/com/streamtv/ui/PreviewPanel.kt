package com.streamtv.ui

import android.view.ViewGroup
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
import androidx.media3.ui.PlayerView
import com.streamtv.data.Channel
import com.streamtv.data.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The preview on Live TV: the channel picked with OK plays in a glass card where the category list
 * was, while the grid stays free to browse. OK on the picture (or OK again on the same tile) opens it
 * full screen; Close or Back puts the list back. Stops when the app leaves the screen.
 */
@Composable
fun PreviewPanel(
    channel: Channel, programs: List<EpgProgram>, favorite: Boolean,
    onFullScreen: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier
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
    val quality = channelQuality(channel.name)
    var pictureFocused by remember { mutableStateOf(false) }
    var closeFocused by remember { mutableStateOf(false) }
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xCC262931), Color(0xCC131519))))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(18.dp))
            .padding(9.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                .border(if (pictureFocused) 2.dp else 0.dp, if (pictureFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
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
                    Modifier.align(Alignment.Center).size(26.dp), color = Color.White.copy(alpha = .9f),
                    trackColor = Color.White.copy(alpha = .14f), strokeWidth = 2.dp
                )
            }
            Text(
                t("LIVE"), Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFE5484D)).padding(horizontal = 6.dp, vertical = 1.dp),
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(channelDisplayName(channel.name), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t(channel.group.ifBlank { "Other" }), color = Color(0xFF99999E), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (quality.isNotEmpty()) PreviewPill(quality)
                if (favorite) PreviewPill("★")
            }
        }
        if (program != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                Spacer(Modifier.width(7.dp))
                Text(program.title, color = Color(0xFFD6D8DE), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(
                    "  · ${t("until")} ${SimpleDateFormat("HH:mm", Locale.US).format(Date(program.endEpochMillis))}",
                    color = Color(0xFF8E9097), fontSize = 12.sp, maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Press OK again for full screen"), color = Color(0xFF8E9097), fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 2, lineHeight = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "✕  " + t("Close"),
                Modifier.clip(CircleShape)
                    .background(if (closeFocused) Color(0xFF8290A6) else Color.White.copy(alpha = .08f))
                    .onFocusChanged { closeFocused = it.isFocused }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PreviewPill(text: String) {
    Text(
        text, Modifier.clip(CircleShape).background(Color.White.copy(alpha = .08f)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = Color(0xFFD6D8DE), fontSize = 11.sp, fontWeight = FontWeight.SemiBold
    )
}
