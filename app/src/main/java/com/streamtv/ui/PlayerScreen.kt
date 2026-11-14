package com.streamtv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import com.streamtv.data.ChannelKind
import com.streamtv.data.kind
import com.streamtv.data.EpgProgram
import kotlinx.coroutines.delay

private val PlayerWhite = Color.White
private val PlayerMuted = Color(0xFFB5B5BA)
private val PlayerGlass = Color(0xF01C1C1E)
private val PlayerGlassBorder = Color(0x33FFFFFF)
private const val InfoBarMillis = 4000L
private const val ListIdleMillis = 8000L
private const val StallMillis = 20000L
private val PlayerAccent = Color(0xFFD5DADF)

private enum class OverlayState { None, InfoBar, ChannelList, Options }

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
    onFavorite: (Channel) -> Unit,
    locked: Boolean = false,
    onOption: (String) -> Unit = {}
) {
    val context = LocalContext.current
    // In a right-to-left layout the panel sits on the right, so the arrow keys swap roles.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val side = if (rtl) 1 else -1
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

    // The lists the side panel can page through with left/right.
    val isLive = remember(channel.url) { channel.kind() == ChannelKind.Live }
    val lists: List<Pair<String, List<Channel>>> = remember(allChannels, recent, favorites, queueTitle, channels, isLive) {
        if (!isLive) listOf(queueTitle to channels) else buildList {
            val live = allChannels.filter { it.kind() == ChannelKind.Live }
            val byKey = live.associateBy { it.key }
            val watched = recent.mapNotNull { byKey[it] }
            if (watched.isNotEmpty()) add("Last watched" to watched)
            add("All channels" to live)
            val starred = live.filter { it.id in favorites }
            if (starred.isNotEmpty()) add("Favorites" to starred)
            live.groupBy { it.group.ifBlank { "Other" } }.toSortedMap().forEach { (name, items) -> add(name to items) }
            if (none { it.first == queueTitle }) add(0, queueTitle to channels)
        }
    }
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
    val okArmed = remember { booleanArrayOf(false) }
    // OK while watching: a short press acts on release, a held press opens the options panel.
    val okShort = remember { booleanArrayOf(false) }
    val okSwallow = remember { booleanArrayOf(false) }
    val listIndex = lists.indexOfFirst { it.first == listTitle }.coerceAtLeast(0)
    fun openList() {
        listTitle = queueTitle
        listPaged = false
        nameFocused = false
        pickerOpen = false
        listTick++
        overlayState = OverlayState.ChannelList
    }
    fun stepList(delta: Int) {
        if (lists.size < 2) return
        listDirection = delta
        // Paging is done on the list name: it takes focus, the channels below just follow.
        listPaged = true
        runCatching { listNameFocusRequester.requestFocus() }
        listTitle = lists[(listIndex + delta + lists.size) % lists.size].first
    }

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
    // The list stays open after choosing a channel and closes only when left alone for a while.
    LaunchedEffect(overlayState, listTick) {
        if (overlayState == OverlayState.ChannelList) {
            delay(ListIdleMillis)
            if (overlayState == OverlayState.ChannelList) overlayState = OverlayState.None
        }
    }
    LaunchedEffect(overlayState, listTick) {
        if (overlayState == OverlayState.Options) {
            delay(ListIdleMillis)
            if (overlayState == OverlayState.Options) overlayState = OverlayState.None
        }
    }
    val showErrorCard = failed && overlayState != OverlayState.ChannelList && overlayState != OverlayState.Options
    LaunchedEffect(overlayState == OverlayState.ChannelList, overlayState == OverlayState.Options, showErrorCard) {
        when {
            overlayState == OverlayState.ChannelList || overlayState == OverlayState.Options -> Unit
            showErrorCard -> retryFocusRequester.requestFocusSafely()
            else -> playerFocusRequester.requestFocusSafely()
        }
    }

    // Back from the channels goes to the list name, from the list name it closes the panel.
    fun backInList() {
        // The same press can arrive twice (as a key and as a system back); count it once.
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBack[0] < 300L) return
        lastBack[0] = now
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
    BackHandler {
        if (overlayState == OverlayState.ChannelList) backInList() else onBack()
    }

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
                            // Release of the press that opened the options panel: must not click a row in it.
                            okSwallow[0] -> { okSwallow[0] = false; true }
                            okArmed[0] -> { okArmed[0] = false; pickerOpen = true; true }
                            okShort[0] -> {
                                okShort[0] = false
                                // Short press: show the info bar; again while it is up toggles the favourite star.
                                if (overlayState == OverlayState.InfoBar) onFavorite(channel)
                                showInfoBar()
                                true
                            }
                            else -> false
                        }
                        else -> false
                    }
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val optionsOpen = overlayState == OverlayState.Options
                if (listOpen || optionsOpen) listTick++
                if (optionsOpen) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Back, Key.DirectionLeft, Key.DirectionRight -> { overlayState = OverlayState.None; true }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonSelect -> okSwallow[0]
                        else -> false
                    }
                }
                when (event.key) {
                    Key.ChannelUp -> { onZap(1); showInfoBar(); true }
                    Key.ChannelDown -> { onZap(-1); showInfoBar(); true }
                    // Handled here, before the system moves focus on its own for the Back key.
                    Key.Back -> { if (listOpen) backInList() else onBack(); true }
                    Key.DirectionUp -> if (listOpen) false else { onZap(1); showInfoBar(); true }
                    Key.DirectionDown -> if (listOpen) false else { onZap(-1); showInfoBar(); true }
                    // On the list name, left/right page between lists; on the channels they close the panel.
                    // "Open" is the arrow pointing at the panel's side of the screen.
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val towardsPanel = (event.key == Key.DirectionLeft) != rtl
                        val step = if ((event.key == Key.DirectionRight) != rtl) 1 else -1
                        when {
                            listOpen && pickerOpen -> true
                            listOpen && nameFocused -> { stepList(step); true }
                            listOpen -> { overlayState = OverlayState.None; true }
                            showErrorCard -> false
                            towardsPanel -> { openList(); true }
                            else -> false
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
                            okSwallow[0] = true
                            listTick++
                            overlayState = OverlayState.Options
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
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.Start) {
                    Text(t("CH"), color = PlayerMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
                Spacer(Modifier.width(14.dp))
                val starred = channel.id in favorites
                val starPop = remember { Animatable(1f) }
                LaunchedEffect(starred) {
                    if (starred) {
                        starPop.snapTo(1.35f)
                        starPop.animateTo(1f, spring(dampingRatio = .4f, stiffness = 380f))
                    }
                }
                StarIcon(starred, Modifier.size(26.dp).scale(starPop.value))
                Spacer(Modifier.width(14.dp))
                ChannelLogo(channel.name, channel.logo, Modifier.size(width = 64.dp, height = 44.dp), fontSize = 18.sp)
            }
        }

        AnimatedVisibility(
            visible = overlayState == OverlayState.Options,
            modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp).width(300.dp),
            enter = slideInHorizontally(initialOffsetX = { -side * it },
                animationSpec = spring(dampingRatio = .86f, stiffness = 300f)) + fadeIn(tween(220)),
            exit = slideOutHorizontally(targetOffsetX = { -side * it }, animationSpec = tween(220)) + fadeOut(tween(180))
        ) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocusSafely() }
            Column(
                Modifier.clip(RoundedCornerShape(28.dp)).background(PlayerGlass)
                    .border(1.dp, PlayerGlassBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Text(channelDisplayName(channel.name), color = PlayerWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(10.dp))
                val starred = channel.id in favorites
                OptionRow(if (starred) "Remove from favorites" else "Add to favorites", Modifier.focusRequester(first)) {
                    onFavorite(channel)
                    overlayState = OverlayState.None
                }
                OptionRow("Hide channel") { overlayState = OverlayState.None; onOption("hide") }
                OptionRow(if (locked) "Unlock channel" else "Lock channel") { overlayState = OverlayState.None; onOption("lock") }
                OptionRow("Rename") { overlayState = OverlayState.None; onOption("rename") }
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
                    .padding(top = 18.dp)
            ) {
                val many = lists.size > 1
                val pop = remember { Animatable(1f) }
                LaunchedEffect(listTitle) {
                    if (listPaged) {
                        pop.snapTo(1.14f)
                        pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = 380f))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).scale(pop.value).clip(RoundedCornerShape(18.dp))
                        .background(if (nameFocused) Color(0x29FFFFFF) else Color.Transparent)
                        .border(1.dp, if (nameFocused) Color(0x66FFFFFF) else Color.Transparent, RoundedCornerShape(18.dp))
                        .focusRequester(listNameFocusRequester)
                        .onFocusChanged { nameFocused = it.isFocused }
                        .focusable()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val arrow = if (!many || pickerOpen) 0f else if (nameFocused) 1f else .5f
                    Text(if (rtl) "›" else "‹", color = PlayerWhite.copy(alpha = arrow), fontSize = 24.sp)
                    AnimatedContent(
                        targetState = listTitle,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            (slideInHorizontally(tween(260)) { listDirection * it / 3 } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(200)) { -listDirection * it / 3 } + fadeOut(tween(160)))
                        },
                        label = "listTitle"
                    ) { title ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t(title), color = PlayerWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val size = lists.firstOrNull { it.first == title }?.second?.size ?: 0
                            Text(if (size == 1) "1 ${t("channel")}" else "$size ${t("channels")}", color = Color(0x99FFFFFF), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Text(if (rtl) "‹" else "›", color = PlayerWhite.copy(alpha = arrow), fontSize = 24.sp)
                }
                Spacer(Modifier.height(8.dp))
                AnimatedContent(
                    targetState = if (pickerOpen) ListPickerKey else listTitle,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (slideInHorizontally(tween(280)) { listDirection * it / 2 } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(220)) { -listDirection * it / 2 } + fadeOut(tween(160)))
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
    LaunchedEffect(Unit) { if (takeFocus) anchor.requestFocusSafely() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room on every side so the 1.04 focus scale and the last row are never clipped.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items, key = { it.key }) { item ->
            val isPlaying = item.key == playing.key
            var focused by remember { mutableStateOf(false) }
            val itemScale by animateFloatAsState(
                if (focused) 1.04f else 1f,
                spring(dampingRatio = .78f, stiffness = 420f),
                label = "channelItemScale"
            )
            Row(
                (if (item.key == anchorKey) Modifier.focusRequester(anchor) else Modifier)
                    .fillMaxWidth().scale(itemScale).clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            focused -> Color(0xF2F2F2F7)
                            isPlaying -> Color(0x1FFFFFFF)
                            else -> Color.Transparent
                        }
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (isPlaying) onClose() else onPick(item) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelLogo(item.name, item.logo, Modifier.size(40.dp), fontSize = 14.sp, corner = 9.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        channelDisplayName(item.name),
                        color = if (focused) Color(0xFF1C1C1E) else PlayerWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.group,
                        color = if (focused) Color(0x991C1C1E) else Color(0x99FFFFFF),
                        fontSize = 12.sp,
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
                            .background(if (focused) Color(0x1F000000) else Color(0x24FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(quality, color = if (focused) Color(0xCC1C1C1E) else Color(0xD9FFFFFF), fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Five-point star with soft corners: a thin outline when off, solid white with a glow when on. */
@Composable
private fun StarIcon(filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.minDimension / 2f * .92f
        val inner = outer * .46f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (r * kotlin.math.cos(angle)).toFloat()
            val y = cy + (r * kotlin.math.sin(angle)).toFloat() + outer * .06f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val round = Stroke(width = size.minDimension * .11f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        if (filled) {
            drawPath(path, Color.White.copy(alpha = .22f), style = Stroke(width = size.minDimension * .34f, join = StrokeJoin.Round))
            drawPath(path, Color.White)
            drawPath(path, Color.White, style = round)
        } else {
            drawPath(path, Color.White.copy(alpha = .55f), style = Stroke(width = size.minDimension * .075f, join = StrokeJoin.Round))
        }
    }
}

/** One action in the channel options panel. */
@Composable
private fun OptionRow(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = .78f, stiffness = 420f), label = "optionScale")
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp).scale(scale).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color(0xF2F2F2F7) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(t(label), color = if (focused) Color(0xFF1C1C1E) else PlayerWhite, fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private fun channelDisplayName(name: String): String =
    name.replace(Regex("\\s*\\((?:\\d{3,4}p|4K|HD|SD)\\)\\s*", RegexOption.IGNORE_CASE), " ").trim()

private fun channelQuality(name: String): String =
    Regex("\\b(\\d{3,4}p|4K|HD|SD)\\b", RegexOption.IGNORE_CASE).find(name)?.value?.uppercase().orEmpty()
