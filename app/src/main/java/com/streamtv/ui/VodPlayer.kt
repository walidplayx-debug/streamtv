package com.streamtv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamtv.data.Channel
import kotlinx.coroutines.delay

private val VodWhite = Color.White
private val VodMuted = Color(0xFFB5B5BA)
private const val SeekStepMillis = 10_000L
private const val ControlsMillis = 4_500L

/** The buttons under the progress bar, in order. */
private val VodButtons = listOf("back10", "play", "fwd10", "favorite")

/**
 * The movie and episode player: a progress bar with times, rewind / play-pause / forward, and the
 * favourite star. Up and down never change the title, and nothing opens a channel list.
 *
 * Keys: with the controls hidden, left/right seek 10 s and OK pauses or plays; any key shows the
 * controls. With the controls showing, the progress bar is selected first (left/right seek), down
 * moves to the buttons (left/right pick, OK presses), up goes back to the bar. Back hides the
 * controls, and a second Back leaves the player.
 */
@Composable
fun VodPlayerScreen(
    channel: Channel,
    favorite: Boolean,
    startAt: Long,
    onFavorite: () -> Unit,
    /** Saves where the title was left (position, duration), now and then and when leaving. */
    onPosition: (Long, Long) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val savePosition by rememberUpdatedState(onPosition)
    val player = remember(channel.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(channel.url), startAt.coerceAtLeast(0L))
            prepare()
            playWhenReady = true
        }
    }
    var position by remember { mutableLongStateOf(startAt.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    // 0: the progress bar, 1: the buttons.
    var row by remember { mutableIntStateOf(0) }
    var button by remember { mutableIntStateOf(1) }
    var touch by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    fun save() {
        val length = player.duration
        if (length != C.TIME_UNSET && length > 0) savePosition(player.currentPosition, length)
    }
    fun show() { controls = true; touch++ }
    fun seek(delta: Long) {
        val length = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val target = (player.currentPosition + delta).coerceAtLeast(0L).let { if (length != null) it.coerceAtMost(length - 1_000L) else it }
        player.seekTo(target)
        position = target
        show()
    }
    fun togglePlay() {
        if (player.isPlaying || player.playWhenReady) player.pause() else player.play()
        show()
    }
    fun press(name: String) {
        when (name) {
            "back10" -> seek(-SeekStepMillis)
            "fwd10" -> seek(SeekStepMillis)
            "play" -> togglePlay()
            "favorite" -> { onFavorite(); show() }
        }
    }
    fun leave() { save(); onExit() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) failed = false
                if (state == Player.STATE_ENDED) { savePosition(0L, 0L); controls = true }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false; controls = true }
        }
        player.addListener(listener)
        onDispose { save(); player.removeListener(listener); player.release() }
    }
    // Home (or anything that takes the app off screen) pauses; the place is kept.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { save(); player.pause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The clock under the bar, and a saved place every half minute in case the TV is switched off.
    LaunchedEffect(player) {
        var ticks = 0
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            if (++ticks % 60 == 0) save()
            delay(500)
        }
    }
    // The controls step away on their own while the title plays.
    LaunchedEffect(controls, touch, playing) {
        if (controls && playing && !failed) {
            delay(ControlsMillis)
            controls = false
            row = 0
            button = 1
        }
    }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    BackHandler {
        if (controls && !failed) { controls = false; row = 0; button = 1 } else leave()
    }

    CompositionLocalProviderLtr {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        // Back is handled on key down (BackHandler); let its release through untouched.
                        return@onPreviewKeyEvent event.key != Key.Back
                    }
                    when (event.key) {
                        Key.Back -> false
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePlay(); true }
                        Key.MediaFastForward -> { seek(SeekStepMillis * 3); true }
                        Key.MediaRewind -> { seek(-SeekStepMillis * 3); true }
                        // Never zap to another title.
                        Key.ChannelUp, Key.ChannelDown -> true
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val forward = event.key == Key.DirectionRight
                            when {
                                failed -> { if (controls) { row = 1; button = if (forward) 1 else 0 }; show() }
                                !controls || row == 0 -> seek(if (forward) SeekStepMillis else -SeekStepMillis)
                                else -> { button = (button + if (forward) 1 else -1).coerceIn(0, VodButtons.lastIndex); show() }
                            }
                            true
                        }
                        Key.DirectionDown -> { if (controls) row = 1; show(); true }
                        Key.DirectionUp -> { if (controls) row = 0; show(); true }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonSelect -> {
                            if (event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                            when {
                                failed -> if (button == 0) leave() else { failed = false; player.prepare(); player.play(); show() }
                                !controls || row == 0 -> togglePlay()
                                else -> press(VodButtons[button])
                            }
                            true
                        }
                        else -> { show(); false }
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
                    color = VodWhite.copy(alpha = .9f), trackColor = VodWhite.copy(alpha = .14f), strokeWidth = 3.dp
                )
            }

            if (failed) {
                Column(
                    Modifier.align(Alignment.Center).width(420.dp).clip(RoundedCornerShape(28.dp))
                        .background(Color(0xE61C1C1E)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(t("Can't play this title"), color = VodWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(channel.name, color = VodMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        VodTextButton(t("Back"), selected = button == 0)
                        VodTextButton(t("Retry"), selected = button != 0)
                    }
                }
            }

            // Top: the title. Bottom: the bar and the buttons. Both fade together.
            AnimatedVisibility(
                visible = controls && !failed, modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(200)), exit = fadeOut(tween(260))
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().height(140.dp).align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    ) {
                        Text(
                            channel.name, Modifier.padding(start = 56.dp, end = 56.dp, top = 34.dp),
                            color = VodWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                            .padding(start = 56.dp, end = 56.dp, top = 60.dp, bottom = 34.dp)
                    ) {
                        ProgressBar(position, duration, selected = row == 0)
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            VodButtons.forEachIndexed { index, name ->
                                if (index > 0) Spacer(Modifier.width(18.dp))
                                VodIconButton(name, playing = playing, favorite = favorite, selected = row == 1 && button == index)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The player keeps one left-to-right layout in every language, like the live player. */
@Composable
private fun CompositionLocalProviderLtr(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

@Composable
private fun ProgressBar(position: Long, duration: Long, selected: Boolean) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val thumb by animateFloatAsState(if (selected) 1f else 0f, spring(stiffness = 500f), label = "thumb")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(position), color = VodWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f).height(18.dp), contentAlignment = Alignment.CenterStart) {
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val y = size.height / 2f
                val track = if (selected) 6.dp.toPx() else 4.dp.toPx()
                drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = track, cap = StrokeCap.Round)
                drawLine(VodWhite, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = track, cap = StrokeCap.Round)
                // The playhead grows in when the bar is selected (left/right seek).
                drawCircle(VodWhite, radius = 3.dp.toPx() + 5.dp.toPx() * thumb, center = Offset(size.width * fraction, y))
            }
        }
        Text(
            if (duration > 0) "-" + formatTime((duration - position).coerceAtLeast(0L)) else "--:--",
            color = VodMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp).padding(start = 12.dp)
        )
    }
}

@Composable
private fun VodIconButton(name: String, playing: Boolean, favorite: Boolean, selected: Boolean) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = .7f, stiffness = 450f), label = "vodButton")
    val big = name == "play"
    Box(
        Modifier.size(if (big) 64.dp else 52.dp).scale(scale).clip(CircleShape)
            .background(if (selected) VodWhite else Color(0x33FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (selected) Color(0xFF1C1C1E) else VodWhite
        when (name) {
            "back10", "fwd10" -> Text(if (name == "back10") "−10" else "+10", color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            else -> Canvas(Modifier.size(if (big) 24.dp else 22.dp)) {
                val w = size.width
                val h = size.height
                when {
                    name == "play" && playing -> {
                        drawRoundRect(tint, topLeft = Offset(w * .18f, h * .12f), size = androidx.compose.ui.geometry.Size(w * .22f, h * .76f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f))
                        drawRoundRect(tint, topLeft = Offset(w * .6f, h * .12f), size = androidx.compose.ui.geometry.Size(w * .22f, h * .76f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f))
                    }
                    name == "play" -> {
                        val path = Path().apply { moveTo(w * .24f, h * .1f); lineTo(w * .9f, h * .5f); lineTo(w * .24f, h * .9f); close() }
                        drawPath(path, tint)
                    }
                    else -> {
                        val path = Path()
                        val outer = w * .48f
                        val inner = outer * .46f
                        for (i in 0 until 10) {
                            val r = if (i % 2 == 0) outer else inner
                            val angle = Math.toRadians(-90.0 + i * 36.0)
                            val x = w / 2f + (r * kotlin.math.cos(angle)).toFloat()
                            val y = h / 2f + (r * kotlin.math.sin(angle)).toFloat() + outer * .06f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        // A favourite's star is filled in; otherwise only its outline shows.
                        if (favorite) drawPath(path, tint)
                        drawPath(path, tint, style = Stroke(width = w * .09f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VodTextButton(label: String, selected: Boolean) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) VodWhite else Color(0x29FFFFFF))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.Black else VodWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 1:05:09 or 05:09. */
fun formatTime(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    // Western digits in every language, like the clock.
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}
