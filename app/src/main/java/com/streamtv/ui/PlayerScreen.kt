package com.streamtv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamtv.data.Channel
import com.streamtv.data.ChannelKind
import com.streamtv.data.kind
import com.streamtv.data.hasFavorite
import com.streamtv.data.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PlayerWhite = Color.White
private val PlayerMuted = Color(0xFFB5B5BA)
private val PlayerGlass = Color(0xF01C1C1E)
private val PlayerGlassBorder = Color(0x33FFFFFF)
private const val InfoBarMillis = 4000L
private const val ListIdleMillis = 8000L
private const val StallMillis = 15000L
/** Pauses before each automatic reconnect, then the error card. */
private val RetryPauses = longArrayOf(300, 1_000, 2_000, 3_000, 5_000, 8_000)
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
    queueTitle: String,
    allChannels: List<Channel>,
    recent: List<String>,
    programs: List<EpgProgram>,
    favorites: Set<String>,
    onBack: () -> Unit,
    onZap: (Int) -> Unit,
    onSelect: (Channel, List<Channel>, String) -> Unit,
    /** Holding OK opens the channel card (the same one as on the channel tiles). */
    onOptions: () -> Unit = {}
) {
    val context = LocalContext.current
    // Live channels: a first-times tip on the remote's keys while watching.
    LaunchedEffect(Unit) {
        if (channel.kind() == ChannelKind.Live) Hints.offer(context, "player", "↑↓ change channel · ←→ channel list · Hold OK for options")
    }
    // In a right-to-left layout the panel sits on the right, so the arrow keys swap roles.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val side = if (rtl) 1 else -1
    // One player for the whole time the player is open: a channel change hands it the new stream
    // (below). A new player per channel, with the old one shut down on the main thread, held the
    // screen for a moment on every change: the bar's name trembled and zapping felt laggy.
    val player = remember { Players.create(context) }
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var autoRetries by remember { mutableIntStateOf(0) }
    var overlayState by remember { mutableStateOf(OverlayState.InfoBar) }
    var infoBarTick by remember { mutableIntStateOf(0) }
    val playerFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val currentChannelIndex = channels.indexOfFirst { it.key == channel.key }.coerceAtLeast(0)
    val current = playerProgram(channel, programs)

    // The lists the side panel can page through with left/right: only the list the channel was
    // opened from, Favorites and Last watched. Fewer pages, no going round in circles.
    val isLive = remember(channel.url) { channel.kind() == ChannelKind.Live }
    // Built off the main thread: sorting a whole playlist on every channel change made the
    // open panel stutter for a moment when a channel was picked.
    var liveLists by remember { mutableStateOf<List<Pair<String, List<Channel>>>>(listOf(queueTitle to channels)) }
    LaunchedEffect(allChannels, recent, favorites, queueTitle, channels, isLive) {
        val built = if (!isLive) listOf(queueTitle to channels) else withContext(Dispatchers.Default) { buildList {
            val live = allChannels.filter { it.kind() == ChannelKind.Live }
            val byKey = live.associateBy { it.key }
            val watched = recent.mapNotNull { byKey[it] }
            add(queueTitle to channels)
            val starred = live.filter { favorites.hasFavorite(it) }
            if (starred.isNotEmpty() && queueTitle != "Favorites") add("Favorites" to starred)
            if (watched.isNotEmpty() && queueTitle != "Last watched") add("Last watched" to watched)
        } }
        liveLists = built
    }
    // Keep the side panel's pages and their order stable for the entire time it is open.
    // Fresh recent-watch data is picked up the next time the viewer opens the panel.
    var frozenLists by remember { mutableStateOf<List<Pair<String, List<Channel>>>?>(null) }
    val lists = frozenLists ?: liveLists
    var listTitle by remember { mutableStateOf(queueTitle) }
    var listDirection by remember { mutableIntStateOf(1) }
    var listTick by remember { mutableIntStateOf(0) }
    // False right after opening (focus goes to the playing channel); true once the user pages.
    var listPaged by remember { mutableStateOf(false) }
    val listNameFocusRequester = remember { FocusRequester() }
    var nameFocused by remember { mutableStateOf(false) }
    // The list of lists, opened with OK on the list name.
    var pickerOpen by remember { mutableStateOf(false) }
    val lastBack = remember { longArrayOf(0L) }
    val lastStep = remember { longArrayOf(0L) }
    val okArmed = remember { booleanArrayOf(false) }
    // OK while watching: a short press acts on release, a held press opens the channel card.
    val okShort = remember { booleanArrayOf(false) }
    val listIndex = lists.indexOfFirst { it.first == listTitle }.coerceAtLeast(0)
    fun openList() {
        frozenLists = liveLists
        listTitle = queueTitle
        listPaged = false
        nameFocused = false
        pickerOpen = false
        listTick++
        overlayState = OverlayState.ChannelList
    }
    fun stepList(delta: Int) {
        if (lists.size < 2) return
        // A held arrow repeats many times a second; page at a calm pace so the slide of one
        // list finishes before the next begins, instead of piling animations on each other.
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastStep[0] < 260L) return
        lastStep[0] = now
        listDirection = delta
        // Paging is done on the list name: it takes focus, the channels below just follow.
        listPaged = true
        runCatching { listNameFocusRequester.requestFocus() }
        listTitle = lists[(listIndex + delta + lists.size) % lists.size].first
    }

    // A fresh start always joins the stream at its live point, never where it dropped.
    fun restart() {
        failed = false
        buffering = true
        player.stop()
        player.setMediaSource(Players.stream(context, channel.url))
        player.prepare()
        player.playWhenReady = true
    }
    // A dropped stream reconnects by itself, quietly, a few times with growing pauses (the spinner
    // stays up); the error card shows only if it still fails. Once it plays again the count starts
    // over, so a long match survives any number of short drops.
    val retryScope = rememberCoroutineScope()
    val retryJob = remember { arrayOfNulls<Job>(1) }
    fun onStreamProblem() {
        if (retryJob[0]?.isActive == true) return
        if (autoRetries < RetryPauses.size) {
            val pause = RetryPauses[autoRetries]
            autoRetries++
            buffering = true
            player.stop()
            retryJob[0] = retryScope.launch { delay(pause); restart() }
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
    // A held up/down still zaps, but at a calm pace: each zap starts a new stream, and dozens a
    // second only pile up half-started downloads (which also drag the picture quality down).
    val lastZap = remember { longArrayOf(0L) }
    fun zap(delta: Int, held: Boolean) {
        val now = android.os.SystemClock.uptimeMillis()
        if (held && now - lastZap[0] < 350L) return
        lastZap[0] = now
        onZap(delta)
        showInfoBar()
    }

    // A channel change (and the first channel): the same player takes the new stream at its live
    // point; a retry still pending for the previous channel is dropped.
    LaunchedEffect(channel.url) {
        retryJob[0]?.cancel()
        failed = false
        buffering = true
        autoRetries = 0
        player.stop()
        player.setMediaSource(Players.stream(context, channel.url))
        player.prepare()
        player.playWhenReady = true
    }
    // The player's listener and the lifecycle observer live as long as the player, so they call
    // the newest versions of these (made for the channel now playing), not the first ones.
    val streamProblem by rememberUpdatedState(newValue = { onStreamProblem() })
    val restartNow by rememberUpdatedState(newValue = { restart() })
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> buffering = true
                    Player.STATE_READY -> { buffering = false; failed = false; autoRetries = 0 }
                    Player.STATE_ENDED -> streamProblem()
                    else -> Unit
                }
            }
            override fun onPlayerError(error: PlaybackException) = streamProblem()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    // Home (or anything else that takes the app off screen) stops playback, sound included;
    // coming back starts the channel again.
    val lifecycleOwner = LocalLifecycleOwner.current
    val stoppedInBackground = remember { booleanArrayOf(false) }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stoppedInBackground[0] = true
                    buffering = false
                    player.playWhenReady = false
                    player.stop()
                }
                Lifecycle.Event.ON_START -> if (stoppedInBackground[0]) {
                    stoppedInBackground[0] = false
                    restartNow()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A stream that buffers forever is treated like a broken one.
    LaunchedEffect(player, channel.url, buffering, failed, autoRetries) {
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
    // The list stays open after choosing a channel and closes only when left alone for a while.
    LaunchedEffect(overlayState, listTick) {
        if (overlayState == OverlayState.ChannelList) {
            delay(ListIdleMillis)
            if (overlayState == OverlayState.ChannelList) overlayState = OverlayState.None
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

    // Back from the channels goes to the list name, from the list name it closes the panel.
    fun backInList() {
        when {
            pickerOpen -> {
                pickerOpen = false
                listPaged = true
                nameFocused = true
                runCatching { listNameFocusRequester.requestFocus() }
            }
            nameFocused -> overlayState = OverlayState.None
            else -> {
                // Set directly so the next Back always closes, even if focus could not move.
                nameFocused = true
                runCatching { listNameFocusRequester.requestFocus() }
            }
        }
    }
    // Back: closes the channel list, else hides the info bar at the bottom, and only then leaves the player.
    fun back() {
        // The same press can arrive twice (as a key and as a system back); count it once, or the
        // second would leave the player right after the first hid the bar or closed the list.
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBack[0] < 300L) return
        lastBack[0] = now
        when (overlayState) {
            OverlayState.ChannelList -> backInList()
            OverlayState.InfoBar -> overlayState = OverlayState.None
            else -> onBack()
        }
    }
    BackHandler { back() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(playerFocusRequester).focusable()
            .onPreviewKeyEvent { event ->
                val listOpen = overlayState == OverlayState.ChannelList
                if (event.type == KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent when (event.key) {
                        // Back is fully handled on key down; swallow the release so nothing else reacts.
                        Key.Back -> true
                        // The list of lists opens on release, so the same press cannot also pick a row in it.
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonSelect -> when {
                            okArmed[0] -> { okArmed[0] = false; pickerOpen = true; true }
                            okShort[0] -> {
                                okShort[0] = false
                                // Short press only shows the info bar; favourites are in the hold-OK options.
                                showInfoBar()
                                true
                            }
                            else -> false
                        }
                        else -> false
                    }
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (listOpen) listTick++
                when (event.key) {
                    // With the channel list open, CH+ / CH− page through it instead (handled by the list).
                    Key.ChannelUp -> if (listOpen) false else { zap(1, event.nativeKeyEvent.repeatCount > 0); true }
                    Key.ChannelDown -> if (listOpen) false else { zap(-1, event.nativeKeyEvent.repeatCount > 0); true }
                    // Handled here, before the system moves focus on its own for the Back key.
                    Key.Back -> { back(); true }
                    Key.DirectionUp -> if (listOpen) false else { zap(1, event.nativeKeyEvent.repeatCount > 0); true }
                    Key.DirectionDown -> if (listOpen) false else { zap(-1, event.nativeKeyEvent.repeatCount > 0); true }
                    // On the list name, left/right page between lists; on the channels they close the panel.
                    // "Open" is the arrow pointing at the panel's side of the screen.
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val step = if ((event.key == Key.DirectionRight) != rtl) 1 else -1
                        // A held arrow repeats many times a second. Only paging the lists uses the
                        // repeats; opening and closing the panel take the first press only, so a held
                        // arrow opens the panel once and leaves it open instead of flashing it on and off.
                        val held = event.nativeKeyEvent.repeatCount > 0
                        when {
                            listOpen && pickerOpen -> true
                            listOpen && nameFocused -> { stepList(step); true }
                            held -> true
                            listOpen -> { overlayState = OverlayState.None; true }
                            showErrorCard -> false
                            // Either arrow brings the list up; the same arrow puts it away again.
                            else -> { openList(); true }
                        }
                    }
                    // OK only brings up the info bar; the channel list opens with the arrow key.
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter,
                    Key.ButtonSelect -> when {
                        // OK on the list name opens the list of lists (on release, see above).
                        listOpen && nameFocused && !pickerOpen -> { okArmed[0] = true; true }
                        listOpen || showErrorCard -> false
                        // First key down arms a short press; a repeat means the key is being held.
                        event.nativeKeyEvent.repeatCount == 0 -> { okShort[0] = true; true }
                        okShort[0] -> {
                            okShort[0] = false
                            // The card opens in its own window, which also takes the release of this press.
                            // The info bar steps away so the two never sit on top of each other.
                            overlayState = OverlayState.None
                            onOptions()
                            true
                        }
                        else -> true
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
                    // The screen stays on while a channel is on screen, not while browsing menus.
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
                Text(t("Can't play this channel"), color = PlayerWhite, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(channelDisplayName(channel.name), color = PlayerMuted, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(t("The stream is not responding right now."), color = PlayerMuted, fontSize = 14.sp,
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
                // "Ch" centred over the number; a longer number widens the column smoothly.
                Column(
                    // Room for three digits, so the bar keeps still while zapping; only a fourth widens it.
                    Modifier.widthIn(min = 64.dp).animateContentSize(spring(dampingRatio = .85f, stiffness = 420f)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Ch", color = PlayerMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    // The number in the red of the logo's S: a touch of colour in the bar.
                    Text((currentChannelIndex + 1).toString(), color = Color(0xFFE8452F), fontSize = 30.sp,
                        lineHeight = 32.sp, fontWeight = FontWeight.Bold, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false)
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
                    // The progress line always keeps its room (just hidden without a guide), so the
                    // name sits at the same height on every channel and does not jump while zapping.
                    Spacer(Modifier.height(6.dp))
                    val progress = if (current == null) 0f else ((System.currentTimeMillis() - current.startEpochMillis).toFloat() /
                        (current.endEpochMillis - current.startEpochMillis).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(3.dp).graphicsLayer { alpha = if (current == null) 0f else 1f }
                        .clip(CircleShape).background(PlayerWhite.copy(alpha = .20f))) {
                        Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(PlayerAccent))
                    }
                }
                Spacer(Modifier.width(14.dp))
                ChannelLogo(channel.name, channel.logo, Modifier.size(width = 64.dp, height = 44.dp), fontSize = 18.sp)
            }
        }

        AnimatedVisibility(
            visible = overlayState == OverlayState.ChannelList,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(24.dp).width(340.dp),
            enter = slideInHorizontally(initialOffsetX = { side * it },
                animationSpec = spring(dampingRatio = .86f, stiffness = 300f)) + fadeIn(tween(220)),
            exit = slideOutHorizontally(targetOffsetX = { side * it }, animationSpec = tween(220)) + fadeOut(tween(180))
        ) {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                    .background(PlayerGlass)
                    .border(1.dp, PlayerGlassBorder, RoundedCornerShape(28.dp))
                    .padding(top = 10.dp)
            ) {
                val many = lists.size > 1
                val pop = remember { Animatable(1f) }
                LaunchedEffect(listTitle) {
                    if (listPaged) {
                        pop.snapTo(1.07f)
                        pop.animateTo(1f, spring(dampingRatio = .6f, stiffness = 420f))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (nameFocused) Color(0x29FFFFFF) else Color.Transparent)
                        .border(1.dp, if (nameFocused) Color(0x66FFFFFF) else Color.Transparent, RoundedCornerShape(18.dp))
                        .focusRequester(listNameFocusRequester)
                        .onFocusChanged { nameFocused = it.isFocused }
                        .focusable()
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val arrow = if (!many || pickerOpen) 0f else if (nameFocused) 1f else .5f
                    Text(if (rtl) "›" else "‹", color = PlayerWhite.copy(alpha = arrow), fontSize = 21.sp)
                    AnimatedContent(
                        targetState = listTitle,
                        modifier = Modifier.weight(1f).scale(pop.value),
                        transitionSpec = {
                            (slideInHorizontally(tween(180)) { listDirection * it / 4 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(120)) { -listDirection * it / 4 } + fadeOut(tween(100)))
                        },
                        label = "listTitle"
                    ) { title ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t(title), color = PlayerWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                            val size = lists.firstOrNull { it.first == title }?.second?.size ?: 0
                            Text(if (size == 1) "1 ${t("channel")}" else "$size ${t("channels")}", color = Color(0x99FFFFFF), fontSize = 11.sp, maxLines = 1, lineHeight = 13.sp)
                        }
                    }
                    Text(if (rtl) "‹" else "›", color = PlayerWhite.copy(alpha = arrow), fontSize = 21.sp)
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState = if (pickerOpen) ListPickerKey else listTitle,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (slideInHorizontally(tween(200)) { listDirection * it / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(130)) { -listDirection * it / 3 } + fadeOut(tween(110)))
                    },
                    label = "listPage"
                ) { title ->
                    if (title == ListPickerKey) {
                        ListPicker(lists, listTitle) { picked ->
                            listDirection = 1
                            listTitle = picked
                            listPaged = false
                            nameFocused = false
                            pickerOpen = false
                        }
                    } else {
                        val page = lists.firstOrNull { it.first == title }?.second.orEmpty()
                        ChannelListPage(page, channel, takeFocus = !listPaged, onClose = { overlayState = OverlayState.None }) { item ->
                            onSelect(item, page, title)
                        }
                    }
                }
            }
        }
    }
}

private const val ListPickerKey = "\u0000lists"

/** Every list by name, so the viewer can jump straight to one. Opens focused on the current list. */
@Composable
private fun ListPicker(lists: List<Pair<String, List<Channel>>>, current: String, onPick: (String) -> Unit) {
    val currentIndex = remember(lists) { lists.indexOfFirst { it.first == current }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    val anchor = remember { FocusRequester() }
    LaunchedEffect(Unit) { anchor.requestFocusSafely() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(lists.size, key = { lists[it].first }) { index ->
            val (name, channels) = lists[index]
            val isCurrent = index == currentIndex
            var focused by remember { mutableStateOf(false) }
            val itemScale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = .78f, stiffness = 420f), label = "listItemScale")
            Row(
                (if (isCurrent) Modifier.focusRequester(anchor) else Modifier)
                    .fillMaxWidth().scale(itemScale).clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            focused -> Color(0xF2F2F2F7)
                            isCurrent -> Color(0x1FFFFFFF)
                            else -> Color.Transparent
                        }
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(name) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t(name), Modifier.weight(1f), color = if (focused) Color(0xFF1C1C1E) else PlayerWhite, fontSize = 17.sp,
                    fontWeight = if (isCurrent || focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(10.dp))
                Text(channels.size.toString(), color = if (focused) Color(0x991C1C1E) else Color(0x99FFFFFF), fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

/** One list of the side panel. Opens scrolled to, and focused on, the playing channel. */
@Composable
private fun ChannelListPage(items: List<Channel>, playing: Channel, takeFocus: Boolean, onClose: () -> Unit, onPick: (Channel) -> Unit) {
    val playingIndex = remember(items) { items.indexOfFirst { it.key == playing.key } }
    val anchorKey = remember(items) { (items.getOrNull(playingIndex) ?: items.firstOrNull())?.key }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (playingIndex - 2).coerceAtLeast(0))
    val anchor = remember { FocusRequester() }
    // Keep one authoritative focused row for the whole list. Per-row remembered focus
    // flags can briefly disagree while Compose dispatches focus loss/gain callbacks.
    var focusedKey by remember(items) { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (takeFocus) anchor.requestFocusSafely() }
    // CH+ / CH− (Page up / down) jump eight rows at a time; held, at a calm fixed pace.
    val rowFocus = remember(items) { HashMap<String, FocusRequester>() }
    val pageScope = rememberCoroutineScope()
    val lastPage = remember { longArrayOf(0L) }
    fun page(direction: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPage[0] < 140L) return
        lastPage[0] = now
        val from = items.indexOfFirst { it.key == focusedKey }.coerceAtLeast(0)
        val to = (from + direction * 8).coerceIn(0, items.lastIndex)
        if (to == from) return
        pageScope.launch {
            listState.scrollToItem((to - 2).coerceAtLeast(0))
            withFrameNanos { }
            runCatching { rowFocus[items[to].key]?.requestFocus() }
        }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.ChannelDown, Key.PageDown -> 1
            Key.ChannelUp, Key.PageUp -> -1
            else -> 0
        }
        if (direction == 0) false else { if (event.type == KeyEventType.KeyDown) page(direction); true }
    }) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room on every side so the 1.04 focus scale and the last row are never clipped.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
        // Compact rows: about half as many again fit on screen, with the same logo, name, group and quality.
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items, key = { it.key }) { item ->
            val isPlaying = item.key == playing.key
            val focused = focusedKey == item.key
            // The "now playing" mark fades out instead of snapping off when another channel is picked.
            val playingMark by animateFloatAsState(if (isPlaying) 1f else 0f, tween(260), label = "playingMark")
            val itemScale by animateFloatAsState(
                if (focused) 1.04f else 1f,
                spring(dampingRatio = .78f, stiffness = 420f),
                label = "channelItemScale"
            )
            Row(
                (if (item.key == anchorKey) Modifier.focusRequester(anchor) else Modifier)
                    .focusRequester(rowFocus.getOrPut(item.key) { FocusRequester() })
                    .fillMaxWidth().scale(itemScale).clip(RoundedCornerShape(16.dp))
                    // A calm blue-grey for the focused row (as elsewhere in the app), not a bright white one.
                    .background(
                        if (focused) Brush.verticalGradient(listOf(Color(0xFF7C889D), Color(0xFF5F6B80)))
                        else SolidColor(Color.White.copy(alpha = .10f * playingMark))
                    )
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            focusedKey = item.key
                        } else if (focusedKey == item.key) {
                            // An older row's delayed focus-loss callback must not clear a
                            // newer row that has already acquired focus.
                            focusedKey = null
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (isPlaying) onClose() else onPick(item) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelLogo(item.name, item.logo, Modifier.size(32.dp), fontSize = 12.sp, corner = 7.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        channelDisplayName(item.name),
                        color = if (focused) Color.White else Color.White.copy(alpha = .80f),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.group,
                        color = Color.White.copy(alpha = if (focused) .72f else .46f),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val quality = channelQuality(item.name)
                if (quality.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = if (focused) .16f else .10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(quality, color = Color.White.copy(alpha = if (focused) .85f else .60f), fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
    // A thin, faint bar on the edge shows where in the list you are.
    ScrollIndicator(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 6.dp, bottom = 22.dp, end = 5.dp))
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
        Text(t(label), color = if (focused) Color.Black else PlayerWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun playerProgram(channel: Channel, programs: List<EpgProgram>): EpgProgram? {
    val now = System.currentTimeMillis()
    return programs.firstOrNull {
        (it.channelId == channel.id || it.channelId.equals(channel.name, true)) &&
            it.startEpochMillis <= now && it.endEpochMillis > now
    }
}

internal fun channelDisplayName(name: String): String =
    name.replace(Regex("\\s*\\((?:\\d{3,4}p|4K|HD|SD)\\)\\s*", RegexOption.IGNORE_CASE), " ").trim()

internal fun channelQuality(name: String): String =
    Regex("\\b(\\d{3,4}p|4K|HD|SD)\\b", RegexOption.IGNORE_CASE).find(name)?.value?.uppercase().orEmpty()
