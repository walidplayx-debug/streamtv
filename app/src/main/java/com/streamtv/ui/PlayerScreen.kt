package com.streamtv.ui

import android.view.ViewGroup
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.scale
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
private val PlayerGlass = Color(0xB8202024)
private val PlayerAccent = Color(0xFFD5DADF)

private enum class OverlayState { None, InfoBar, ChannelList }

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
    var overlayState by remember { mutableStateOf(OverlayState.InfoBar) }
    val playerFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }
    LaunchedEffect(overlayState) {
        if (overlayState == OverlayState.ChannelList && channels.isNotEmpty()) channelListFocusRequester.requestFocus()
        else if (overlayState != OverlayState.ChannelList) playerFocusRequester.requestFocus()
    }
    val favorite = channel.id in favorites
    val current = playerProgram(channel, programs)
    val next = programs.filter { (it.channelId == channel.id || it.channelId.equals(channel.name, true)) && it.startEpochMillis > System.currentTimeMillis() }
        .minByOrNull { it.startEpochMillis }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(exception: androidx.media3.common.PlaybackException) {
                error = exception.message ?: "Playback error"
                overlayState = OverlayState.InfoBar
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(channel.id) { overlayState = OverlayState.InfoBar }
    LaunchedEffect(overlayState, channel.id) {
        if (overlayState == OverlayState.InfoBar) {
            delay(4000)
            if (overlayState == OverlayState.InfoBar) overlayState = OverlayState.None
        }
    }
    BackHandler {
        if (overlayState == OverlayState.ChannelList) overlayState = OverlayState.None else onBack()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(playerFocusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key(AndroidKeyEvent.KEYCODE_CHANNEL_UP.toLong()) -> {
                        onZap(1); overlayState = OverlayState.InfoBar; true
                    }
                    Key(AndroidKeyEvent.KEYCODE_CHANNEL_DOWN.toLong()) -> {
                        onZap(-1); overlayState = OverlayState.InfoBar; true
                    }
                    Key(AndroidKeyEvent.KEYCODE_BACK.toLong()) -> {
                        AnimatedVisibility(
            visible = overlayState == OverlayState.ChannelList,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(24.dp).width(420.dp),
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = .86f, stiffness = 300f)) + fadeIn(tween(220)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(tween(180))
        ) {
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xED202329), Color(0xE8101115))))
                .border(1.dp, PlayerWhite.copy(alpha = .16f), RoundedCornerShape(28.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text("Channels", color = PlayerWhite, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channels, key = { it.id }) { item ->
                        val selected = item.id == channel.id
                        var focused by remember(item.id) { mutableStateOf(false) }
                        val itemScale by animateFloatAsState(if (focused) 1.035f else 1f, spring(dampingRatio = .78f, stiffness = 420f), label = "channelItemScale")
                        Row((if (item == channels.firstOrNull()) Modifier.focusRequester(channelListFocusRequester) else Modifier)
                            .fillMaxWidth().scale(itemScale).clip(RoundedCornerShape(17.dp))
                            .background(if (focused) PlayerWhite.copy(alpha = .94f) else if (selected) PlayerWhite.copy(alpha = .16f) else PlayerWhite.copy(alpha = .055f))
                            .border(1.dp, if (focused) PlayerWhite.copy(alpha = .9f) else PlayerWhite.copy(alpha = if (selected) .35f else .08f), RoundedCornerShape(17.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onSelect(item); overlayState = OverlayState.InfoBar }
                            .focusable().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!item.logo.isNullOrBlank()) AsyncImage(model = item.logo, contentDescription = null, modifier = Modifier.size(46.dp))
                            else Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF383C43)), contentAlignment = Alignment.Center) {
                                Text(item.name.trim().take(2).uppercase(), color = PlayerWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = if (focused) Color(0xFF101114) else PlayerWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(item.group, channelQuality(item.name)).filter { it.isNotBlank() }.joinToString("  ·  "),
                                    color = if (focused) Color.DarkGray else PlayerMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun channelQuality(name: String): String = Regex("\\b(\\d{3,4}p|4K|HD|SD)\\b", RegexOption.IGNORE_CASE).find(name)?.value?.uppercase().orEmpty()
