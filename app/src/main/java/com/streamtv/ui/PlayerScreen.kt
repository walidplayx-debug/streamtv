package com.streamtv.ui

import android.view.ViewGroup
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.streamtv.data.Channel
import com.streamtv.data.EpgProgram
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PlayerWhite = Color.White
private val PlayerMuted = Color(0xFFB5B5BA)
private val PlayerGlass = Color(0xDD252528)
private val PlayerAccent = Color(0xFFD5DADF)

@Composable
fun PlayerScreen(
    channel: Channel,
    channels: List<Channel>,
    programs: List<EpgProgram>,
    favorites: Set<String>,
    onBack: () -> Unit,
    onZap: (Int) -> Unit,
    onSelect: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit
) {
    val context = LocalContext.current
    val player = remember(channel.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(channel.url))
            prepare()
            playWhenReady = true
        }
    }
    var error by remember(channel.url) { mutableStateOf<String?>(null) }
    var showOverlay by remember { mutableStateOf(false) }
    var showChannelPanel by remember { mutableStateOf(false) }
    var zapLabel by remember { mutableStateOf(false) }
    val playerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }
    val favorite = channel.id in favorites
    val current = playerProgram(channel, programs)
    val next = programs.filter { (it.channelId == channel.id || it.channelId.equals(channel.name, true)) && it.startEpochMillis > System.currentTimeMillis() }
        .minByOrNull { it.startEpochMillis }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(exception: androidx.media3.common.PlaybackException) {
                error = exception.message ?: "Playback error"
                showOverlay = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(showOverlay, channel.url, showChannelPanel) {
        if (showOverlay && !showChannelPanel) {
            delay(6500)
            showOverlay = false
        }
    }
    LaunchedEffect(zapLabel, channel.id) {
        if (zapLabel) {
            delay(1800)
            zapLabel = false
        }
    }
    BackHandler {
        if (showChannelPanel) showChannelPanel = false else onBack()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(playerFocusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key(AndroidKeyEvent.KEYCODE_CHANNEL_UP.toLong()) -> {
                        onZap(1); zapLabel = true; showOverlay = false; true
                    }
                    Key(AndroidKeyEvent.KEYCODE_CHANNEL_DOWN.toLong()) -> {
                        onZap(-1); zapLabel = true; showOverlay = false; true
                    }
                    Key(AndroidKeyEvent.KEYCODE_BACK.toLong()) -> {
                        if (showChannelPanel) { showChannelPanel = false; true } else false
                    }
                    else -> when (event.key) {
                        Key.DirectionUp -> {
                            if (showChannelPanel) false else { onZap(1); zapLabel = true; showOverlay = false; true }
                        }
                        Key.DirectionDown -> {
                            if (showChannelPanel) false else { onZap(-1); zapLabel = true; showOverlay = false; true }
                        }
                        Key.DirectionRight -> {
                            showChannelPanel = true
                            showOverlay = false
                            true
                        }
                        Key.DirectionLeft -> {
                            if (showChannelPanel) { showChannelPanel = false; true } else false
                        }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (showChannelPanel) false else { showOverlay = true; true }
                        }
                        else -> false
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = player }
        )

        if (showOverlay) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .28f), Color.Black.copy(alpha = .88f))))) {
                Row(Modifier.align(Alignment.TopEnd).padding(36.dp).clip(CircleShape).background(PlayerGlass).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), color = PlayerWhite, fontSize = 18.sp)
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 58.dp, end = 58.dp, bottom = 42.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(PlayerAccent, CircleShape))
                        Spacer(Modifier.width(9.dp))
                        Text("LIVE  ·  ${channel.name}", color = PlayerMuted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(channel.name, color = PlayerWhite, fontSize = 42.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (next != null) "Next  ·  ${next.title}  ·  ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next.startEpochMillis))}" else current?.title ?: channel.group,
                        color = PlayerMuted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    error?.let {
                        Text(it, color = Color(0xFFFF9B8F), fontSize = 16.sp)
                        PlayerAction("Retry") { error = null; player.prepare(); player.playWhenReady = true }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LIVE", color = PlayerMuted, fontSize = 14.sp)
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(PlayerWhite.copy(alpha = .24f))) {
                            Box(Modifier.fillMaxWidth(.82f).fillMaxHeight().background(PlayerWhite, CircleShape))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text("− LIVE", color = PlayerMuted, fontSize = 14.sp)
                        Spacer(Modifier.width(26.dp))
                        PlayerAction("CC") { showOverlay = true }
                        PlayerAction("AUDIO") { showOverlay = true }
                        PlayerAction(if (favorite) "★" else "☆", favorite) { onFavorite(channel); showOverlay = true }
                        PlayerAction("•••") { showOverlay = true }
                    }
                }
            }
        }

        if (zapLabel) {
            Row(Modifier.align(Alignment.TopStart).padding(36.dp).clip(RoundedCornerShape(16.dp)).background(PlayerGlass).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(channels.indexOfFirst { it.id == channel.id }.let { if (it < 0) "—" else (it + 1).toString() }, color = PlayerAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(channel.name, color = PlayerWhite, fontSize = 18.sp)
            }
        }

        if (showChannelPanel) {
            Column(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(390.dp).background(PlayerGlass).padding(22.dp)) {
                Text("Channels", color = PlayerWhite, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channels, key = { it.id }) { item ->
                        val selected = item.id == channel.id
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (selected) PlayerWhite else Color.White.copy(alpha = .07f))
                            .clickable { onSelect(item); showChannelPanel = false; showOverlay = false }
                            .focusable().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!item.logo.isNullOrBlank()) AsyncImage(model = item.logo, contentDescription = null, modifier = Modifier.size(42.dp))
                            else Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF45454A)), contentAlignment = Alignment.Center) {
                                Text(item.name.take(1).uppercase(), color = PlayerWhite, fontSize = 19.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = if (selected) Color.Black else PlayerWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(playerProgram(item, programs)?.title ?: item.group, color = if (selected) Color.DarkGray else PlayerMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerAction(label: String, active: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 8.dp).sizeIn(minWidth = 54.dp, minHeight = 50.dp).clip(CircleShape)
        .background(if (focused) PlayerWhite else PlayerGlass).border(1.dp, PlayerWhite.copy(alpha = .12f), CircleShape)
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (focused) Color.Black else if (active) PlayerAccent else PlayerWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun playerProgram(channel: Channel, programs: List<EpgProgram>): EpgProgram? {
    val now = System.currentTimeMillis()
    return programs.firstOrNull { (it.channelId == channel.id || it.channelId.equals(channel.name, true)) && it.startEpochMillis <= now && it.endEpochMillis > now }
}