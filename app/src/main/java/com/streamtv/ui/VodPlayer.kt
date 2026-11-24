package com.streamtv.ui

import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.media3.ui.PlayerView
import com.streamtv.data.Channel
import kotlinx.coroutines.delay

private val VodWhite = Color.White
private val VodMuted get() = ActivePalette.current.overlayMuted
private const val ControlsMillis = 4_500L
/** Left/right let go this long: the film jumps to the chosen place by itself (no OK needed). */
private const val ScrubSettleMillis = 900L
/** While left/right is held, the place moves one step this often. */
private const val HoldStepMillis = 100L

/** Light text over the picture keeps a soft shadow, so it reads on bright scenes too. */
private val ShadowStyle = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 8f), fontFamily = StvFont)

/** One subtitle or sound track found in the file. */
private class TextTrack(val label: String, val language: String?, val group: androidx.media3.common.TrackGroup, val selected: Boolean)

/** One row of a player menu: a choice (with a check mark when in use) or a way into a sub-menu. */
private class MenuRow(val label: String, val detail: String? = null, val checked: Boolean = false, val opens: Boolean = false, val lineAbove: Boolean = false)

/** Fast-forward and rewind speeds 1 to 5: seconds of the title crossed per second. */
private val ScrubSpeeds = longArrayOf(0L, 10_000L, 30_000L, 60_000L, 120_000L, 300_000L)

/** Left/right presses in one direction, close together: the longer the run, the bigger the step. */
private class ScrubRun {
    var lastStep = 0L
}

/**
 * How far one left/right step moves: 10 s for a press, and a steady 30 s per step while the key is
 * held, which never speeds up. A short title never moves more than a fifteenth of its length per step.
 */
internal fun scrubStep(held: Boolean, durationMillis: Long): Long {
    val step = if (held) 30_000L else 10_000L
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
    // Subtitles carried inside the file. The one in the app's language is picked by itself.
    var subtitles by remember { mutableStateOf<List<TextTrack>>(emptyList()) }
    var subtitlesOff by remember { mutableStateOf(false) }
    // The subtitles are set once per title: the app's language when the file has it, otherwise off.
    val subtitlesDecided = remember(player) { booleanArrayOf(false) }
    // Sound tracks in the file (dubbings); the button shows only when there are two or more.
    var sounds by remember { mutableStateOf<List<TextTrack>>(emptyList()) }
    // The menu open above the buttons: "subtitles" (on/off, then Language and Style), its two
    // sub-menus "language" and "style", or "audio"; null when closed. menuIndex: the row under the remote.
    var menu by remember { mutableStateOf<String?>(null) }
    var menuIndex by remember { mutableIntStateOf(0) }
    // The subtitles' look (box or shadow), kept for both players.
    var subtitleBox by remember { mutableStateOf(Players.subtitleBox(context)) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    // The last subtitles turned on, so "On" brings them back.
    var lastSubtitle by remember { mutableIntStateOf(0) }
    // Fast-forward (positive) or rewind (negative) at speed 1 to 5, started by the ⏩ / ⏪ keys; the
    // place keeps moving on its own until OK plays it or Back drops it. 0: plain steps.
    var speed by remember { mutableIntStateOf(0) }
    var speedMode by remember { mutableStateOf(false) }
    val appLanguage = LocalAppLanguage.current
    LaunchedEffect(player, appLanguage) {
        Players.preferSound(player, appLanguage)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(appLanguage).build()
    }
    // Beside the title, at the end of the bar: sound (two tracks or more) and subtitles (any).
    val buttons = listOfNotNull("audio".takeIf { sounds.size > 1 }, "subtitles".takeIf { subtitles.isNotEmpty() })
    val chosenSubtitle = if (subtitlesOff) -1 else subtitles.indexOfFirst { it.selected }
    /** Shows subtitle track [index] of [list]; -1 turns subtitles off. */
    fun chooseSubtitle(index: Int, list: List<TextTrack> = subtitles) {
        val params = player.trackSelectionParameters.buildUpon()
        if (index !in list.indices) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            subtitlesOff = true
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(list[index].group, 0))
            subtitlesOff = false
            lastSubtitle = index
        }
        player.trackSelectionParameters = params.build()
    }
    /** Plays sound track [index] (a dubbing) instead of the one playing. */
    fun chooseSound(index: Int) {
        val track = sounds.getOrNull(index) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(track.group, 0)).build()
    }
    fun setBox(boxed: Boolean) {
        subtitleBox = boxed
        Players.setSubtitleBox(context, boxed)
        playerView?.let { Players.styleSubtitles(it) }
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
    /** Plays the chosen place now (OK). Until then the title goes on playing where it was. */
    fun commitScrub() {
        speedMode = false
        speed = 0
        val target = scrubTarget ?: return
        scrubTarget = null
        player.seekTo(target)
        position = target
        show()
    }
    /**
     * One left/right step; [held] is true for the repeats of a key being held down. Only the
     * fast-forward and rewind keys ([fast]) start speeds 1 to 5; left and right only ever step.
     */
    fun scrub(forward: Boolean, held: Boolean, fast: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val direction = if (forward) 1 else -1
        if (speedMode) {
            // Each press changes the speed by one: the same way faster, the other way slower and
            // then backwards. A held key does nothing more; the place moves on its own.
            if (!held) speed = (speed + direction).coerceIn(-5, 5)
            show()
            return
        }
        // A held key repeats very fast: one step every HoldStepMillis keeps the speed even.
        if (held && now - run.lastStep < HoldStepMillis) return
        run.lastStep = now
        val step = scrubStep(held, knownLength())
        scrubTarget = clampPlace((scrubTarget ?: player.currentPosition) + direction * step)
        if (fast && !held) { speedMode = true; speed = direction }
        show()
    }
    fun togglePlay() {
        if (player.isPlaying || player.playWhenReady) player.pause() else player.play()
        show()
    }
    fun press(name: String) {
        when (name) {
            "subtitles" -> { menu = "subtitles"; menuIndex = if (chosenSubtitle >= 0) 0 else 1; show() }
            "audio" -> { menu = "audio"; menuIndex = sounds.indexOfFirst { it.selected }.coerceAtLeast(0); show() }
        }
    }
    fun leave() { save(); onExit() }

    // Read here: the player's listener below is not part of the screen and cannot translate.
    val subtitleWord = t("Subtitles")
    val soundWord = t("Audio")
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
                        ?: "$subtitleWord ${index + 1}"
                    TextTrack(label, language, group.mediaTrackGroup, group.isSelected)
                }
                sounds = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }.mapIndexed { index, group ->
                    val format = group.getTrackFormat(0)
                    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
                    val name = format.label?.takeIf { it.isNotBlank() }
                        ?: language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale(appLanguage)).replaceFirstChar { c -> c.uppercase() } }
                        ?: "$soundWord ${index + 1}"
                    // "5.1" beside the name when the track is surround.
                    val label = if (format.channelCount >= 6) "$name 5.1" else name
                    TextTrack(label, language, group.mediaTrackGroup, group.isSelected)
                }
                // First time the title's subtitles are known: the viewer's language on, or none at all.
                val found = subtitles
                if (!subtitlesDecided[0] && found.isNotEmpty()) {
                    subtitlesDecided[0] = true
                    chooseSubtitle(found.indexOfFirst { sameLanguage(it.language, appLanguage) }, found)
                }
            }
        }
        player.addListener(listener)
        onDispose { save(); player.removeListener(listener); player.release() }
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
                    speedMode = false
                    speed = 0
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
    // Choosing a place ends by itself: once the arrows rest, the film goes there, as on Apple TV.
    LaunchedEffect(scrubTarget, speedMode) {
        if (scrubTarget != null && !speedMode) {
            delay(ScrubSettleMillis)
            commitScrub()
        }
    }
    // Fast-forward / rewind: the place moves on its own at the chosen speed until OK or Back.
    LaunchedEffect(speedMode, speed) {
        if (!speedMode || speed == 0) return@LaunchedEffect
        val perTick = ScrubSpeeds[kotlin.math.abs(speed)] / 10 * (if (speed > 0) 1 else -1)
        while (true) {
            delay(100)
            val from = scrubTarget ?: player.currentPosition
            val next = clampPlace(from + perTick)
            scrubTarget = next
            touch++
            // At the start or the end it stops there, waiting for OK.
            if (next == from) { speed = 0; break }
        }
    }
    // The controls step away on their own while the title plays.
    LaunchedEffect(controls, touch, playing, (menu != null)) {
        if (controls && playing && !failed && scrubTarget == null && !(menu != null)) {
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
            menu == "language" || menu == "style" -> { menuIndex = if (menu == "language") 2 else 3; menu = "subtitles"; show() }
            (menu != null) -> { menu = null; show() }
            scrubTarget != null -> { scrubTarget = null; speedMode = false; speed = 0; show() }
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
                    // A menu takes every key while open: up/down move, OK picks, right opens a sub-menu,
                    // left or Back goes back up, Back on the first level closes.
                    if ((menu != null)) {
                        val count = when (menu) {
                            "subtitles" -> 4
                            "language" -> subtitles.size
                            "style" -> 2
                            else -> sounds.size
                        }
                        val ok = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter || event.key == Key.ButtonSelect
                        when {
                            event.key == Key.Back -> return@onPreviewKeyEvent false
                            event.key == Key.DirectionUp -> menuIndex = (menuIndex - 1).coerceAtLeast(0)
                            event.key == Key.DirectionDown -> menuIndex = (menuIndex + 1).coerceAtMost((count - 1).coerceAtLeast(0))
                            event.key == Key.DirectionLeft && (menu == "language" || menu == "style") -> {
                                menuIndex = if (menu == "language") 2 else 3; menu = "subtitles"
                            }
                            (ok && !held) || (event.key == Key.DirectionRight && menu == "subtitles" && menuIndex >= 2) -> when (menu) {
                                "subtitles" -> when (menuIndex) {
                                    0 -> { chooseSubtitle(lastSubtitle.coerceIn(0, (subtitles.size - 1).coerceAtLeast(0))); menu = null }
                                    1 -> { chooseSubtitle(-1); menu = null }
                                    2 -> { menu = "language"; menuIndex = chosenSubtitle.coerceAtLeast(0) }
                                    else -> { menu = "style"; menuIndex = if (subtitleBox) 0 else 1 }
                                }
                                "language" -> { chooseSubtitle(menuIndex); menu = null }
                                "style" -> { setBox(menuIndex == 0); menu = null }
                                else -> { chooseSound(menuIndex); menu = null }
                            }
                            else -> Unit
                        }
                        show()
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.Back -> false
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { if (!held) togglePlay(); true }
                        Key.MediaFastForward -> { if (!failed) scrub(forward = true, held = held, fast = true); true }
                        Key.MediaRewind -> { if (!failed) scrub(forward = false, held = held, fast = true); true }
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
                        Players.styleSubtitles(this)
                        playerView = this
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
                        .background(ActivePalette.current.panel).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
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
                        // The subtitles list rises above the CC box, at the end of the bar; it takes no
                        // room of its own, so nothing moves when it opens.
                        Layout(
                            modifier = Modifier.fillMaxWidth(),
                            content = {
                                AnimatedVisibility(
                                    visible = (menu != null),
                                    enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = .82f, stiffness = 520f), initialScale = .9f, transformOrigin = TransformOrigin(1f, 1f)),
                                    exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = .94f, transformOrigin = TransformOrigin(1f, 1f))
                                ) {
                                    // What the open menu lists (kept while it fades out).
                                    val shownMenu = menu ?: "subtitles"
                                    val chosenLabel = subtitles.getOrNull(chosenSubtitle)?.label
                                    val styleLabel = t(if (subtitleBox) "Dark background" else "Transparent background")
                                    when (shownMenu) {
                                        "subtitles" -> PlayerMenu(t("Subtitles"), listOf(
                                            MenuRow(t("On") + (subtitles.getOrNull(if (chosenSubtitle >= 0) chosenSubtitle else lastSubtitle)?.label?.let { " ($it)" } ?: ""), checked = chosenSubtitle >= 0),
                                            MenuRow(t("Off"), checked = chosenSubtitle < 0),
                                            MenuRow(t("Language"), detail = chosenLabel ?: subtitles.getOrNull(lastSubtitle)?.label, opens = true, lineAbove = true),
                                            MenuRow(t("Style"), detail = styleLabel, opens = true)
                                        ), menuIndex)
                                        "language" -> PlayerMenu(t("Language"), subtitles.mapIndexed { i, track -> MenuRow(track.label, checked = i == chosenSubtitle) }, menuIndex)
                                        "style" -> PlayerMenu(t("Style"), listOf(
                                            MenuRow(t("Dark background"), checked = subtitleBox),
                                            MenuRow(t("Transparent background"), checked = !subtitleBox)
                                        ), menuIndex)
                                        else -> PlayerMenu(t("Audio"), sounds.map { MenuRow(it.label, checked = it.selected) }, menuIndex)
                                    }
                                }
                            }
                        ) { measurables, constraints ->
                            val menu = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            layout(constraints.maxWidth, 0) {
                                menu?.place(constraints.maxWidth - menu.width, -menu.height - 10.dp.roundToPx())
                            }
                        }
                        // The title (its year small above it) on the left, the round buttons on the right,
                        // both just above the bar.
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                if (channel.year > 0) Text(channel.year.toString(), color = VodWhite.copy(alpha = .85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = ShadowStyle)
                                Text(
                                    channel.name, color = VodWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = ShadowStyle
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                buttons.forEachIndexed { index, name ->
                                    RoundIconButton(
                                        name, selected = row == 1 && button == index || menu?.let { (it == "audio") == (name == "audio") } == true,
                                        on = name == "audio" || chosenSubtitle >= 0
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TimeBar(
                            position = scrubTarget ?: position, duration = duration, playing = playing,
                            selected = row == 0, scrubbing = scrubTarget != null,
                            jump = scrubTarget?.let { it - player.currentPosition },
                            speed = if (speedMode) speed else null
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
    speed: Int? = null
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val lift by animateFloatAsState(if (selected || scrubbing) 1f else 0f, spring(stiffness = 500f), label = "bar")
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val y = size.height / 2f
            val thick = 5.dp.toPx() + 2.dp.toPx() * lift
            val corner = CornerRadius(thick / 2f)
            drawRoundRect(Color(0x47FFFFFF), topLeft = Offset(0f, y - thick / 2f), size = Size(size.width, thick), cornerRadius = corner)
            val x = size.width * fraction
            if (x > 0f) {
                drawRoundRect(ActivePalette.current.seek, topLeft = Offset(0f, y - thick / 2f), size = Size(x.coerceAtLeast(thick), thick), cornerRadius = corner)
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
                // While choosing a place: the fast-forward / rewind speed (1 to 5), else how far it
                // is from where the title is now.
                Text(
                    speed?.let { (if (it < 0) "◀◀ " else "▶▶ ") + kotlin.math.abs(it) }
                        ?: jump?.let { (if (it < 0) "−" else "+") + formatTime(kotlin.math.abs(it)) } ?: "",
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

/** True when a track's language code ("ar", "ara", "ar-SA", "fre"...) is the app's language. */
private fun sameLanguage(code: String?, app: String): Boolean {
    val c = code?.lowercase()?.substringBefore('-')?.substringBefore('_')?.takeIf { it.isNotBlank() } ?: return false
    if (c == app) return true
    val iso3 = runCatching { java.util.Locale(app).isO3Language }.getOrNull()
    val older = mapOf("fr" to "fre", "de" to "ger", "nl" to "dut", "zh" to "chi", "ar" to "arb")
    return c == iso3 || c == older[app]
}

/**
 * A round button as on Apple TV: a sound wave for the sound tracks, a speech bubble for the
 * subtitles. White with a dark mark while selected, an outlined ring otherwise.
 */
@Composable
private fun RoundIconButton(name: String, selected: Boolean, on: Boolean) {
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = .7f, stiffness = 450f), label = "roundButton")
    val ink = if (selected) Color.Black else VodWhite
    Canvas(Modifier.size(40.dp).scale(scale)) {
        val w = size.width
        val h = size.height
        if (selected) drawCircle(VodWhite) else {
            drawCircle(Color(0x33000000))
            drawCircle(VodWhite.copy(alpha = .85f), style = Stroke(width = 1.6.dp.toPx()))
        }
        val line = 2.dp.toPx()
        if (name == "audio") {
            // Five bars of a sound wave.
            val heights = floatArrayOf(.18f, .34f, .46f, .34f, .18f)
            val gap = w * .1f
            val left = w / 2f - gap * 2f
            for (i in heights.indices) {
                val x = left + gap * i
                val half = h * heights[i] / 2f
                drawLine(ink, Offset(x, h / 2f - half), Offset(x, h / 2f + half), strokeWidth = line, cap = StrokeCap.Round)
            }
        } else {
            // A speech bubble with two lines of text; filled when the subtitles are on.
            val bubble = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(w * .26f, h * .3f, w * .74f, h * .64f, CornerRadius(w * .07f)))
                moveTo(w * .36f, h * .63f); lineTo(w * .36f, h * .74f); lineTo(w * .47f, h * .63f); close()
            }
            if (on) drawPath(bubble, ink) else drawPath(bubble, ink, style = Stroke(width = line * .8f))
            val text = if (on) (if (selected) VodWhite else Color.Black) else ink
            drawLine(text, Offset(w * .34f, h * .42f), Offset(w * .66f, h * .42f), strokeWidth = line * .8f, cap = StrokeCap.Round)
            drawLine(text, Offset(w * .34f, h * .52f), Offset(w * .58f, h * .52f), strokeWidth = line * .8f, cap = StrokeCap.Round)
        }
    }
}

/**
 * A small menu above the buttons, the same size whatever it lists: five rows at a time that
 * scroll around the one under the remote, a check mark on what is in use, and "›" on the rows
 * that open a sub-menu (with what is chosen there under their name). A thin bar on its edge shows
 * where in a long list it is.
 */
@Composable
private fun PlayerMenu(title: String, rows: List<MenuRow>, focused: Int) {
    val visible = 5
    val start = (focused - visible / 2).coerceIn(0, (rows.size - visible).coerceAtLeast(0))
    Row(
        Modifier.width(240.dp).clip(RoundedCornerShape(18.dp)).background(ActivePalette.current.panel)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
            .padding(6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VodMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp))
            for (i in start until minOf(rows.size, start + visible)) {
                val row = rows[i]
                if (row.lineAbove) Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Color(0x26FFFFFF)))
                val lit = i == focused
                val fill by androidx.compose.animation.animateColorAsState(if (lit) ActivePalette.current.lit else Color.Transparent, tween(140), label = "menuRow")
                val ink = if (lit) ActivePalette.current.onLit else VodWhite
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (row.checked) "✓" else "", Modifier.width(20.dp), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text(row.label, color = ink, fontSize = 14.sp, fontWeight = if (row.checked || row.opens) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        row.detail?.let {
                            Text(it, color = ink.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (row.opens) Text("›", color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (rows.size > visible) {
            val share = visible.toFloat() / rows.size
            val offset = start.toFloat() / rows.size
            Box(Modifier.padding(start = 4.dp, top = 26.dp, bottom = 4.dp).width(3.dp).height((visible * 32).dp).clip(CircleShape).background(Color(0x22FFFFFF))) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(share).offset(y = (visible * 32).dp * offset).clip(CircleShape).background(Color(0x99FFFFFF)))
            }
        }
    }
}

@Composable
private fun VodTextButton(label: String, selected: Boolean) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) ActivePalette.current.lit else Color(0x29FFFFFF))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) ActivePalette.current.onLit else VodWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
