package com.streamtv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamtv.data.Channel

@Composable
fun PlayerScreen(channel: Channel, onBack: () -> Unit, onZap: (Int) -> Unit) {
    val context = LocalContext.current
    val player = remember(channel.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(channel.url))
            prepare()
            playWhenReady = true
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(exception: androidx.media3.common.PlaybackException) {
                error = exception.message ?: "Playback error"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) { onZap(1); true }
        else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) { onZap(-1); true }
        else false
    }) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = player }
        )
        Row(Modifier.fillMaxWidth().background(Color(0xFF111018)).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(channel.group, color = Color.LightGray)
                error?.let { Text(it, color = Color(0xFFFF806F)) }
            }
            TextButton(onClick = onBack) { Text("Back to channels") }
        }
    }
}
