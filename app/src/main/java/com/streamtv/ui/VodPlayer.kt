package com.streamtv.ui

import android.os.SystemClock
import android.view.TextureView
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
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
private const val ControlsMillis = 4_500L
/** The preview follows the chosen place after this pause, so a held key does not flood it with seeks. */
private const val PreviewSeekMillis = 120L
/** The preview player is kept this long after choosing ends, ready for the next time. */
private const val PreviewKeepMillis = 15_000L
/** While left/right is held, the place moves one step this often. */
private const val HoldStepMillis = 80L
/** A press within this time of the last one, in the same direction, keeps the run (and its speed). */
private const val RunGapMillis = 1_200L

/** Light text over the picture keeps a soft shadow, so it reads on bright scenes too. */
private val ShadowStyle = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 8f))

/** One subtitle track found in the file. */
private class TextTrack(val label: String, val group: androidx.media3.common.TrackGroup, val selected: Boolean)
/** A sound track: [code] is what its button shows ("AR", "EN", or its number). */
private class AudioTrack(val code: String, val group: androidx.media3.common.TrackGroup, val selected: Boolean)

/** Left/right presses in one direction, close together: the longer the run, the bigger the step. */
private class ScrubRun {
    var direction = 0
    var started = 0L
    var last = 0L
    var lastStep = 0L
    var steps = 0
}

/**
 * How far one left/right step moves, by the number of steps in the run: 10 s for the first two,
 * then 30 s, 1 min, 2 min and 5 min, so a held key crosses a whole film in a second or two.
 * A short title never moves more than a fifteenth of its length per step.
 */
internal fun scrubStep(steps: Int, durationMillis: Long): Long {
    val step = when {
        steps <= 2 -> 10_000L
        steps <= 4 -> 30_000L
        steps <= 8 -> 60_000L
        steps <= 16 -> 120_000L
        else -> 300_000L
    }
    val cap = if (durationMillis > 0) (durationMillis / 15).coerceAtLeast(10_000L) else step
    return step.coerceAtMost(cap)
}

/**
 * The movie and episode player, in the style of the Apple TV app: no panel, a dark fade at the
 * bottom, the title, a few round buttons and a full-width bar with the time under the playhead and
 * the time left at the end. Up and down never change the title, and nothing opens a channel list.
 *
 * Keys: left/right choose a place on the bar, with a small picture of that place above it, while
 * the title goes on playing behind (the step grows while the presses keep coming or the key is
 * held); OK plays the chosen place, otherwise OK pauses or plays. With the controls showing, up goes to the buttons (left/right pick, OK presses) and down
 * back to the bar. Back drops a place being chosen, then hides the controls, then leaves.
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
        Players.createVod(context).apply {
            setMediaSource(Players.stream(context, channel.url), startAt.coerceAtLeast(0L))
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
    // 0: the progress bar, 1: the buttons above it.
    var row by remember { mutableIntStateOf(0) }
    var button by remember { mutableIntStateOf(0) }
    // On the error card: 0 is Back, 1 is Retry.
    var failPick by remember { mutableIntStateOf(1) }
    var touch by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    // The place being chosen with left/right, before it is played; null when not choosing.
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    val run = remember { ScrubRun() }
    // The small picture above the bar while choosing a place. Made when first needed; if the device
    // or the server cannot give a second picture, only the time is shown.
    var preview by remember { mutableStateOf<ExoPlayer?>(null) }
    var previewShown by remember { mutableStateOf(false) }
    var previewFailed by remember { mutableStateOf(false) }
    fun dropPreview() {
        preview?.release()
        preview = null
        previewShown = false
    }
    // Subtitles carried inside the file. The one in the app's language is picked by itself.
    var subtitles by remember { mutableStateOf<List<TextTrack>>(emptyList()) }
    var subtitlesOff by remember { mutableStateOf(false) }
    // Sound tracks (a dubbed and an original language, say). The app's language is picked by itself.
    var audios by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    val appLanguage = LocalAppLanguage.current
    LaunchedEffect(player, appLanguage) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(appLanguage).setPreferredAudioLanguage(appLanguage).build()
    }
    // Beside the title: the sound button only when there is a choice, the subtitles button only when
    // the title has some, and the favourite star last.
    val buttons = (if (audios.size > 1) listOf("audio") else emptyList()) +
        (if (subtitles.isNotEmpty()) listOf("subtitles") else emptyList()) + "favorite"
    val audioCode = audios.firstOrNull { it.selected }?.code ?: ""
    fun nextAudio() {
        // One track after the other, back to the first.
        val next = (audios.indexOfFirst { it.selected } + 1) % audios.size
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(audios[next].group, 0)).build()
    }
    val chosenSubtitle = if (subtitlesOff) -1 else subtitles.indexOfFirst { it.selected }
    fun nextSubtitle() {
        // Off -> first track -> second -> ... -> off.
        val next = chosenSubtitle + 1
        val params = player.trackSelectionParameters.buildUpon()
        if (next >= subtitles.size) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            subtitlesOff = true
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(subtitles[next].group, 0))
            subtitlesOff = false
        }
        player.trackSelectionParameters = params.build()
    }

    fun save() {
        val length = player.duration
        if (length != C.TIME_UNSET && length > 0) savePosition(player.currentPosition, length)
    }
    fun show() { controls = true; touch++ }
    fun knownLength(): Long = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    fun clampPlace(target: Long): Long {
        val length = knownLength()
        val end = if (length > 0) (length - 1_000L).coerceAtLeast(0L) else Long.MAX_VALUE
        return target.coerceIn(0L, end)
    }
    /** Plays the chosen place now (OK). Until then the title goes on playing behind the preview. */
    fun commitScrub() {
        val target = scrubTarget ?: return
        scrubTarget = null
        player.seekTo(target)
        position = target
        show()
    }
    /** One left/right step; [held] is true for the repeats of a key being held down. */
    fun scrub(forward: Boolean, held: Boolean) {
        val now = SystemClock.uptimeMillis()
        val direction = if (forward) 1 else -1
        if (run.direction != direction || now - run.last > RunGapMillis) {
            run.direction = direction
            run.started = now
            run.steps = 0
            run.lastStep = 0L
        }
        run.last = now
        // A held key repeats very fast: one step every HoldStepMillis keeps the speed even.
        if (held && now - run.lastStep < HoldStepMillis) return
        run.lastStep = now
        run.steps++
        val step = scrubStep(run.steps, knownLength())
        scrubTarget = clampPlace((scrubTarget ?: player.currentPosition) + direction * step)
        if (preview == null && !previewFailed) {
            preview = Players.createPreview(context).apply {
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { previewShown = true }
                    override fun onPlayerError(error: PlaybackException) { previewFailed = true; dropPreview() }
                })
                setMediaSource(Players.stream(context, channel.url), scrubTarget ?: 0L)
                prepare()
            }
        }
        show()
    }
    fun togglePlay() {
        if (player.isPlaying || player.playWhenReady) player.pause() else player.play()
        show()
    }
    fun press(name: String) {
        when (name) {
            "subtitles" -> { nextSubtitle(); show() }
            "favorite" -> { onFavorite(); show() }
            "audio" -> { if (audios.size > 1) nextAudio(); show() }
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
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false; controls = true; scrubTarget = null }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                subtitles = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }.mapIndexed { index, group ->
                    val format = group.getTrackFormat(0)
                    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
                    val label = format.label?.takeIf { it.isNotBlank() }
                        ?: language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale(appLanguage)).replaceFirstChar { c -> c.uppercase() } }
                        ?: "${index + 1}"
                    TextTrack(label, group.mediaTrackGroup, group.isSelected)
                }
                audios = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }.mapIndexed { index, group ->
                    val language = group.getTrackFormat(0).language?.takeIf { it.isNotBlank() && it != "und" }
                    AudioTrack(language?.substringBefore('-')?.take(3)?.uppercase() ?: "${index + 1}", group.mediaTrackGroup, group.isSelected)
                }
            }
        }
        player.addListener(listener)
        onDispose { save(); player.removeListener(listener); player.release(); dropPreview() }
    }
    // Home (or anything that takes the app off screen) stops the title completely: no download,
    // no decoder held. The place is kept; coming back, the title is ready again, paused there.
    val lifecycleOwner = LocalLifecycleOwner.current
    val stoppedInBackground = remember(player) { booleanArrayOf(false) }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    save()
                    stoppedInBackground[0] = true
                    scrubTarget = null
                    dropPreview()
                    player.playWhenReady = false
                    player.stop()
                }
                Lifecycle.Event.ON_START -> if (stoppedInBackground[0]) {
                    stoppedInBackground[0] = false
                    player.prepare()
                    controls = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The clock under the bar, and a saved place every half minute in case the TV is switched off.
    // Only while on screen.
    LaunchedEffect(player, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var ticks = 0
            while (true) {
                position = player.currentPosition
                duration = knownLength()
                if (++ticks % 60 == 0) save()
                delay(500)
            }
        }
    }
    // The preview follows the chosen place; once choosing ends it is let go after a while.
    LaunchedEffect(scrubTarget) {
        val target = scrubTarget
        if (target != null) {
            delay(PreviewSeekMillis)
            preview?.seekTo(target)
        } else if (preview != null) {
            delay(PreviewKeepMillis)
            dropPreview()
        }
    }
    // The controls step away on their own while the title plays.
    LaunchedEffect(controls, touch, playing) {
        if (controls && playing && !failed && scrubTarget == null) {
            delay(ControlsMillis)
            controls = false
            row = 0
            button = 0
        }
    }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    BackHandler {
        when {
            scrubTarget != null -> { scrubTarget = null; show() }
            controls && !failed -> { controls = false; row = 0; button = 0 }
            else -> leave()
        }
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
                    val held = event.nativeKeyEvent.repeatCount > 0
                    when (event.key) {
                        Key.Back -> false
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { if (!held) togglePlay(); true }
                        Key.MediaFastForward -> { if (!failed) scrub(forward = true, held = held); true }
                        Key.MediaRewind -> { if (!failed) scrub(forward = false, held = held); true }
                        // Never zap to another title.
                        Key.ChannelUp, Key.ChannelDown -> true
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val forward = event.key == Key.DirectionRight
                            when {
                                failed -> { failPick = if (forward) 1 else 0; show() }
                                !controls || row == 0 -> scrub(forward, held)
                                else -> { button = (button + if (forward) 1 else -1).coerceIn(0, buttons.lastIndex); show() }
                            }
                            true
                        }
                        Key.DirectionUp -> { if (controls && !failed && scrubTarget == null && buttons.isNotEmpty()) row = 1; show(); true }
                        Key.DirectionDown -> { if (controls) row = 0; show(); true }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonSelect -> {
                            if (held) return@onPreviewKeyEvent true
                            when {
                                failed -> if (failPick == 0) leave() else { failed = false; player.prepare(); player.play(); show() }
                                scrubTarget != null -> commitScrub()
                                !controls || row == 0 -> togglePlay()
                                else -> press(buttons[button.coerceIn(0, buttons.lastIndex)])
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
                        // The screen stays on while a title is on screen, not while browsing menus.
                        keepScreenOn = true
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
                        VodTextButton(t("Back"), selected = failPick == 0)
                        VodTextButton(t("Retry"), selected = failPick != 0)
                    }
                }
            }

            // No panel: a dark fade at the bottom of the picture carries the title, the buttons and the bar.
            AnimatedVisibility(
                visible = controls && !failed,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(280))
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.45f)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000), Color(0xD9000000))))
                    )
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(start = 64.dp, end = 64.dp, bottom = 34.dp)
                    ) {
                        // The title on the left, the small buttons on the right, both just above the bar.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.name, Modifier.weight(1f), color = VodWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = ShadowStyle
                            )
                            Spacer(Modifier.width(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                buttons.forEachIndexed { index, name ->
                                    VodIconButton(
                                        name, favorite = favorite, subtitlesOn = chosenSubtitle >= 0,
                                        subtitleLabel = if (chosenSubtitle >= 0) subtitles[chosenSubtitle].label else t("Off"),
                                        selected = row == 1 && button == index, audioCode = audioCode
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TimeBar(
                            position = scrubTarget ?: position, duration = duration, playing = playing,
                            selected = row == 0, scrubbing = scrubTarget != null,
                            jump = scrubTarget?.let { it - player.currentPosition },
                            preview = preview?.takeIf { scrubTarget != null },
                            previewShown = previewShown
                        )
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

/**
 * A full-width bar: the watched part bright, the rest see-through, and a thin upright playhead while
 * the bar is selected or a place is being chosen. The time sits under the playhead and follows it,
 * with the play/pause mark beside it; the time left sits at the end of the bar.
 */
@Composable
private fun TimeBar(
    position: Long, duration: Long, playing: Boolean, selected: Boolean, scrubbing: Boolean, jump: Long?,
    preview: ExoPlayer? = null, previewShown: Boolean = false
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val lift by animateFloatAsState(if (selected || scrubbing) 1f else 0f, spring(stiffness = 500f), label = "bar")
    Column(Modifier.fillMaxWidth()) {
        // The preview floats above the bar, centred on the playhead; it takes no room of its own,
        // so nothing moves when it appears.
        if (preview != null) {
            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = { ScrubPreview(preview, previewShown) }
            ) { measurables, constraints ->
                val box = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
                val width = constraints.maxWidth
                layout(width, 0) {
                    val x = ((width * fraction).toInt() - box.width / 2).coerceIn(0, (width - box.width).coerceAtLeast(0))
                    box.place(x, -box.height - 8.dp.roundToPx())
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val y = size.height / 2f
            val thick = 5.dp.toPx() + 2.dp.toPx() * lift
            val corner = CornerRadius(thick / 2f)
            drawRoundRect(Color(0x47FFFFFF), topLeft = Offset(0f, y - thick / 2f), size = Size(size.width, thick), cornerRadius = corner)
            val x = size.width * fraction
            if (x > 0f) {
                drawRoundRect(Color(0xE6FFFFFF), topLeft = Offset(0f, y - thick / 2f), size = Size(x.coerceAtLeast(thick), thick), cornerRadius = corner)
            }
            if (lift > 0f) {
                val w = 3.dp.toPx()
                val h = size.height * lift
                drawRoundRect(
                    VodWhite.copy(alpha = lift), topLeft = Offset((x - w / 2f).coerceIn(0f, size.width - w), y - h / 2f),
                    size = Size(w, h), cornerRadius = CornerRadius(w / 2f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Text(formatTime(position), color = VodWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = ShadowStyle)
                PlayStateIcon(playing)
                // While choosing a place: how far it is from where the title is now.
                Text(
                    jump?.let { (if (it < 0) "−" else "+") + formatTime(kotlin.math.abs(it)) } ?: "",
                    Modifier.clip(CircleShape).background(if (jump != null) Color(0x40FFFFFF) else Color.Transparent)
                        .padding(horizontal = if (jump != null) 10.dp else 0.dp, vertical = 2.dp),
                    color = VodWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (duration > 0) "-" + formatTime((duration - position).coerceAtLeast(0L)) else "--:--",
                    color = VodWhite.copy(alpha = .85f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = ShadowStyle
                )
            }
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val time = measurables[0].measure(loose)
            val icon = measurables[1].measure(loose)
            val chip = measurables[2].measure(loose)
            val left = measurables[3].measure(loose)
            val width = constraints.maxWidth
            val height = maxOf(time.height, icon.height, chip.height, left.height)
            val gap = 8.dp.roundToPx()
            layout(width, height) {
                // The time is centred under the playhead, but never runs into the time left.
                val chipSpace = if (chip.width > 0) chip.width + gap else 0
                val lastX = (width - left.width - 3 * gap - icon.width - time.width - chipSpace).coerceAtLeast(0)
                val x = ((width * fraction).toInt() - time.width / 2).coerceIn(0, lastX)
                time.place(x, (height - time.height) / 2)
                icon.place(x + time.width + gap, (height - icon.height) / 2)
                chip.place(x + time.width + 2 * gap + icon.width, (height - chip.height) / 2)
                left.place(width - left.width, (height - left.height) / 2)
            }
        }
    }
}

/** The picture at the chosen place, from the silent preview player. */
@Composable
private fun ScrubPreview(preview: ExoPlayer, shown: Boolean) {
    Box(
        Modifier.width(160.dp).height(90.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { TextureView(it).also { view -> preview.setVideoTextureView(view) } },
            update = { view -> preview.setVideoTextureView(view) },
            onRelease = { view -> preview.clearVideoTextureView(view) }
        )
        if (!shown) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = VodWhite.copy(alpha = .85f), trackColor = VodWhite.copy(alpha = .14f), strokeWidth = 2.5.dp
            )
        }
    }
}

/** A ring with a pause mark while playing, or a play mark while paused. */
@Composable
private fun PlayStateIcon(playing: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(VodWhite, radius = w / 2f - 1.dp.toPx(), style = Stroke(width = 1.6.dp.toPx()))
        if (playing) {
            val barW = w * .1f
            val barH = h * .36f
            val top = (h - barH) / 2f
            drawRoundRect(VodWhite, Offset(w * .5f - barW * 1.6f, top), Size(barW, barH), CornerRadius(barW / 2f))
            drawRoundRect(VodWhite, Offset(w * .5f + barW * .6f, top), Size(barW, barH), CornerRadius(barW / 2f))
        } else {
            val path = Path().apply { moveTo(w * .41f, h * .3f); lineTo(w * .72f, h * .5f); lineTo(w * .41f, h * .7f); close() }
            drawPath(path, VodWhite)
        }
    }
}

/**
 * A small see-through button, all the same height: the sound track, the subtitles (a pill with the
 * language shown, or "Off"; a press goes to the next one) and the favourite star.
 */
@Composable
private fun VodIconButton(
    name: String, favorite: Boolean, subtitlesOn: Boolean, subtitleLabel: String, selected: Boolean, audioCode: String = ""
) {
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, spring(dampingRatio = .7f, stiffness = 450f), label = "vodButton")
    val pill = name == "subtitles"
    Box(
        (if (pill) Modifier.height(36.dp) else Modifier.size(36.dp)).scale(scale).clip(CircleShape)
            .background(if (selected) VodWhite else Color(0x38FFFFFF))
            .border(1.dp, if (selected) Color.Transparent else Color(0x29FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (selected) Color(0xFF1C1C1E) else VodWhite
        when (name) {
            // A sound wave, with the language being played under it: a press goes to the next one.
            "audio" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(width = 16.dp, height = 11.dp)) {
                    val heights = floatArrayOf(.35f, .7f, 1f, .7f, .35f)
                    val bar = size.width / 9f
                    heights.forEachIndexed { i, f ->
                        val h = size.height * f
                        drawRoundRect(tint, Offset(bar * (2 * i), (size.height - h) / 2f), Size(bar, h), CornerRadius(bar / 2f))
                    }
                }
                if (audioCode.isNotEmpty()) Text(audioCode, color = tint, fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp)
            }
            // A speech bubble with two lines of text (dimmed while off) and the language chosen.
            "subtitles" -> Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp)) {
                    val color = tint.copy(alpha = if (subtitlesOn) 1f else .5f)
                    val w = size.width
                    val h = size.height
                    val stroke = 1.6.dp.toPx()
                    drawRoundRect(color, Offset(w * .08f, h * .14f), Size(w * .84f, h * .58f), CornerRadius(w * .14f), style = Stroke(width = stroke))
                    val tail = Path().apply { moveTo(w * .3f, h * .72f); lineTo(w * .3f, h * .9f); lineTo(w * .48f, h * .72f) }
                    drawPath(tail, color, style = Stroke(width = stroke))
                    drawLine(color, Offset(w * .26f, h * .36f), Offset(w * .74f, h * .36f), strokeWidth = stroke, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * .26f, h * .52f), Offset(w * .58f, h * .52f), strokeWidth = stroke, cap = StrokeCap.Round)
                }
                Spacer(Modifier.width(7.dp))
                Text(subtitleLabel, color = tint.copy(alpha = if (subtitlesOn) 1f else .7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
            }
            else -> Canvas(Modifier.size(16.dp)) {
                val w = size.width
                val h = size.height
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
