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
private val PlayerGlass = Color(0xB8202024)
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
    var showChannelPanel by remember { mutableStateOf(false) }
    var zapLabel by remember { mutableStateOf(false) }
    val playerFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }
    LaunchedEffect(showChannelPanel) {
        if (showChannelPanel && channels.isNotEmpty()) channelListFocusRequester.requestFocus()
        else if (!showChannelPanel) playerFocusRequester.requestFocus()
    }
    val favorite = channel.id in favorites
    val current = playerProgram(channel, programs)
    val next = programs.filter { (it.channelId == channel.id || it.channelId.equals(channel.name, true)) && it.startEpochMillis > System.currentTimeMillis() }
        .minByOrNull { it.startEpochMillis }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(exception: androidx.media3.common.PlaybackException) {
                error = exception.message ?: "Playback error"
                zapLabel = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(channel.id) { zapLabel = true }
    LaunchedEffect(zapLabel, channel.id) {
        if (zapLabel) {
            val progress = current?.let {
                ((System.currentTimeMillis() - it.startEpochMillis).toFloat() /
                    (it.endEpochMillis - it.startEpochMillis).coerceAtLeast(1L)).coerceIn(0f, 1f)
            } ?: 0f
            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val channelNumber = channels.indexOfFirst { it.id == channel.id }.let {
                if (it < 0) "—" else (it + 1).toString()
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(.92f).padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xD9161A22))
                    .border(1.dp, PlayerWhite.copy(alpha = .13f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    AsyncImage(model = channel.logo, contentDescription = channel.name,
                        modifier = Modifier.width(112.dp).height(62.dp))
                } else {
                    Box(Modifier.width(112.dp).height(62.dp), contentAlignment = Alignment.Center) {
                        Text(channel.name.take(2).uppercase(), color = PlayerWhite,
                            fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Box(Modifier.width(1.dp).height(66.dp).background(PlayerWhite.copy(alpha = .35f)))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(channel.name, color = PlayerWhite, fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current?.title ?: channel.group, color = PlayerMuted, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current != null) {
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                            .background(PlayerWhite.copy(alpha = .25f))) {
                            Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape)
                                .background(Color(0xFFE33445)))
                        }
                    }
                    error?.let { Text(it, color = Color(0xFFFF9B8F), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Spacer(Modifier.width(22.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (current != null) {
                        Text(timeFormat.format(Date(current.startEpochMillis)) + "  |  " +
                            timeFormat.format(Date(current.endEpochMillis)),
                            color = PlayerMuted, fontSize = 12.sp)
                    }
                    Text("CH " + channelNumber, color = PlayerWhite, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (showChannelPanel) {
            Column(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(390.dp).background(PlayerGlass).padding(22.dp)) {
                Text("Channels", color = PlayerWhite, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channels, key = { it.id }) { item ->
                        val selected = item.id == channel.id
                        var focused by remember(item.id) { mutableStateOf(false) }
                        Row((if (item == channels.firstOrNull()) Modifier.focusRequester(channelListFocusRequester) else Modifier).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (focused) Color(0xFFE2E7ED) else if (selected) PlayerWhite.copy(alpha = .18f) else Color.White.copy(alpha = .09f))
                            .border(if (focused) 1.5.dp else 1.dp, if (focused) PlayerWhite.copy(alpha = .95f) else PlayerWhite.copy(alpha = .12f), RoundedCornerShape(14.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onSelect(item); showChannelPanel = false; }
                            .focusable().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!item.logo.isNullOrBlank()) AsyncImage(model = item.logo, contentDescription = null, modifier = Modifier.size(42.dp))
                            else Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF45454A)), contentAlignment = Alignment.Center) {
                                Text(item.name.take(1).uppercase(), color = PlayerWhite, fontSize = 19.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = if (focused || selected) Color.Black else PlayerWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(playerProgram(item, programs)?.title ?: item.group, color = if (focused || selected) Color.DarkGray else PlayerMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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