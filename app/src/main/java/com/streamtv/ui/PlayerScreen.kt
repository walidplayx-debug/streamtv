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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamtv.data.Channel
import com.streamtv.data.EpgProgram
import kotlinx.coroutines.delay

private val PlayerWhite = Color.White
private val PlayerMuted = Color(0xFFB5B5BA)
private val PlayerGlass = Color(0xB81C1C1E)
private val PlayerGlassBorder = Color(0x33FFFFFF)
private const val InfoBarMillis = 4000L
private const val StallMillis = 20000L
private val PlayerAccent = Color(0xFFD5DADF)

private enum class OverlayState { None, InfoBar, ChannelList }

/** requestFocus() throws while the target is not on screen yet; wait a few frames for it. */
private suspend fun FocusRequester.requestFocusSafely() {
    repeat(20) {
        if (runCatching { requestFocus() }.isSuccess) return
        withFrameNanos { }
    }
}

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
    var buffering by remember(channel.url) { mutableStateOf(true) }
    var failed by remember(channel.url) { mutableStateOf(false) }
    var autoRetries by remember(channel.url) { mutableIntStateOf(0) }
    var overlayState by remember { mutableStateOf(OverlayState.InfoBar) }
    var infoBarTick by remember { mutableIntStateOf(0) }
    val playerFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val currentChannelIndex = channels.indexOfFirst { it.key == channel.key }.coerceAtLeast(0)
    val current = playerProgram(channel, programs)

    fun restart() {
        failed = false
        buffering = true
        player.stop()
        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.playWhenReady = true
    }
    // One silent retry, then the error card.
    fun onStreamProblem() {
        if (autoRetries < 1) {
            autoRetries++
            restart()
        } else {
            player.stop()
            buffering = false
            failed = true
        }
    }
    fun showInfoBar() {
        overlayState = OverlayState.InfoBar
        infoBarTick++
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> buffering = true
                    Player.STATE_READY -> { buffering = false; failed = false }
                    Player.STATE_ENDED -> onStreamProblem()
                    else -> Unit
                }
            }
            override fun onPlayerError(error: PlaybackException) = onStreamProblem()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    // A stream that buffers forever is treated like a broken one.
    LaunchedEffect(player, buffering, failed, autoRetries) {
        if (buffering && !failed) {
            delay(StallMillis)
            onStreamProblem()
        }
    }

    LaunchedEffect(channel.key) { if (overlayState != OverlayState.ChannelList) showInfoBar() }
    LaunchedEffect(overlayState, infoBarTick) {
        if (overlayState == OverlayState.InfoBar) {
            delay(InfoBarMillis)
            if (overlayState == OverlayState.InfoBar) overlayState = OverlayState.None
        }
    }
    val showErrorCard = failed && overlayState != OverlayState.ChannelList
    LaunchedEffect(overlayState == OverlayState.ChannelList, showErrorCard) {
        when {
            overlayState == OverlayState.ChannelList -> Unit
            showErrorCard -> retryFocusRequester.requestFocusSafely()
            else -> playerFocusRequester.requestFocusSafely()
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
                val listOpen = overlayState == OverlayState.ChannelList
                when (event.key) {
                    Key(AndroidKeyEvent.KEYCODE_CHANNEL_UP.toLong()) -> { onZap(1); showInfoBar(); true }
                    Key(AndroidKeyEvent.KEYCODE_CHANNEL_DOWN.toLong()) -> { onZap(-1); showInfoBar(); true }
                    Key(AndroidKeyEvent.KEYCODE_BACK.toLong()) -> {
                        if (listOpen) { overlayState = OverlayState.None; true } else false
                    }
                    Key.DirectionUp -> if (listOpen) false else { onZap(1); showInfoBar(); true }
                    Key.DirectionDown -> if (listOpen) false else { onZap(-1); showInfoBar(); true }
                    Key.DirectionRight -> when {
                        listOpen || showErrorCard -> false
                        else -> { overlayState = OverlayState.ChannelList; true }
                    }
                    Key.DirectionLeft -> if (listOpen) { overlayState = OverlayState.None; true } else false
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter,
                    Key(AndroidKeyEvent.KEYCODE_BUTTON_SELECT.toLong()) -> {
                        if (listOpen || showErrorCard) false else { showInfoBar(); true }
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    isFocusable = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = player }
        )

        if (buffering && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(46.dp),
                color = PlayerWhite.copy(alpha = .9f),
                trackColor = PlayerWhite.copy(alpha = .14f),
                strokeWidth = 3.dp
            )
        }

        if (showErrorCard) {
            Column(
                Modifier.align(Alignment.Center).width(440.dp).clip(RoundedCornerShape(28.dp))
                    .background(Color(0xE61C1C1E))
                    .border(1.dp, PlayerGlassBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Can't play this channel", color = PlayerWhite, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(channelDisplayName(channel.name), color = PlayerMuted, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("The stream is not responding right now.", color = PlayerMuted, fontSize = 14.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardButton("Retry", Modifier.focusRequester(retryFocusRequester)) { autoRetries = 0; restart() }
                    CardButton("Next channel") { onZap(1) }
                }
            }
        }

        // The info bar never coexists with the channel list and never changes size.
        if (overlayState != OverlayState.ChannelList) AnimatedVisibility(
            visible = overlayState == OverlayState.InfoBar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth(.65f).height(80.dp),
            enter = fadeIn(animationSpec = tween(260)) +
                slideInVertically(animationSpec = spring(dampingRatio = .82f, stiffness = 360f)) { it / 2 },
            exit = fadeOut(animationSpec = tween(220)) +
                slideOutVertically(animationSpec = tween(220)) { it / 3 }
        ) {
            Row(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xE626282D), Color(0xE6101115), Color(0xE6222429))))
                    .border(1.dp, PlayerWhite.copy(alpha = .16f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.Start) {
                    Text("CH", color = PlayerMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text((currentChannelIndex + 1).toString(), color = PlayerWhite, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(1.dp).height(46.dp).background(PlayerWhite.copy(alpha = .22f)))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(channelDisplayName(channel.name), color = PlayerWhite, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(current?.title ?: channel.group, channelQuality(channel.name))
                            .filter { it.isNotBlank() }.joinToString("  ·  "),
                        color = PlayerMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (current != null) {
                        Spacer(Modifier.height(6.dp))
                        val progress = ((System.currentTimeMillis() - current.startEpochMillis).toFloat() /
                            (current.endEpochMillis - current.startEpochMillis).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(PlayerWhite.copy(alpha = .20f))) {
                            Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(PlayerAccent))
                        }
                    }
                }
                Spacer(Modifier.width(18.dp))
                ChannelLogo(channel.name, channel.logo, Modifier.size(width = 64.dp, height = 44.dp), fontSize = 18.sp)
            }
        }

        AnimatedVisibility(
            visible = overlayState == OverlayState.ChannelList,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(24.dp).width(420.dp),
            enter = slideInHorizontally(initialOffsetX = { it },
                animationSpec = spring(dampingRatio = .86f, stiffness = 300f)) + fadeIn(tween(220)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(tween(180))
        ) {
            // Created on every open so the list starts at, and focuses, the playing channel.
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentChannelIndex - 2).coerceAtLeast(0))
            val playingFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { playingFocusRequester.requestFocusSafely() }
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                    .background(PlayerGlass)
                    .border(1.dp, PlayerGlassBorder, RoundedCornerShape(28.dp))
                    .padding(top = 20.dp)
            ) {
                Text("Channels", color = PlayerWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start, modifier = Modifier.padding(horizontal = 26.dp))
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    // Room on every side so the 1.04 focus scale and the last row are never clipped.
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(channels, key = { it.key }) { item ->
                        val playing = item.key == channel.key
                        var focused by remember { mutableStateOf(false) }
                        val itemScale by animateFloatAsState(
                            if (focused) 1.04f else 1f,
                            spring(dampingRatio = .78f, stiffness = 420f),
                            label = "channelItemScale"
                        )
                        Row(
                            (if (playing) Modifier.focusRequester(playingFocusRequester) else Modifier)
                                .fillMaxWidth().scale(itemScale).clip(RoundedCornerShape(18.dp))
                                .background(
                                    when {
                                        focused -> Color(0x33FFFFFF)
                                        playing -> Color(0x14FFFFFF)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(1.dp, if (focused) Color(0x73FFFFFF) else Color.Transparent, RoundedCornerShape(18.dp))
                                .onFocusChanged { focused = it.isFocused }
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    overlayState = OverlayState.None
                                    onSelect(item)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelLogo(item.name, item.logo, Modifier.size(48.dp), fontSize = 16.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(
                                    channelDisplayName(item.name),
                                    color = PlayerWhite,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    item.group,
                                    color = Color(0x99FFFFFF),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val quality = channelQuality(item.name)
                            if (quality.isNotBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(7.dp))
                                        .background(Color(0x24FFFFFF))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(quality, color = Color(0xD9FFFFFF), fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(stiffness = 430f), label = "cardButtonScale")
    Box(
        modifier.scale(scale).clip(CircleShape)
            .background(if (focused) PlayerWhite else Color(0x29FFFFFF))
            .border(1.dp, PlayerGlassBorder, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (focused) Color.Black else PlayerWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun playerProgram(channel: Channel, programs: List<EpgProgram>): EpgProgram? {
    val now = System.currentTimeMillis()
    return programs.firstOrNull {
        (it.channelId == channel.id || it.channelId.equals(channel.name, true)) &&
            it.startEpochMillis <= now && it.endEpochMillis > now
    }
}

private fun channelDisplayName(name: String): String =
    name.replace(Regex("\\s*\\((?:\\d{3,4}p|4K|HD|SD)\\)\\s*", RegexOption.IGNORE_CASE), " ").trim()

private fun channelQuality(name: String): String =
    Regex("\\b(\\d{3,4}p|4K|HD|SD)\\b", RegexOption.IGNORE_CASE).find(name)?.value?.uppercase().orEmpty()
