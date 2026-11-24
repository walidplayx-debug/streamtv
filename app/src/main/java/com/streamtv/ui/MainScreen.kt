package com.streamtv.ui

import android.content.ActivityNotFoundException
import android.net.Uri

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.streamtv.R
import com.streamtv.data.Channel
import com.streamtv.data.ChannelKind
import com.streamtv.data.kind
import com.streamtv.data.hasFavorite
import com.streamtv.data.detectedLanguage
import com.streamtv.data.EpgProgram
import com.streamtv.data.Episode
import com.streamtv.data.Season
import com.streamtv.data.Shows
import com.streamtv.data.TitleInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink get() = ActivePalette.current.ink
private val White get() = ActivePalette.current.text
private val Muted get() = ActivePalette.current.muted
private val Accent get() = ActivePalette.current.accent
private val Glass get() = ActivePalette.current.glass

/** Something destructive waiting for the viewer to say yes. */
private class ConfirmRequest(val question: String, val subject: String, val confirmLabel: String, val action: () -> Unit)

/** One focus colour for every control: the soft sky blue of the tab pills. */
private val FocusBrush get() = ActivePalette.current.focusBrush
private val PressBrush get() = ActivePalette.current.pressBrush
private val LibraryBackdrop get() = ActivePalette.current.backdropBrush
/** Dark glass: see-through black with a lit edge, brighter where the light falls (top left). */
private val GlassFill get() = ActivePalette.current.glassFillBrush
private val GlassEdge = Brush.linearGradient(listOf(Color(0x5CFFFFFF), Color(0x0FFFFFFF), Color(0x14FFFFFF), Color(0x38FFFFFF)))

/** A short "pressed" pulse so an OK press is felt even when nothing else changes on screen. */
@Composable
private fun rememberPress(onClick: () -> Unit): Pair<Boolean, () -> Unit> {
    var pulses by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pulses) {
        if (pulses > 0) {
            pressed = true
            delay(170)
            pressed = false
        }
    }
    return pressed to { pulses++; onClick() }
}

@Composable
fun MainScreen(state: TvState, model: MainViewModel) {
    // The channel to put focus back on when the player closes.
    var returnKey by remember { mutableStateOf<String?>(null) }
    // Looks for a newer version now and then, for the red mark on Settings.
    // Only while the app is on screen: nothing runs after Home.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                UpdateNotice.refresh()
                kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
            }
        }
    }
    // The Movies and Series pages keep their place (menu choice, open title) while a title plays.
    val movieUi = remember { LibraryUi() }
    val seriesUi = remember { LibraryUi() }
    // A series chosen elsewhere (search) opens on its own page under Series.
    LaunchedEffect(state.openShow) {
        state.openShow?.let { seriesUi.search = ""; seriesUi.details = it; model.showOpened() }
    }
    // Where the next movie starts: 0, or the place it was left when "Resume" was chosen.
    var vodStart by remember { mutableLongStateOf(0L) }

    // With no PIN set, hiding a channel or removing a playlist asks a plain yes/no question on the
    // back of the card instead; with a PIN, the card asks for the PIN and nothing else.
    var question by remember { mutableStateOf<CardQuestion?>(null) }
    var renaming by remember { mutableStateOf<Channel?>(null) }
    // "Search logo" from the channel card: a screen of its own, like renaming.
    var logoFor by remember { mutableStateOf<Channel?>(null) }
    logoFor?.let { target ->
        LogoSearchScreen(
            name = target.name, hasPicked = target.key in state.logos,
            onPick = { url -> model.setChannelLogo(target, url); logoFor = null },
            onRestore = { model.setChannelLogo(target, null); logoFor = null },
            onCancel = { logoFor = null }
        )
        return
    }
    renaming?.let { target ->
        SingleFieldScreen(
            title = "Rename channel", hint = "Channel name", initial = target.name, pin = false, error = null, saveLabel = "Save",
            onSave = { name -> model.renameChannel(target, name); renaming = null },
            onCancel = { renaming = null }
        )
        return
    }
    // Holding OK on a channel (a tile, or the video while it plays) opens the channel card.
    var optionsFor by remember { mutableStateOf<Channel?>(null) }
    // "Go to number": null when not asking; what was typed first otherwise (a remote digit, or nothing).
    var numberAsk by remember { mutableStateOf<String?>(null) }
    var numberError by remember { mutableStateOf<String?>(null) }
    // A digit pressed on the channel grid opens the number pad straight away.
    NumberJump.started?.let { first -> SideEffect { numberError = null; numberAsk = first; NumberJump.started = null } }
    val handleOption: (Channel, String) -> Unit = { target, option ->
        // Locking and hiding keep the card open: it turns over to ask for the PIN. A favourite keeps
        // it open too, so the star can be seen filling in. Everything else closes it.
        if (option != "lock" && option != "hide" && option != "favorite" && option != "goto") optionsFor = null
        when (option) {
            "logo" -> logoFor = target
            "goto" -> { numberError = null; numberAsk = "" }
            "favorite" -> model.toggleFavorite(target)
            "history" -> model.removeFromHistory(target)
            "hide" -> {
                if (state.pinSet) model.requestHide(target)
                else {
                    question = CardQuestion(target.name, "Hide this channel?", "Hide channel") {
                        model.hideChannel(target); question = null; optionsFor = null
                    }
                }
            }
            "lock" -> model.toggleLock(target)
            "rename" -> renaming = target
        }
        // These open a screen of their own; come back to the same tile afterwards.
        if (option != "favorite" && option != "history" && !state.playing) returnKey = target.key
    }
    // One card for the channel options and every PIN question: the options on its front, the PIN
    // pad on its back. A PIN asked for on its own (a locked channel, removing a playlist, changing
    // the PIN) opens the card already turned to its back, over the screen instead of a black page.
    val prompt = state.pin
    // Set when the PIN was accepted from the options: the whole card closes instead of turning back.
    var closeCard by remember { mutableStateOf(false) }
    if (closeCard && prompt == null) SideEffect { closeCard = false; optionsFor = null }
    val cardChannel = if (closeCard && prompt == null) null else optionsFor
    if (cardChannel != null || prompt != null || question != null || numberAsk != null) {
        FlipCardDialog(
            atEnd = state.playing,
            pin = prompt?.let { pinFace(it, state.pinError) },
            question = question,
            onPinSubmit = { code -> if (model.submitPin(code) && cardChannel != null) { closeCard = true } },
            onPinCancel = model::cancelPin,
            onQuestionCancel = { question = null },
            onDismiss = {
                when {
                    prompt != null -> { model.cancelPin() }
                    question != null -> { question = null }
                    else -> { optionsFor = null; numberAsk = null }
                }
            },
            // Left alone for a while, the card puts itself away, PIN question and all.
            onIdle = { if (prompt != null) model.cancelPin(); question = null; optionsFor = null; numberAsk = null },
            // The number is the channel's place in the list on screen; it plays at once.
            number = numberAsk?.let { first ->
                NumberAsk(first, numberError, onSubmit = { n ->
                    val list = NumberJump.list
                    if (n in 1..list.size) {
                        numberAsk = null; optionsFor = null
                        returnKey = list[n - 1].key
                        model.select(list[n - 1], list, NumberJump.title)
                    } else numberError = "No channel with this number"
                }, onCancel = { numberAsk = null })
            },
            options = cardChannel?.let { target ->
                CardOptions(
                    channel = target, starred = state.favorites.hasFavorite(target), locked = target.key in state.locked,
                    inHistory = target.key in state.recent
                ) { option -> handleOption(target, option) }
            }
        )
    }
    val openOptions: (Channel) -> Unit = { target -> optionsFor = target }

    if (state.playing && state.selected != null && state.selected.kind() != ChannelKind.Live) {
        // Movies and episodes get a real player: progress bar, seeking, pause, favourite; no zapping.
        val title = state.selected
        VodPlayerScreen(
            channel = title, favorite = state.favorites.hasFavorite(title), startAt = vodStart,
            onFavorite = { model.toggleFavorite(title) },
            onPosition = { position, duration -> model.savePosition(title, position, duration) },
            onExit = model::stopPlaying
        )
        // The start point is used once; anything opened later (from Search, say) starts from the top.
        LaunchedEffect(title.key) { vodStart = 0L }
        return
    }
    if (state.playing && state.selected != null) {
        SideEffect { returnKey = state.selected.key }
        // The player keeps one layout in every language: channel names are mostly Latin, and the
        // list sliding in from the other side in Arabic felt wrong.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        PlayerScreen(
            channel = state.selected,
            channels = state.queue.ifEmpty { state.channels },
            queueTitle = state.queueTitle,
            allChannels = state.channels,
            recent = state.recent,
            programs = state.programs,
            favorites = state.favorites,
            onBack = model::stopPlaying,
            onZap = model::zap,
            onSelect = { item, list, title -> model.select(item, list, title) },
            onOptions = { optionsFor = state.selected }
        )
        }
        return
    }

    // Text entry is a screen of its own: a floating TV keyboard would cover an inline form.
    var entry by remember { mutableStateOf("") }
    var settingsSection by remember { mutableStateOf("Playlist") }
    when (entry) {
        "playlist" -> {
            TextEntryScreen(
                title = "Add M3U playlist", urlHint = "Playlist address  https://…", initialUrl = "",
                exampleUrl = null, showName = true, saveLabel = "Save & load",
                onSave = { name, url ->
                    model.setPlaylistName(name)
                    model.setUrl(url)
                    model.savePlaylistEntry()
                    // A new playlist opens on Live TV, not on the Settings page it was added from.
                    model.loadPlaylist(openLive = true)
                    entry = ""
                },
                onCancel = { entry = "" }
            )
            return
        }
        "xtream" -> {
            XtreamEntryScreen(
                onSave = { name, url ->
                    model.setPlaylistName(name.ifBlank { "Xtream" })
                    model.setUrl(url)
                    model.savePlaylistEntry()
                    // A new playlist opens on Live TV, not on the Settings page it was added from.
                    model.loadPlaylist(openLive = true)
                    entry = ""
                },
                onCancel = { entry = "" }
            )
            return
        }
        "epg" -> {
            TextEntryScreen(
                title = "TV guide address (XMLTV)", urlHint = "https://…", initialUrl = state.epgUrl,
                exampleUrl = null, showName = false, saveLabel = "Save & load",
                onSave = { _, url ->
                    model.setEpgUrl(url)
                    model.loadEpg()
                    entry = ""
                },
                onCancel = { entry = "" }
            )
            return
        }
    }

    if (state.ready && state.playlistUrl.isBlank() && state.channels.isEmpty()) {
        WelcomeScreen(
            loading = state.loading, error = state.error,
            onAdd = { entry = "playlist" },
            onXtream = { entry = "xtream" },
            onImport = model::importPlaylist,
            onError = model::setError
        )
        return
    }

    // TV safe area: 48dp sides and 27dp top/bottom (Android TV's own margins) keep everything inside the overscan margin.
    // Every page sits on the softer, less black backdrop.
    Column(
        Modifier.fillMaxSize()
            .background(LibraryBackdrop)
            .padding(start = 48.dp, end = 48.dp, top = 27.dp, bottom = 27.dp)
    ) {
        // Back first returns to the tab bar; from the tab bar it leaves the app.
        val tabFocus = remember { FocusRequester() }
        var topBarFocused by remember { mutableStateOf(false) }
        BackHandler(enabled = !topBarFocused) {
            if (runCatching { tabFocus.requestFocus() }.isFailure) topBarFocused = true
        }
        TopNavigation(state.tab, { tab ->
            // Changing tabs closes an open title page.
            movieUi.details = null
            seriesUi.details = null
            model.selectTab(tab)
        }, tabFocus) { topBarFocused = it }
        // The pages start right under the tab bar; the tab already says where you are.
        Spacer(Modifier.height(if (state.tab == "Live TV" || state.tab == "Movies" || state.tab == "Series") 18.dp else 30.dp))
        val playTitle: (Channel, Long) -> Unit = { item, start -> vodStart = start; model.select(item) }
        when (state.tab) {
            // Movies and Series use the Live TV style of list for their menus.
            "Movies" -> LibraryContent(ChannelKind.Movie, state, model, movieUi, returnKey, { returnKey = null }, { returnKey = it }, playTitle, liveMenu = true)
            "Series" -> LibraryContent(ChannelKind.Series, state, model, seriesUi, returnKey, { returnKey = null }, { returnKey = it }, playTitle, liveMenu = true)
            "Favorites" -> {
                ScreenHeading("Favorites", "${state.channels.count { state.favorites.hasFavorite(it) }} ${t("saved channels")}")
                Spacer(Modifier.height(20.dp))
                ChannelGrid(state.channels.filter { state.favorites.hasFavorite(it) }, "Favorites", state.programs, state.favorites, model, returnKey, { returnKey = null }, openOptions)
            }
            "Search" -> SearchContent(state, model, returnKey, { returnKey = null }, openOptions)
            "Settings" -> SettingsContent(state, model, settingsSection, { settingsSection = it }, { request ->
                if (state.pinSet) {
                    model.protect(request.subject, "Please enter the PIN to remove this playlist", request.action)
                } else {
                    question = CardQuestion(request.subject, request.question, request.confirmLabel) { request.action(); question = null }
                }
            }) { entry = it }
            else -> LiveContent(state, model, returnKey, { returnKey = null }, openOptions)
        }
    }
}

@Composable
private fun TopNavigation(tab: String, onSelect: (String) -> Unit, tabFocus: FocusRequester, onFocus: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(124.dp), contentAlignment = Alignment.CenterStart) { Clock() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(Glass.copy(alpha = .72f)).padding(5.dp)
                    .onFocusChanged { onFocus(it.hasFocus) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Live TV", "Search", "Movies", "Series", "Settings").forEach { item ->
                    val selected = tab == item
                    FocusPill(item, selected, Modifier.weight(1f).fillMaxWidth().then(if (selected) Modifier.focusRequester(tabFocus) else Modifier)) { onSelect(item) }
                }
            }
            // A newer version is waiting: the mark sits on the end of the bar, beside Settings (the
            // last tab), centred on its height and half over its edge. Drawn outside the bar's clip
            // so it is never cut.
            if (UpdateNotice.available) {
                AlertBadge(Modifier.align(Alignment.CenterEnd).offset(x = 8.dp), size = 24.dp)
            }
        }
        Box(Modifier.width(124.dp), contentAlignment = Alignment.CenterEnd) {
            // Quiet in the corner: a little smaller and dimmed, so the eye goes to the content.
            Image(painterResource(ActivePalette.current.wordmark), contentDescription = "STV", modifier = Modifier.height(20.dp).graphicsLayer { alpha = .6f })
        }
    }
}

/** 24h clock with Western digits in left-to-right order, whatever the TV language is. */
@Composable
private fun Clock() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // Ticks only while the app is on screen.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000L - System.currentTimeMillis() % 60_000L + 100L)
            }
        }
    }
    val text = remember(now) { SimpleDateFormat("HH:mm", Locale.US).format(Date(now)) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(text, color = Muted, fontSize = 19.sp, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr))
    }
}

@Composable
private fun FocusPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) .95f else if (focused) 1.04f else 1f, spring(stiffness = 500f), label = "pillScale")
    Box(
        modifier.scale(scale).clip(CircleShape)
            .background(
                when {
                    pressed -> PressBrush
                    focused -> FocusBrush
                    selected -> Brush.verticalGradient(listOf(White.copy(alpha = .24f), White.copy(alpha = .14f)))
                    else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(
                width = if (focused) 1.dp else if (selected) 1.dp else 0.dp,
                color = if (focused) White.copy(alpha = .48f) else White.copy(alpha = .16f),
                shape = CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = press).padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        // A word too long for its tab is drawn smaller rather than cut off.
        val label = t(text)
        var fontSize by remember(label) { mutableStateOf(16f) }
        Text(
            label,
            color = White.copy(alpha = if (focused || selected) .98f else .72f),
            fontSize = fontSize.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
            onTextLayout = { layout -> if (layout.hasVisualOverflow && fontSize > 10f) fontSize -= 1f }
        )
    }
}

@Composable
private fun LiveContent(state: TvState, model: MainViewModel, restoreKey: String?, onRestored: () -> Unit, onOptions: (Channel) -> Unit) {
    val context = LocalContext.current
    val live = remember(state.channels) { Memo.of("live", state.channels) { state.channels.filter { it.kind() == ChannelKind.Live } } }
    // The side list's sections, divided the way Settings › Playlist says (provider, language,
    // country or genre).
    val mode = GroupMode.of(state.groupMode)
    val defaultSections = remember(live, mode, state.language) { Memo.of("sections", live, mode, state.language) { channelSections(live, mode, state.language) } }
    // The user's own order first (what they moved), then the rest in the default order; hidden ones left out.
    val savedOrder = state.sectionOrder[mode.key].orEmpty()
    val hidden = state.sectionHidden[mode.key].orEmpty()
    val arranged = remember(defaultSections, savedOrder, hidden) {
        val byToken = defaultSections.associateBy { it.token }
        val first = savedOrder.mapNotNull { byToken[it] }
        (first + defaultSections.filter { it.token !in savedOrder }).filter { it.token !in hidden }
    }
    // Moving an entry: the list being rearranged, and which entry is lifted.
    var draft by remember { mutableStateOf<List<ChannelSection>?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<ChannelSection?>(null) }
    // Where each entry is on screen, so its card can open right over it.
    val rowBounds = remember { HashMap<String, androidx.compose.ui.geometry.Rect>() }
    val liftFocus = remember { FocusRequester() }
    val sections = draft ?: arranged
    // "Last watched" is not in this list (it is in the player's side panel), to leave room for more lists.
    // Nothing chosen yet (app just opened): start on Favorites when there are some, else All channels.
    // A choice that no longer matches anything falls back to the default list instead of "no channels".
    val chosen = state.searchQuery
    val hasFavorites = live.any { state.favorites.hasFavorite(it) }
    val stillValid = when {
        chosen.isBlank() -> false
        chosen == "__favorites" -> hasFavorites
        chosen == "__all" -> true
        else -> sections.any { it.token.equals(chosen, true) }
    }
    val query = if (stillValid) chosen else if (hasFavorites) "__favorites" else "__all"
    val section = sections.firstOrNull { it.token.equals(query, true) }
    val filtered = when (query) {
        "__favorites" -> live.filter { state.favorites.hasFavorite(it) }
        "__all" -> live
        else -> section?.channels ?: live
    }
    val heading = when (query) {
        "__favorites" -> "Favorites"
        "__all" -> "All channels"
        else -> section?.title ?: "All channels"
    }
    Row(Modifier.fillMaxSize()) {
        val groupsState = rememberLazyListState()
        // While moving: keep the lifted entry focused and on screen as it travels.
        LaunchedEffect(moving, draft) {
            val token = moving ?: return@LaunchedEffect
            val top = (if (hasFavorites) 1 else 0) + 2
            val index = top + sections.indexOfFirst { it.token == token }
            val visible = groupsState.layoutInfo.visibleItemsInfo
            if (visible.isNotEmpty() && (index <= visible.first().index || index >= visible.last().index)) {
                groupsState.animateScrollToItem((index - 3).coerceAtLeast(0))
            }
            runCatching { liftFocus.requestFocus() }
        }
        // Coming back from a channel the list is rebuilt: bring the chosen entry back into view
        // instead of showing the top of the list.
        LaunchedEffect(Unit) {
            val top = if (hasFavorites) 1 else 0
            val index = when (query) {
                "__favorites" -> top - 1
                "__all" -> top
                else -> sections.indexOfFirst { it.token.equals(query, true) }.let { if (it < 0) 0 else top + 2 + it }
            }
            if (index > 2) groupsState.scrollToItem(index - 2)
        }
        Box(Modifier.width(250.dp).fillMaxHeight()) {
            LazyColumn(state = groupsState, modifier = Modifier.fillMaxSize().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (live.any { state.favorites.hasFavorite(it) }) {
                    item { FocusRow("Favorites", live.count { state.favorites.hasFavorite(it) }.toString(), query == "__favorites") { model.setSearchQuery("__favorites") } }
                }
                item { FocusRow("All channels", live.size.toString(), query == "__all") { model.setSearchQuery("__all") } }
                item { Text(t(mode.heading), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                items(sections, key = { it.token }) { item ->
                    val held = remember { booleanArrayOf(false) }
                    val lifted = moving == item.token
                    FocusRow(
                        t(item.title), item.channels.size.toString(), query.equals(item.token, true),
                        modifier = (if (lifted) Modifier.focusRequester(liftFocus) else Modifier)
                            .onGloballyPositioned { rowBounds[item.token] = it.boundsInWindow() }
                            .onPreviewKeyEvent { event ->
                            val ok = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter || event.key == Key.ButtonSelect
                            if (lifted) {
                                // Up and down move the lifted entry; OK or Back puts it down and saves the order.
                                val list = draft ?: return@onPreviewKeyEvent true
                                val at = list.indexOfFirst { it.token == item.token }
                                when {
                                    event.type != KeyEventType.KeyDown -> if (ok || event.key == Key.Back) {
                                        if (event.type == KeyEventType.KeyUp) {
                                            model.setSectionOrder(mode.key, list.map { it.token })
                                            moving = null; draft = null
                                        }
                                        true
                                    } else event.key == Key.DirectionUp || event.key == Key.DirectionDown
                                    event.key == Key.DirectionUp -> { if (at > 0) draft = list.toMutableList().apply { add(at - 1, removeAt(at)) }; true }
                                    event.key == Key.DirectionDown -> { if (at < list.size - 1) draft = list.toMutableList().apply { add(at + 1, removeAt(at)) }; true }
                                    ok || event.key == Key.Back -> true
                                    else -> true
                                }
                            } else when {
                                !ok -> false
                                // Held OK opens the entry's menu (move, move to top, hide).
                                event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> { held[0] = false; false }
                                event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                                    if (!held[0]) { held[0] = true; menuFor = item }
                                    true
                                }
                                event.type == KeyEventType.KeyUp && held[0] -> { held[0] = false; true }
                                else -> false
                            }
                        },
                        leading = item.flag?.let { code -> { CountryFlag(code) } },
                        onFocus = {
                            // Only worth suggesting for an entry a little way down: the first ones are already on top.
                            if (sections.indexOf(item) >= 3) when (mode) {
                                GroupMode.Country -> Hints.offer(context, "section_country", "Want this country first? Hold OK and move it up")
                                GroupMode.Language -> Hints.offer(context, "section_language", "Want this language first? Hold OK and move it up")
                                else -> Hints.offer(context, "section_list", "Prefer this section higher? Hold OK and arrange the list your way")
                            }
                        },
                        lifted = lifted
                    ) { if (!lifted) model.setSearchQuery(item.token) }
                }
            }
            // While moving, a line at the foot of the list says how.
            if (moving != null) {
                Text(
                    t("↑↓ to move · OK to drop"), Modifier.align(Alignment.BottomCenter).clip(CircleShape)
                        .background(ActivePalette.current.panel).padding(horizontal = 14.dp, vertical = 7.dp),
                    color = White.copy(alpha = .9f), fontSize = 13.sp
                )
            }
            // The same card as a channel's, with two rows: move the entry, or hide it.
            menuFor?.let { picked ->
                FlipCardDialog(
                    atEnd = false, pin = null, question = null,
                    onPinSubmit = {}, onPinCancel = {}, onQuestionCancel = {},
                    onDismiss = { menuFor = null }, onIdle = { menuFor = null }, options = null,
                    menu = CardMenu(t(picked.title), listOf(Triple("Move section", "move", "move"), Triple("Hide this section", "hide", "hide")), rowBounds[picked.token]) { id ->
                        menuFor = null
                        if (id == "move") { draft = arranged; moving = picked.token }
                        else model.hideSection(mode.key, picked.token)
                    }
                )
            }
            ScrollIndicator(groupsState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp))
        }
        Spacer(Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} ${t("channels")}", color = Muted, fontSize = 17.sp)
            }
            Spacer(Modifier.height(14.dp))
            ChannelGrid(filtered, heading, state.programs, state.favorites, model, restoreKey, onRestored, onOptions)
        }
    }
}

/** What the Movies (or Series) page remembers while a title plays: the menu choice, the search and the open title. */
private class LibraryUi {
    var section by mutableStateOf("__all")
    var search by mutableStateOf("")
    var details by mutableStateOf<Channel?>(null)
}

/**
 * Movies or Series: a menu that always stays on the side (search, watch history, favourites and the
 * genres) and the posters beside it. OK on a poster opens the title's page, not the player.
 */
@Composable
private fun LibraryContent(
    kind: ChannelKind, state: TvState, model: MainViewModel, ui: LibraryUi,
    restoreKey: String?, onRestored: () -> Unit, onReturn: (String) -> Unit, onPlay: (Channel, Long) -> Unit,
    /** The menu looks like the Live TV list (plain rows, no glass card). */
    liveMenu: Boolean = false
) {
    // Series: one title per show (its episodes are on its page), not one per episode.
    val titles = remember(state.channels, kind) {
        Memo.of("titles", state.channels, kind) {
            if (kind == ChannelKind.Series) Shows.of(state.channels) else state.channels.filter { it.kind() == kind }
        }
    }
    val name = if (kind == ChannelKind.Movie) "movies" else "series"
    if (titles.isEmpty()) {
        EmptyState("No $name yet", "Your playlist has no $name. Add one that includes them in Settings.", "Settings") {
            model.selectTab("Settings")
        }
        return
    }
    ui.details?.takeIf { kind == ChannelKind.Series && it.url.startsWith(com.streamtv.data.ShowScheme) }?.let { show ->
        SeriesDetails(
            show, state, model,
            onPlay = { episode, start -> onPlay(episode, start) },
            onFavorite = { model.toggleFavorite(show) },
            onClose = { ui.details = null; onReturn(show.key) }
        )
        return
    }
    ui.details?.let { item ->
        TitleDetails(
            loadInfo = { model.titleInfo(it) },
            item, kind, titles, state,
            onPlay = { start -> onPlay(item, start) },
            onFavorite = { model.toggleFavorite(item) },
            onOpen = { other -> ui.details = other },
            onClose = { ui.details = null; onReturn(item.key) }
        )
        return
    }
    val byKey = remember(titles) { titles.associateBy { it.key } }
    // History and favourites of this page only: channels keep theirs apart.
    val history = remember(byKey, state.recentVod) { state.recentVod.mapNotNull { byKey[it] } }
    val favorites = remember(titles, state.favorites) { titles.filter { state.favorites.hasFavorite(it) } }
    // Ten fixed genres (Action, Comedy, Drama...), the same for every provider. A provider whose
    // category names say nothing of genre keeps its own categories, as before.
    val genres: Map<String, List<Channel>> = remember(titles) {
        Memo.of<Map<String, List<Channel>>>("genres", titles) {
            val fixed = MovieGenres.group(titles)
            if (fixed.size >= 2) fixed else titles.groupBy { it.group.ifBlank { "Other" } }
        }
    }
    // The provider's own ratings: its 100 best rated titles, when it rates them.
    val topRated = remember(titles) {
        Memo.of("topRated", titles) { titles.filter { it.rating > 0f }.sortedByDescending { it.rating }.take(100) }
    }
    val query = ui.search.trim()
    val chosen = ui.section
    val section = when {
        chosen == "__top" -> if (topRated.isEmpty()) "__all" else chosen
        chosen == "__history" -> if (history.isEmpty()) "__all" else chosen
        chosen == "__favorites" -> if (favorites.isEmpty()) "__all" else chosen
        chosen == "__all" || genres.containsKey(chosen) -> chosen
        else -> "__all"
    }
    val shown = when {
        query.isNotEmpty() -> titles.filter { it.name.contains(query, true) || it.group.contains(query, true) }
        section == "__top" -> topRated
        section == "__history" -> history
        section == "__favorites" -> favorites
        section == "__all" -> titles
        else -> genres[section].orEmpty()
    }
    Row(Modifier.fillMaxSize()) {
        val menuState = rememberLazyListState()
        // Back from a title or the player, the chosen genre is brought back into view.
        LaunchedEffect(Unit) {
            val genreIndex = genres.keys.indexOf(section)
            if (query.isEmpty() && genreIndex >= 0) {
                val before = (if (liveMenu) 2 else 3) + (if (topRated.isNotEmpty()) 1 else 0) + (if (history.isNotEmpty()) 1 else 0) + (if (favorites.isNotEmpty()) 1 else 0) + 1
                menuState.scrollToItem((before + genreIndex - 2).coerceAtLeast(0))
            }
        }
        val searchHint = if (kind == ChannelKind.Movie) "Search movies" else "Search series"
        if (liveMenu) {
            // The same list as Live TV: plain rows, section headings, the scroll bar on its edge.
            Box(Modifier.width(214.dp).fillMaxHeight()) {
                LazyColumn(state = menuState, modifier = Modifier.fillMaxSize().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item { LibrarySearch(ui.search, searchHint, large = true) { ui.search = it } }
                    item { FocusRow("All", titles.size.toString(), query.isEmpty() && section == "__all") { ui.search = ""; ui.section = "__all" } }
                    if (topRated.isNotEmpty()) {
                        item { FocusRow("Top rated", topRated.size.toString(), query.isEmpty() && section == "__top") { ui.search = ""; ui.section = "__top" } }
                    }
                    if (history.isNotEmpty()) {
                        item { FocusRow("Watch history", history.size.toString(), query.isEmpty() && section == "__history") { ui.search = ""; ui.section = "__history" } }
                    }
                    if (favorites.isNotEmpty()) {
                        item { FocusRow("Favorites", favorites.size.toString(), query.isEmpty() && section == "__favorites") { ui.search = ""; ui.section = "__favorites" } }
                    }
                    item { Text(t("GENRES"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                    items(genres.toList()) { (genre, list) ->
                        FocusRow(genre, list.size.toString(), query.isEmpty() && section == genre) { ui.search = ""; ui.section = genre }
                    }
                }
                ScrollIndicator(menuState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp))
            }
            Spacer(Modifier.width(22.dp))
        } else {
        // A floating card of dark glass that never goes away.
        Box(
            Modifier.width(210.dp).fillMaxHeight()
                .padding(top = 4.dp, bottom = 6.dp)
                .shadow(22.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)).background(GlassFill)
                .border(1.dp, GlassEdge, RoundedCornerShape(24.dp))
        ) {
            LazyColumn(
                state = menuState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                item { LibrarySearch(ui.search, searchHint) { ui.search = it } }
                item { Spacer(Modifier.height(4.dp)) }
                item { MenuRow("All", titles.size.toString(), query.isEmpty() && section == "__all") { ui.search = ""; ui.section = "__all" } }
                if (history.isNotEmpty()) {
                    item { MenuRow("Watch history", history.size.toString(), query.isEmpty() && section == "__history") { ui.search = ""; ui.section = "__history" } }
                }
                if (favorites.isNotEmpty()) {
                    item { MenuRow("Favorites", favorites.size.toString(), query.isEmpty() && section == "__favorites") { ui.search = ""; ui.section = "__favorites" } }
                }
                item { Text(t("GENRES"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)) }
                items(genres.toList()) { (genre, list) ->
                    MenuRow(genre, list.size.toString(), query.isEmpty() && section == genre) { ui.search = ""; ui.section = genre }
                }
            }
            ScrollIndicator(menuState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 18.dp, bottom = 18.dp, end = 4.dp))
        }
        Spacer(Modifier.width(22.dp))
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (shown.isEmpty()) {
                Text(t(if (query.isNotEmpty()) "No results" else "Nothing here yet"), color = Muted, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                PosterGrid(shown, if (query.isNotEmpty()) "?$query" else section, state.favorites, state.positions, restoreKey, onRestored) { ui.details = it }
            }
        }
    }
}

/** Posters a row in the Movies and Series grids. */
private const val PosterColumns = 7

@Composable
private fun PosterGrid(
    titles: List<Channel>, listKey: String, favorites: Set<String>, positions: Map<String, Pair<Long, Long>>,
    restoreKey: String?, onRestored: () -> Unit, onOpen: (Channel) -> Unit
) {
    // Each list starts at its own top.
    key(listKey) {
        val restoreIndex = remember(titles, restoreKey) { if (restoreKey == null) -1 else titles.indexOfFirst { it.key == restoreKey } }
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = if (restoreIndex >= 0) (restoreIndex - 4).coerceAtLeast(0) else 0)
        val restoreFocus = remember { FocusRequester() }
        RestoreFocus(restoreKey, restoreIndex >= 0, restoreFocus, onRestored)
        var focusedKey by remember { mutableStateOf<String?>(null) }
        GlideToFocus(gridState, focusedKey)
        // CH+ / CH- (and Page Down / Up) turn a whole page: three rows (21 titles) on, same column,
        // so a library of a thousand titles is crossed in a few presses.
        var pageTarget by remember { mutableStateOf<String?>(null) }
        val pageFocus = remember { FocusRequester() }
        LaunchedEffect(pageTarget) {
            val key = pageTarget ?: return@LaunchedEffect
            val index = titles.indexOfFirst { it.key == key }
            if (index < 0) { pageTarget = null; return@LaunchedEffect }
            // The new row second from the top, as the grid glides to for the focus anyway.
            gridState.scrollToItem((index - index % PosterColumns - PosterColumns).coerceAtLeast(0))
            repeat(20) {
                if (runCatching { pageFocus.requestFocus() }.isSuccess) { pageTarget = null; return@LaunchedEffect }
                withFrameNanos { }
            }
            pageTarget = null
        }
        Column(Modifier.fillMaxSize()) {
            // The title under the remote, named once above the grid: the posters carry no names,
            // so twice as many fit on the screen.
            val current = remember(titles, focusedKey) { titles.firstOrNull { it.key == focusedKey } ?: titles.first() }
            Row(Modifier.fillMaxWidth().height(38.dp).padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(current.name, Modifier.weight(1f, fill = false), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(12.dp))
                val saved = positions[current.key]
                val detail = t(current.group.ifBlank { "Other" }) +
                    (saved?.let { "  ·  " + "${formatTime((it.second - it.first).coerceAtLeast(0L))} ${t("left")}" } ?: "")
                Text(detail, Modifier.weight(1f), color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${titles.size} ${t("titles")}", color = Muted, fontSize = 14.sp, maxLines = 1)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    // Seven posters a row: the page uses its width.
                    columns = GridCells.Fixed(PosterColumns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                        val down = event.key == Key.ChannelUp || event.key == Key.PageDown
                        val up = event.key == Key.ChannelDown || event.key == Key.PageUp
                        if (!down && !up) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown) {
                            val from = titles.indexOfFirst { it.key == focusedKey }.coerceAtLeast(0)
                            val page = PosterColumns * 3
                            val to = (from + if (down) page else -page).coerceIn(0, titles.lastIndex)
                            if (to != from) pageTarget = titles[to].key
                        }
                        true
                    },
                    // Free room under the last row, so every row can glide up to the focus height.
                    contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 16.dp, bottom = maxHeight),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(titles, key = { it.key }) { item ->
                        PosterCard(
                            item, favorites.hasFavorite(item),
                            when (item.key) {
                                pageTarget -> Modifier.focusRequester(pageFocus)
                                restoreKey -> Modifier.focusRequester(restoreFocus)
                                else -> Modifier
                            },
                            // No watched bar on the poster: the time left is named above the grid.
                            { focusedKey = item.key }, showTitle = false
                        ) { onOpen(item) }
                    }
                }
                ScrollIndicator(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp))
            }
        }
    }
}

/** The search pill at the top of the Movies / Series menu. OK opens the TV keyboard (voice search works too). */
@Composable
private fun LibrarySearch(text: String, hint: String, large: Boolean = false, onChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val bar = remember { FocusRequester() }
    val seen = remember { booleanArrayOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun finish() { editing = false; runCatching { bar.requestFocus() } }
    BackHandler(enabled = editing) { finish() }
    LaunchedEffect(editing) {
        if (editing) {
            repeat(20) {
                if (runCatching { field.requestFocus() }.isSuccess) return@LaunchedEffect
                withFrameNanos { }
            }
        } else {
            seen[0] = false
            keyboard?.hide()
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (large) 4.dp else 2.dp).height(if (large) 50.dp else 40.dp).clip(CircleShape)
            .background(if (focused || editing) FocusBrush else SolidColor(White.copy(alpha = .07f)))
            .border(1.dp, White.copy(alpha = if (focused || editing) .5f else .10f), CircleShape)
            .focusRequester(bar).onFocusChanged { focused = it.isFocused }
            .clickable { editing = true }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editing) {
            BasicTextField(
                value = text, onValueChange = onChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = if (large) 18.sp else 15.sp, fontFamily = StvFont),
                cursorBrush = SolidColor(White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { finish() }),
                modifier = Modifier.weight(1f).focusRequester(field)
                    .onFocusChanged { state ->
                        if (state.isFocused) seen[0] = true
                        else if (seen[0] && editing) { seen[0] = false; editing = false }
                    }
            )
        } else {
            Text(
                if (text.isEmpty()) t(hint) else text, Modifier.weight(1f),
                color = if (text.isEmpty()) White.copy(alpha = .55f) else White, fontSize = if (large) 18.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A title's own page: poster, name, genre, where it was left, and Play / Resume / favourite. */
@Composable
private fun TitleDetails(
    loadInfo: suspend (Channel) -> TitleInfo?,
    item: Channel, kind: ChannelKind, titles: List<Channel>, state: TvState,
    onPlay: (Long) -> Unit, onFavorite: () -> Unit, onOpen: (Channel) -> Unit, onClose: () -> Unit
) {
    BackHandler { onClose() }
    // The provider's genre and story, asked for now (an Xtream film); the page shows without them first.
    var info by remember(item.key) { mutableStateOf<TitleInfo?>(null) }
    LaunchedEffect(item.key) { info = loadInfo(item) }
    val saved = state.positions[item.key]
    val starred = state.favorites.hasFavorite(item)
    val primary = remember(item.key) { FocusRequester() }
    LaunchedEffect(item.key) {
        repeat(30) {
            if (runCatching { primary.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    // The film's other parts ("Rocky", "Rocky II"...), then titles of the same genre.
    val parts = remember(item.key, titles) {
        val saga = MovieGenres.saga(item.name)
        if (saga == null) emptyList() else titles.filter {
            it.key != item.key && it.name.startsWith(saga.take(4), ignoreCase = true) && MovieGenres.saga(it.name) == saga
        }.sortedBy { it.name.lowercase() }.take(24)
    }
    val more = remember(item.key, titles, parts) {
        val genre = MovieGenres.of(item)
        val skip = parts.mapTo(HashSet()) { it.key }
        titles.asSequence().filter {
            it.key != item.key && it.key !in skip && (if (genre != null) MovieGenres.of(it) == genre else it.group == item.group)
        }.take(24).toList()
    }
    // The page eases in when a title opens.
    val enter = remember(item.key) { Animatable(0f) }
    LaunchedEffect(item.key) { enter.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    // Which poster of the row below has focus: its heading says whether it is another part or a similar title.
    var rowIndex by remember(item.key) { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // The poster, faint and wide, behind the page.
        AsyncImage(
            model = item.logo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).graphicsLayer { alpha = .14f }
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
        // Everything fits on one screen: nothing scrolls away, so the buttons are always one press up.
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 48f }
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // The page stays still; only the row below slides sideways. Compact, so the row of
            // suggestions gets the room: the poster with three small round buttons under it, and
            // beside it the name, the pills, the story and the time left.
            var buttonLabel by remember(item.key) { mutableStateOf("") }
            Row {
                Column(Modifier.width(112.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(18.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, White.copy(alpha = .12f), RoundedCornerShape(12.dp))
                    ) {
                        ChannelLogo(
                            item.name, item.logo, Modifier.fillMaxSize(), fontSize = 22.sp, corner = 12.dp,
                            placeholder = Brush.linearGradient(channelGradient(item.name)), contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (saved != null) {
                            SmallRoundButton("▶\uFE0E", t("Resume"), Modifier.weight(1f).focusRequester(primary), { buttonLabel = it }) { onPlay(saved.first) }
                            SmallRoundButton("↺", t("Start over"), Modifier.weight(1f), { buttonLabel = it }) { onPlay(0L) }
                        } else {
                            SmallRoundButton("▶\uFE0E", t("Play"), Modifier.weight(1f).focusRequester(primary), { buttonLabel = it }) { onPlay(0L) }
                        }
                        SmallRoundButton(
                            if (starred) "★\uFE0E" else "☆\uFE0E", t(if (starred) "Remove from favorites" else "Add to favorites"),
                            Modifier.weight(1f), { buttonLabel = it }
                        ) { onFavorite() }
                    }
                    // What the focused button does, small under the buttons.
                    Text(buttonLabel, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(item.name, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    // Soft pills: what it is, its year and rating, and its genres (else where it belongs).
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (label in titlePills(item, info, t(if (kind == ChannelKind.Movie) "Movie" else "Series"))) {
                            Box(
                                Modifier.clip(CircleShape).background(White.copy(alpha = .08f)).border(1.dp, White.copy(alpha = .12f), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(label, color = White.copy(alpha = .9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                        Spacer(Modifier.height(8.dp))
                        Text(plot, color = White.copy(alpha = .75f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 640.dp),
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
                    }
                    if (saved != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val done = (saved.first.toFloat() / saved.second.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            Box(Modifier.width(180.dp).height(4.dp).clip(CircleShape).background(White.copy(alpha = .2f))) {
                                Box(Modifier.fillMaxWidth(done).fillMaxHeight().clip(CircleShape).background(White))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${formatTime((saved.second - saved.first).coerceAtLeast(0L))} ${t("left")}", color = Muted, fontSize = 14.sp)
                        }
                    }
                }
            }
            // One row under the page, as on Apple TV: the other parts first, then similar titles.
            // It slides sideways; the page above never moves. Up always goes back to Play / Resume.
            val row = remember(parts, more) { parts + more }
            if (row.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val heading = when {
                    rowIndex < parts.size -> t("Other parts")
                    MovieGenres.of(item) != null -> t("Similar")
                    else -> t("More in") + " " + t(item.group.ifBlank { "Other" })
                }
                Text(heading, color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Posters as tall as the room left allows (room for the focus growth included).
                    val posterHeight = (maxHeight - 28.dp).coerceIn(90.dp, 200.dp)
                    val rowState = rememberLazyListState()
                    var rowFocus by remember(item.key) { mutableStateOf<Int?>(null) }
                    GlideRow(rowState, rowFocus)
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                runCatching { primary.requestFocus() }
                                true
                            } else false
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(row, key = { _, entry -> entry.key }) { index, other ->
                            PosterCard(
                                other, state.favorites.hasFavorite(other), Modifier.width(posterHeight * (2f / 3f)),
                                onFocus = { rowIndex = index; rowFocus = index }
                            ) { onOpen(other) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A series' page, as on Apple TV: the poster and Play / Resume above, then the seasons and a row
 * of episodes that slides sideways. The episodes of an Xtream series are asked for only now.
 */
@Composable
private fun SeriesDetails(
    show: Channel, state: TvState, model: MainViewModel,
    onPlay: (Channel, Long) -> Unit, onFavorite: () -> Unit, onClose: () -> Unit
) {
    BackHandler { onClose() }
    var seasons by remember(show.key) { mutableStateOf<List<Season>?>(null) }
    var failed by remember(show.key) { mutableStateOf<String?>(null) }
    var attempt by remember(show.key) { mutableStateOf(0) }
    LaunchedEffect(show.key, attempt) {
        failed = null
        try {
            seasons = model.seasons(show)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = e.message ?: "Unable to load episodes"
        }
    }
    val all = seasons.orEmpty()
    // Known once the seasons came (the same answer from the server carries it).
    val showInfo = remember(show.key, seasons) { if (seasons == null) null else model.showInfo(show) }
    var seasonIndex by remember(show.key) { mutableStateOf(0) }
    val season = all.getOrNull(seasonIndex.coerceIn(0, (all.size - 1).coerceAtLeast(0)))
    val primary = remember(show.key) { FocusRequester() }
    val chosenSeason = remember(show.key) { FocusRequester() }
    LaunchedEffect(show.key) {
        repeat(30) {
            if (runCatching { primary.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    val starred = state.favorites.hasFavorite(show)
    // Go on where the viewer stopped: the last episode started and not finished, else the first.
    val byKey = remember(all) { all.flatMap { it.episodes }.associateBy { Shows.playable(show, it).key } }
    val next: Pair<Episode, Long>? = remember(byKey, state.recentVod, state.positions) {
        state.recentVod.firstNotNullOfOrNull { key ->
            val episode = byKey[key] ?: return@firstNotNullOfOrNull null
            val saved = state.positions[key] ?: return@firstNotNullOfOrNull null
            if (saved.first < saved.second * 0.95) episode to saved.first else null
        } ?: all.firstOrNull()?.episodes?.firstOrNull()?.let { it to 0L }
    }
    fun code(e: Episode) = "S${e.season} E${e.number}"
    val enter = remember(show.key) { Animatable(0f) }
    LaunchedEffect(show.key) { enter.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(
            model = show.logo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).graphicsLayer { alpha = .14f }
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 48f }
                // More room at the bottom: TVs crop the screen's edge, and the episode names sit there.
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp)
        ) {
            // Compact, as on the film page: the poster with small round buttons under it (the
            // focused one's name under them), and beside it the name, pills and story, so the
            // seasons and episodes get the room.
            var buttonLabel by remember(show.key) { mutableStateOf("") }
            Row {
                Column(Modifier.width(112.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(18.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, White.copy(alpha = .12f), RoundedCornerShape(12.dp))
                    ) {
                        ChannelLogo(
                            show.name, show.logo, Modifier.fillMaxSize(), fontSize = 22.sp, corner = 12.dp,
                            placeholder = Brush.linearGradient(channelGradient(show.name)), contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            next != null -> {
                                val (episode, start) = next
                                val label = (if (start > 0L) t("Resume") else t("Play")) + " " + code(episode)
                                SmallRoundButton("▶\uFE0E", label, Modifier.weight(1f).focusRequester(primary), { buttonLabel = it }) {
                                    onPlay(Shows.playable(show, episode), start)
                                }
                            }
                            failed != null -> SmallRoundButton("↻", t("Try again"), Modifier.weight(1f).focusRequester(primary), { buttonLabel = it }) { attempt++ }
                        }
                        SmallRoundButton(
                            if (starred) "★\uFE0E" else "☆\uFE0E", t(if (starred) "Remove from favorites" else "Add to favorites"),
                            Modifier.weight(1f).then(if (next == null && failed == null) Modifier.focusRequester(primary) else Modifier),
                            { buttonLabel = it }
                        ) { onFavorite() }
                    }
                    Text(buttonLabel, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(show.name, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val pills = titlePills(show, showInfo, t("Series")) +
                            listOfNotNull(all.size.takeIf { it > 0 }?.let { "$it ${t(if (it == 1) "season" else "seasons")}" })
                        for (label in pills) {
                            Box(
                                Modifier.clip(CircleShape).background(White.copy(alpha = .08f)).border(1.dp, White.copy(alpha = .12f), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(label, color = White.copy(alpha = .9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    showInfo?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                        Spacer(Modifier.height(8.dp))
                        Text(plot, color = White.copy(alpha = .75f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 640.dp),
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                failed != null -> Text(t(failed ?: ""), color = Color(0xFFFF8A80), fontSize = 16.sp, maxLines = 2)
                seasons == null -> Text(t("Loading episodes…"), color = Muted, fontSize = 16.sp)
                season == null -> Text(t("No episodes yet"), color = Muted, fontSize = 16.sp)
                else -> {
                    // The seasons: moving onto one shows its episodes at once.
                    if (all.size > 1) {
                        LazyRow(
                            modifier = Modifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                    runCatching { primary.requestFocus() }
                                    true
                                } else false
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(all, key = { _, s -> s.number }) { index, s ->
                                SeasonChip(
                                    "${t("Season")} ${s.number}", index == seasonIndex,
                                    if (index == seasonIndex) Modifier.focusRequester(chosenSeason) else Modifier
                                ) { seasonIndex = index }
                            }
                        }
                    } else {
                        Text("${t("Season")} ${season.number}", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        // Pictures as tall as the room left allows, over their one-line names.
                        val imageHeight = (maxHeight - 54.dp).coerceIn(60.dp, 200.dp)
                        key(seasonIndex) {
                            val rowState = rememberLazyListState()
                            var rowFocus by remember { mutableStateOf<Int?>(null) }
                            GlideRow(rowState, rowFocus)
                            LazyRow(
                                state = rowState,
                                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        runCatching { if (all.size > 1) chosenSeason.requestFocus() else primary.requestFocus() }
                                        true
                                    } else false
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(season.episodes, key = { _, e -> e.id }) { index, episode ->
                                    val playable = Shows.playable(show, episode)
                                    val saved = state.positions[playable.key]
                                    EpisodeCard(
                                        episode, show.logo,
                                        saved?.let { (it.first.toFloat() / it.second.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                                        Modifier.width(imageHeight * (16f / 9f)),
                                        onFocus = { rowFocus = index }
                                    ) {
                                        val start = saved?.takeIf { it.first < it.second * 0.95 }?.first ?: 0L
                                        onPlay(playable, start)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A small round button with a mark (play, start over, favourite) for the title page: white with a
 * dark mark under the remote, see-through otherwise. [onFocus] gets its name, shown under the row.
 */
@Composable
private fun SmallRoundButton(mark: String, label: String, modifier: Modifier, onFocus: (String) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) .92f else 1f, spring(stiffness = 430f), label = "smallButton")
    Box(
        modifier.height(34.dp).scale(scale).clip(CircleShape)
            .background(if (focused) White else White.copy(alpha = .14f))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(label) }
            .clickable(onClick = press),
        contentAlignment = Alignment.Center
    ) {
        Text(mark, color = if (focused) Ink else White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** The pills under a title's name: its kind, year, rating, and its genres (else its category). */
@Composable
private fun titlePills(item: Channel, info: TitleInfo?, kindLabel: String): List<String> {
    val year = info?.year?.takeIf { it > 0 } ?: item.year.takeIf { it > 0 }
    val rating = info?.rating?.takeIf { it > 0f } ?: item.rating.takeIf { it > 0f }
    val genres = info?.genres.orEmpty().ifEmpty { listOf(t(item.group.ifBlank { "Other" })) }.take(2)
    return listOfNotNull(kindLabel, year?.toString(), rating?.let { "★ " + String.format(java.util.Locale.US, "%.1f", it) }) + genres
}

@Composable
private fun SeasonChip(label: String, chosen: Boolean, modifier: Modifier = Modifier, onChoose: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.clip(CircleShape)
            .background(if (focused) FocusBrush else SolidColor(White.copy(alpha = if (chosen) .16f else .06f)))
            .border(1.dp, White.copy(alpha = if (focused) .5f else if (chosen) .3f else .1f), CircleShape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onChoose() }
            .clickable { onChoose() }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(label, color = White, fontSize = 16.sp, fontWeight = if (chosen || focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

/** An episode: its picture (16:9) with how much was watched, then its number, name and length. */
@Composable
private fun EpisodeCard(episode: Episode, fallback: String?, progress: Float?, modifier: Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = .7f, stiffness = 380f), label = "episodeScale")
    Column(
        modifier.zIndex(if (focused) 1f else 0f).scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .shadow(if (focused) 20.dp else 0.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(White.copy(alpha = .06f))
                // As on the posters: no white frame, the focused one stands out by size and shadow.
                .border(1.dp, White.copy(alpha = if (focused) .16f else .07f), RoundedCornerShape(10.dp))
        ) {
            AsyncImage(model = episode.image ?: fallback, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(White.copy(alpha = .25f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(White))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val title = episode.title.ifBlank { "${t("Episode")} ${episode.number}" }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The name keeps its own direction, so a Latin one reads "1 · Name", number first.
            Text("${episode.number} · $title", color = if (focused) White else White.copy(alpha = .9f), fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
            if (episode.seconds > 0) Text("  " + formatTime(episode.seconds * 1000L), color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Poster-shaped card with the title underneath, for movies and series. */
@Composable
private fun PosterCard(
    channel: Channel, favorite: Boolean, modifier: Modifier = Modifier, onFocus: () -> Unit = {},
    /** The name under the poster; the grid names the focused title above itself instead. */
    showTitle: Boolean = true,
    /** How much was watched (0 to 1): a thin bar along the bottom of the poster. */
    progress: Float? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(dampingRatio = .7f, stiffness = 380f), label = "posterScale")
    Column(
        modifier.zIndex(if (focused) 1f else 0f).scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .shadow(if (focused) 24.dp else 0.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                // No white frame: as on the channel tiles, the focused poster is picked out by its
                // size and its shadow.
                .border(1.dp, White.copy(alpha = if (focused) .16f else .07f), RoundedCornerShape(12.dp))
        ) {
            ChannelLogo(
                channel.name, channel.logo, Modifier.fillMaxSize(), fontSize = 26.sp, corner = 12.dp,
                placeholder = Brush.linearGradient(channelGradient(channel.name)), contentScale = ContentScale.Crop
            )
            if (favorite) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = White, fontSize = 15.sp)
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(White.copy(alpha = .25f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(White))
                }
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(7.dp))
            Text(channel.name, color = if (focused) White else White.copy(alpha = .78f), fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        }
    }
}

/** First screen of a fresh install: the wordmark and the three ways to add a playlist. */
@Composable
private fun WelcomeScreen(
    loading: Boolean, error: String?, onAdd: () -> Unit, onXtream: () -> Unit,
    onImport: (Uri) -> Unit, onError: (String) -> Unit
) {
    val context = LocalContext.current
    // The file is read by the view model, off the main thread: a large one froze the screen here.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ActivePalette.current.welcomeTop, Ink))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(ActivePalette.current.wordmark), contentDescription = "STV", modifier = Modifier.height(66.dp))
            Spacer(Modifier.height(26.dp))
            Text(t("Welcome"), color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(t("Choose how to add your playlist. You can change it any time in Settings."),
                color = Muted, fontSize = 17.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                WelcomeCard("M3U link", "Paste the address of an M3U or M3U8 playlist", Modifier.focusRequester(first)) { if (!loading) onAdd() }
                WelcomeCard("Xtream login", "Sign in with a server, username and password") { if (!loading) onXtream() }
                WelcomeCard("Import file", "Pick an M3U file stored on this TV") {
                    if (!loading) {
                        try {
                            filePicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "application/octet-stream", "*/*"))
                        } catch (_: ActivityNotFoundException) {
                            onError("No file picker is available on this TV. Use M3U link or Xtream login instead.")
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                when {
                    loading -> t("Loading…")
                    error != null -> t(error)
                    else -> " "
                },
                color = if (loading) Muted else Color(0xFFFF8B7B), fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 2
            )
        }
    }
}

@Composable
private fun WelcomeCard(title: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) .97f else if (focused) 1.06f else 1f, spring(dampingRatio = .75f, stiffness = 400f), label = "welcomeCardScale")
    Column(
        modifier.width(230.dp).height(128.dp).scale(scale).clip(RoundedCornerShape(22.dp))
            .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(White.copy(alpha = .07f)))
            .border(1.dp, White.copy(alpha = if (focused) .5f else .10f), RoundedCornerShape(22.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = press)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(t(title), color = White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(t(description), color = White.copy(alpha = if (focused) .9f else .6f), fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3)
    }
}

/**
 * Apple TV feel: the page glides under the focus instead of the focus walking down the screen.
 * The focused row is brought to one fixed height (the second row when three rows fit, else the
 * first), so only the first and last rows of a list move the focus itself.
 */
@Composable
private fun GlideToFocus(gridState: LazyGridState, focusedKey: String?) {
    LaunchedEffect(focusedKey) {
        val key = focusedKey ?: return@LaunchedEffect
        repeat(3) {
            val info = gridState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@LaunchedEffect
            val stride = item.size.height + info.mainAxisItemSpacing
            val pivot = if (info.viewportSize.height >= stride * 3) stride else 0
            val delta = (item.offset.y - pivot).toFloat()
            if (kotlin.math.abs(delta) < 1f) return@LaunchedEffect
            try {
                gridState.animateScrollBy(delta, tween(320, easing = FastOutSlowInEasing))
                return@LaunchedEffect
            } catch (e: CancellationException) {
                // The built-in "keep the focus visible" scroll took over; try again on the next frame.
                ensureActive()
                withFrameNanos { }
            }
        }
    }
}

/**
 * A sideways row as on Apple TV: the focused poster glides to the second place from the start
 * (the first one to the very start), so it is always whole, its growth and shadow included.
 * Left alone, the row only scrolled until the poster touched its edge, and cut it.
 */
@Composable
private fun GlideRow(state: LazyListState, focusedIndex: Int?) {
    LaunchedEffect(focusedIndex) {
        val index = focusedIndex ?: return@LaunchedEffect
        repeat(4) {
            try {
                state.animateScrollToItem((index - 1).coerceAtLeast(0))
                return@LaunchedEffect
            } catch (e: CancellationException) {
                // The built-in "keep the focus visible" scroll took over; try again on the next frame.
                ensureActive()
                withFrameNanos { }
            }
        }
    }
}

/** After the player closes, puts focus back on the channel that was opened. */
@Composable
private fun RestoreFocus(restoreKey: String?, found: Boolean, target: FocusRequester, onRestored: () -> Unit) {
    LaunchedEffect(restoreKey) {
        if (restoreKey == null) return@LaunchedEffect
        if (found) {
            repeat(30) {
                if (runCatching { target.requestFocus() }.isSuccess) { onRestored(); return@LaunchedEffect }
                withFrameNanos { }
            }
        }
        onRestored()
    }
}

@Composable
private fun ChannelGrid(
    channels: List<Channel>, title: String, programs: List<EpgProgram>, favorites: Set<String>, model: MainViewModel,
    restoreKey: String? = null, onRestored: () -> Unit = {}, onOptions: (Channel) -> Unit = {}
) {
    if (channels.isEmpty()) {
        EmptyState("No channels yet", "Add a playlist or choose another group.", "Settings") { model.selectTab("Settings") }
        return
    }
    // Every list opens at its top: the grid used to keep the place of the list shown before it,
    // so Favourites could open half-way down with its first rows out of sight.
    key(title) {
    val restoreIndex = remember(channels, restoreKey) { if (restoreKey == null) -1 else channels.indexOfFirst { it.key == restoreKey } }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = if (restoreIndex >= 0) (restoreIndex - 8).coerceAtLeast(0) else 0)
    val restoreFocus = remember { FocusRequester() }
    RestoreFocus(restoreKey, restoreIndex >= 0, restoreFocus, onRestored)
    var focusedKey by remember { mutableStateOf<String?>(null) }
    GlideToFocus(gridState, focusedKey)
    // Channels that share one logo (one channel in several languages or regions) are labelled with
    // what tells them apart; every other tile stays clean.
    val marks = remember(channels) { Memo.of("marks", channels) { distinguishingLabels(channels) } }
    val qualities = remember(channels) { Memo.of("qualities", channels) { qualityBadges(channels) } }
    // CH+ / CH− (or Page up / down) jump four rows at a time (16 channels with four across). A held
    // key repeats at a calm, fixed pace, so it speeds through the list without piling up work.
    val tileFocus = remember { HashMap<String, FocusRequester>() }
    SideEffect { NumberJump.list = channels; NumberJump.title = title }
    val pageScope = rememberCoroutineScope()
    val lastPage = remember { longArrayOf(0L) }
    fun page(direction: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPage[0] < 140L) return
        lastPage[0] = now
        val visible = gridState.layoutInfo.visibleItemsInfo
        val columns = visible.firstOrNull()?.let { first -> visible.count { it.row == first.row } }?.coerceAtLeast(1) ?: 4
        val from = channels.indexOfFirst { it.key == focusedKey }.coerceAtLeast(0)
        val to = (from + direction * columns * 4).coerceIn(0, channels.lastIndex)
        if (to == from) return
        pageScope.launch {
            gridState.scrollToItem((to - to % columns - columns).coerceAtLeast(0))
            withFrameNanos { }
            runCatching { tileFocus[channels[to].key]?.requestFocus() }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.ChannelDown, Key.PageDown -> 1
            Key.ChannelUp, Key.PageUp -> -1
            else -> 0
        }
        val code = event.nativeKeyEvent.keyCode
        val digit = when (code) {
            in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> '0' + (code - android.view.KeyEvent.KEYCODE_0)
            in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (code - android.view.KeyEvent.KEYCODE_NUMPAD_0)
            else -> null
        }
        when {
            // The remote's digits start "Go to number" with that digit.
            digit != null -> { if (event.type == KeyEventType.KeyDown) NumberJump.started = digit.toString(); true }
            direction == 0 -> false
            else -> { if (event.type == KeyEventType.KeyDown) page(direction); true }
        }
    }) {
        LazyVerticalGrid(
            // One tile size everywhere (Live TV, Favourites, Search): four across beside the Live TV
            // lists; a wider grid fits more tiles of the same size instead of stretching them.
            columns = GridCells.FixedSize(112.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            // Room around the grid so the enlarged, focused tile is never cut off.
            // The free room under the last row lets every row glide up to the focus height.
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = maxHeight, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            gridItems(channels, key = { it.key }) { channel ->
                ChannelCard(channel, favorites.hasFavorite(channel), marks[channel.key], qualities[channel.key], channels.size > 100, { onOptions(channel) }, Modifier.fillMaxWidth().height(72.dp).focusRequester(tileFocus.getOrPut(channel.key) { FocusRequester() }).then(if (channel.key == restoreKey) Modifier.focusRequester(restoreFocus) else Modifier), { focusedKey = channel.key }) { model.select(channel, channels, title) }
            }
        }
        // A thin, faint bar on the far side of the tiles (the left in Arabic) shows where in the list you are.
        ScrollIndicator(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp))
    }
    }
}

@Composable
private fun ChannelCard(channel: Channel, favorite: Boolean, mark: String?, quality: String?, bigList: Boolean, onLongPress: () -> Unit, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Holding OK: the first repeat of the key opens the options, and its release must not also open the channel.
    val held = remember { booleanArrayOf(false) }
    val (pressed, press) = rememberPress(onClick)
    // A dark or see-through logo gets a soft silver tile instead of the dark one, same shape and size.
    var light by remember(channel.logo) { mutableStateOf(channel.logo?.let { LogoTone.known[it] } == true) }
    val scale by animateFloatAsState(if (pressed) 1.07f else if (focused) 1.17f else 1f, spring(dampingRatio = .7f, stiffness = 380f), label = "cardScale")
    Box(
        // The focused tile grows and sits above its neighbours.
        modifier.zIndex(if (focused) 1f else 0f).scale(scale)
            .shadow(if (focused) 24.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(when {
                light && focused -> ActivePalette.current.lightTileFocused
                light -> ActivePalette.current.lightTile
                focused -> ActivePalette.current.tileFocused
                else -> ActivePalette.current.tile
            }))
            // No white frame: the tile is picked out by its size, its lighter face and its shadow.
            .border(1.dp, White.copy(alpha = if (light) (if (focused) .30f else .18f) else if (focused) .16f else .07f), RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocus()
                    // In a long list, first the tip on getting around it quickly.
                    if (!(bigList && Hints.offer(context, "biglist", "So many channels? CH+ / CH− jump 16 at a time, or type the channel number")))
                        Hints.offer(context, "channel_options", "Hold OK to add the channel to favourites, lock, hide or rename it")
                }
            }
            .onPreviewKeyEvent { event ->
                val ok = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter || event.key == Key.ButtonSelect
                when {
                    !ok -> false
                    // The card opens in its own window and takes the release with it, so start clean on every new press.
                    event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> { held[0] = false; false }
                    event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                        if (!held[0]) { held[0] = true; onLongPress() }
                        true
                    }
                    event.type == KeyEventType.KeyUp && held[0] -> { held[0] = false; true }
                    else -> false
                }
            }
            .clickable(onClick = press),
        contentAlignment = Alignment.Center
    ) {
        ChannelLogo(
            // Without a logo, a labelled tile's face shows the name its siblings share (the label
            // under it says which one): "ITV1" over "North West", not the same category on every tile.
            if (mark != null) sharedName(channel.name, mark) else channel.name, channel.logo,
            // With a label, the logo is smaller and stands clear of the words under it.
            Modifier.fillMaxSize().padding(
                start = if (mark != null) 12.dp else 8.dp, end = if (mark != null) 12.dp else 8.dp,
                top = if (mark != null) 6.dp else 8.dp, bottom = if (mark != null) 25.dp else 8.dp
            ),
            fontSize = 25.sp, corner = 8.dp,
            placeholder = Brush.linearGradient(channelGradient(channel.name)), logoPadding = 2.dp,
            group = channel.group, compact = mark != null, onNeedsLight = { light = it }
        )
        // The same channel in another quality: a small badge in the star's corner. When the channel
        // is a favourite the badge glides down to make room for the star above it.
        if (quality != null) {
            val badgeTop by androidx.compose.animation.core.animateDpAsState(
                if (favorite) 27.dp else 8.dp, spring(dampingRatio = .8f, stiffness = 380f), label = "badge"
            )
            QualityBadge(quality, Modifier.align(Alignment.TopEnd).padding(top = badgeTop, end = 7.dp))
        }
        if (mark != null) TileLabel(mark, light, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 4.dp).height(18.dp))
        if (favorite) FavoriteStar(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 7.dp).size(17.dp))
    }
}

/** A favourite's mark: a star engraved into the tile itself, nothing laid over it. */
@Composable
private fun FavoriteStar(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f + size.height * .04f
        val outer = size.width / 2f
        fun star(dx: Float, dy: Float) = Path().apply {
            for (i in 0 until 10) {
                val rr = if (i % 2 == 0) outer else outer * .45f
                val a = Math.toRadians(-90.0 + i * 36.0)
                val x = cx + dx + (rr * kotlin.math.cos(a)).toFloat()
                val y = cy + dy + (rr * kotlin.math.sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val lip = 1.dp.toPx()
        drawPath(star(lip, lip), Color.White.copy(alpha = .22f))   // the lower lip catches the light
        drawPath(star(-lip, -lip), Color.Black.copy(alpha = .35f)) // the upper lip is in shade
        drawPath(star(0f, 0f), Color.White.copy(alpha = .28f))     // the cut itself, a shade of the tile
    }
}

private fun channelGradient(name: String): List<Color> {
    val faces = ActivePalette.current.faces
    return faces[name.hashCode().ushr(1) % faces.size]
}

@Composable
private fun SearchContent(state: TvState, model: MainViewModel, restoreKey: String?, onRestored: () -> Unit, onOptions: (Channel) -> Unit) {
    // The TV's own keyboard floats wherever it likes and covers the results, so Search has a
    // keyboard of its own: it stays on one side and the results fill the other.
    val language = LocalAppLanguage.current
    var arabicKeys by remember { mutableStateOf(language == "ar") }
    var scope by remember { mutableStateOf("All") }
    val fieldSeen = remember { booleanArrayOf(false) }
    val text = state.searchText
    val q = text.trim()
    val pool = remember(state.channels, scope) {
        when (scope) {
            "Live TV" -> state.channels.filter { it.kind() == ChannelKind.Live }
            "Movies" -> state.channels.filter { it.kind() == ChannelKind.Movie }
            "Series" -> state.channels.filter { it.kind() == ChannelKind.Series }
            else -> state.channels
        }
    }
    val matches = remember(pool, q) {
        if (q.isEmpty()) emptyList() else pool.filter { it.name.contains(q, true) || it.group.contains(q, true) || it.country.contains(q, true) }
    }
    // The search bar is a real text field: OK on it opens the TV keyboard, so voice search and
    // typing or pasting from a phone remote app keep working. The keys below are for the D-pad.
    var editing by remember { mutableStateOf(false) }
    var barFocused by remember { mutableStateOf(false) }
    val barFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val backToBar = remember { booleanArrayOf(false) }
    BackHandler(enabled = editing) { backToBar[0] = true; editing = false }
    LaunchedEffect(editing) {
        if (editing) {
            repeat(20) {
                if (runCatching { fieldFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                withFrameNanos { }
            }
        } else {
            fieldSeen[0] = false
            keyboard?.hide()
            // Finished with Back or the keyboard's search key: focus returns to the search bar.
            if (backToBar[0]) { backToBar[0] = false; runCatching { barFocus.requestFocus() } }
        }
    }
    // Both halves ease in instead of snapping onto the screen.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(360)) }
    Row(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.width(300.dp).graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 24f }) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(CircleShape)
                        .background(if (barFocused || editing) FocusBrush else SolidColor(Glass.copy(alpha = .72f)))
                        .border(1.dp, White.copy(alpha = if (barFocused || editing) .5f else .14f), CircleShape)
                        .focusRequester(barFocus)
                        .onFocusChanged { barFocused = it.isFocused }
                        .clickable { editing = true }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (editing) {
                        BasicTextField(
                            value = text, onValueChange = model::setSearchText, singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 18.sp, fontFamily = StvFont),
                            cursorBrush = SolidColor(White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { backToBar[0] = true; editing = false }),
                            modifier = Modifier.weight(1f).focusRequester(fieldFocus)
                                // Leaving the field with the D-pad ends typing; the keys below take over.
                                .onFocusChanged { field ->
                                    if (field.isFocused) fieldSeen[0] = true
                                    else if (fieldSeen[0] && editing) { fieldSeen[0] = false; editing = false }
                                }
                        )
                    } else {
                        Text(
                            if (text.isEmpty()) t("Search channels") else text, Modifier.weight(1f),
                            color = if (text.isEmpty()) White.copy(alpha = .6f) else White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // The TV keyboard and these keys never show together: while the TV keyboard is up
                // (typing in the search bar), the in-app keys step aside, and come back after.
                AnimatedVisibility(visible = !editing, enter = fadeIn(tween(220)), exit = fadeOut(tween(140))) {
                    // One soft panel holding plain letters; only the key in focus is filled.
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass.copy(alpha = .72f))
                            .border(1.dp, White.copy(alpha = .08f), RoundedCornerShape(22.dp)).padding(8.dp)
                    ) {
                        val keys = if (arabicKeys) "ابتثجحخدذرزسشصضطظعغفقكلمنهويءةىأإؤئآ" else "abcdefghijklmnopqrstuvwxyz0123456789"
                        for (row in keys.chunked(6)) {
                            Row(Modifier.fillMaxWidth()) {
                                for (key in row) {
                                    SearchKey(key.toString(), Modifier.weight(1f)) { model.setSearchText(text + key) }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SearchKey("space", Modifier.weight(2f), outlined = true) { if (text.isNotEmpty()) model.setSearchText("$text ") }
                            SearchKey("⌫", Modifier.weight(1f), outlined = true) { model.setSearchText(text.dropLast(1)) }
                            SearchKey("✕", Modifier.weight(1f), outlined = true) { model.setSearchText("") }
                            SearchKey(if (arabicKeys) "ABC" else "عربي", Modifier.weight(2f), outlined = true) { arabicKeys = !arabicKeys }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(1f).fillMaxHeight().graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 24f }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(CircleShape).background(Glass.copy(alpha = .72f)).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (item in listOf("All", "Live TV", "Movies", "Series")) {
                        FocusPill(item, scope == item) { scope = item }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(if (q.isEmpty()) t("Start typing to search") else "${matches.size} ${t("results")}", color = Muted, fontSize = 16.sp, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            val posterState = rememberLazyGridState()
            var posterFocus by remember { mutableStateOf<String?>(null) }
            GlideToFocus(posterState, posterFocus)
            when {
                matches.isEmpty() -> Unit
                scope == "Movies" || scope == "Series" -> BoxWithConstraints(Modifier.fillMaxSize()) { LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    state = posterState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, top = 14.dp, end = 12.dp, bottom = maxHeight),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    gridItems(matches, key = { it.key }) { item ->
                        PosterCard(item, state.favorites.hasFavorite(item), onFocus = { posterFocus = item.key }) { model.select(item, matches, "Search results") }
                    }
                }
                    ScrollIndicator(posterState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 14.dp))
                }
                else -> ChannelGrid(matches, "Search results", state.programs, state.favorites, model, restoreKey, onRestored, onOptions)
            }
        }
    }
}

/** One key of the in-app search keyboard: a plain letter that fills with the focus colour. */
@Composable
private fun SearchKey(label: String, modifier: Modifier = Modifier, outlined: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) .92f else if (focused) 1.12f else 1f, spring(dampingRatio = .7f, stiffness = 480f), label = "searchKeyScale")
    Box(
        modifier.height(if (outlined) 40.dp else 41.dp).zIndex(if (focused) 1f else 0f).scale(scale).clip(CircleShape)
            .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(Color.Transparent))
            .border(1.dp, White.copy(alpha = if (focused) .5f else if (outlined) .16f else 0f), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = press),
        contentAlignment = Alignment.Center
    ) {
        Text(t(label), color = White.copy(alpha = if (focused) 1f else .8f), fontSize = if (outlined) 15.sp else 19.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun SettingsContent(
    state: TvState, model: MainViewModel, section: String, onSection: (String) -> Unit,
    onConfirm: (ConfirmRequest) -> Unit, onEntry: (String) -> Unit
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Read by the view model, off the main thread: a large file froze the screen here.
        if (uri != null) model.importPlaylist(uri)
    }
    Row(Modifier.fillMaxSize()) {
        // The sections as a list like Live TV's. Moving onto a section opens it, no press needed;
        // coming back into the list (from the page or the tabs) lands on the open section, so
        // passing over the list never opens another one by accident.
        val sections = listOf("Playlist", "TV guide", "Language", "Parental control", "About")
        // One focus target per row that never changes, so opening a section does not rebuild its row.
        val rows = remember { sections.associateWith { FocusRequester() } }
        // The list scrolls instead of squeezing: on a short screen the last row ("About") was
        // pressed into a sliver and its name was cut down to a few marks.
        Column(
            Modifier.width(250.dp).fillMaxHeight().padding(end = 8.dp)
                .focusProperties { enter = { rows[section] ?: FocusRequester.Default } }.focusGroup()
                .verticalScroll(rememberScrollState())
        ) {
            sections.forEach { item ->
                FocusRow(
                    item, "", item == section,
                    modifier = Modifier.focusRequester(rows.getValue(item)),
                    onFocus = { if (item != section) onSection(item) },
                    alert = item == "About" && UpdateNotice.available
                ) { onSection(item) }
            }
        }
        Spacer(Modifier.width(30.dp))
        val pageScroll = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxHeight()) {
        ScrollIndicator(pageScroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp))
        Column(Modifier.fillMaxSize().padding(end = 14.dp).verticalScroll(pageScroll)) {
            // No page heading: the lit section box already says where you are. The first row lines up
            // with the first section box.
            Spacer(Modifier.height(10.dp))
            when (section) {
                "Playlist" -> {
                    val counts = remember(state.channels) { state.channels.groupingBy { it.kind() }.eachCount() }
                    val activeName = state.playlists.firstOrNull { it.url == state.playlistUrl }?.name
                    Text(t("Add a playlist"), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(CircleShape)
                            .background(Glass.copy(alpha = .72f)).padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FocusPill("M3U", false, Modifier.weight(1f)) { if (!state.loading) onEntry("playlist") }
                        FocusPill("Xtream", false, Modifier.weight(1f)) { if (!state.loading) onEntry("xtream") }
                        FocusPill("File", false, Modifier.weight(1f)) {
                            try {
                                filePicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "application/octet-stream", "*/*"))
                            } catch (_: ActivityNotFoundException) {
                                model.setError("No file picker is available on this TV. Use M3U link or Xtream login instead.")
                            }
                        }
                        if (state.playlistUrl.startsWith("http")) {
                            FocusPill("Reload", false, Modifier.weight(1f)) { if (!state.loading) model.loadPlaylist() }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Slim summary of the active playlist, the same width as the bar above it.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(White.copy(alpha = .07f))
                            .border(1.dp, White.copy(alpha = .10f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("NOW PLAYING FROM"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(12.dp))
                        Text(activeName ?: t("Playlist"), color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.width(12.dp))
                        Text(t(playlistLabel(state.playlistUrl)), Modifier.weight(1f), color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (state.loading) t("Loading…")
                            else "${counts[ChannelKind.Live] ?: 0} ${t("live")}  ·  ${counts[ChannelKind.Movie] ?: 0} ${t("movies")}  ·  ${counts[ChannelKind.Series] ?: 0} ${t("series")}",
                            color = White.copy(alpha = .86f), fontSize = 14.sp, maxLines = 1
                        )
                    }
                    state.error?.let { Text(it, color = Color(0xFFFF8B7B), fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp)) }
                    if (state.playlists.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        Text(t("Your playlists"), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        for (item in state.playlists) {
                            PlaylistRow(item, item.url == state.playlistUrl, { model.selectPlaylist(item) }) {
                                onConfirm(ConfirmRequest("Remove this playlist?", item.name, "Remove") { model.removePlaylist(item) })
                            }
                        }
                    }
                    // How the Live TV side list divides the channels.
                    Spacer(Modifier.height(22.dp))
                    Text(t("Group channels by"), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    for (pair in GroupMode.entries.chunked(2)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (choice in pair) {
                                ChoiceBox(t(choice.label), state.groupMode == choice.key, Modifier.weight(1f)) { model.setGroupMode(choice.key) }
                            }
                        }
                    }
                    // Only when something was moved or hidden.
                    if (state.sectionOrder.values.any { it.isNotEmpty() } || state.sectionHidden.values.any { it.isNotEmpty() }) {
                        Spacer(Modifier.height(10.dp))
                        WhiteButton("Reset list order") { model.resetSections() }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                "TV guide" -> {
                    SettingsRow("XMLTV guide URL", state.epgUrl.ifBlank { "Optional programme guide · not set" })
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WhiteButton("Set guide address") { onEntry("epg") }
                        if (state.epgUrl.isNotBlank()) WhiteButton("Reload TV guide") { model.loadEpg() }
                        // Off until an address is set again; the playlist's own guide does not come back by itself.
                        if (state.epgUrl.isNotBlank()) WhiteButton("Remove TV guide") { model.removeEpg() }
                    }
                    state.error?.let { Text(it, color = Color(0xFFFF8B7B), fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp)) }
                    Text("${state.programs.size} ${t("programmes loaded")}", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp))
                }
                "Language" -> {
                    // Two even columns of boxes: all ten languages fit without scrolling.
                    for (pair in AppLanguages.chunked(2)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for ((code, name) in pair) {
                                ChoiceBox(name, state.language == code, Modifier.weight(1f)) { model.setLanguage(code) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                // Compact rows, each one reachable with the remote; the action sits on its own row.
                "Parental control" -> {
                    SettingItem("PIN", if (state.pinSet) "Set" else "Not set", if (state.pinSet) "Change PIN" else "Set PIN") { model.changePin() }
                    SettingItem("Locked channels", state.locked.size.toString())
                    if (state.hidden.isNotEmpty()) {
                        SettingItem("Hidden channels", state.hidden.size.toString(), "Restore hidden channels") { model.restoreHidden() }
                    } else {
                        SettingItem("Hidden channels", state.hidden.size.toString())
                    }
                    Spacer(Modifier.height(24.dp))
                }
                "About" -> AppUpdateSection()
                else -> SettingsRow("streamtv", "Version 1.0.0")
            }
        }
        }
    }
}

@Composable
private fun PlaylistRow(entry: PlaylistEntry, active: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var removeFocused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onOpen)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(White.copy(alpha = if (active) .12f else .05f)))
                .onFocusChanged { focused = it.isFocused }.clickable(onClick = press)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t(playlistLabel(entry.url)), color = if (focused) White.copy(alpha = .8f) else Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (active) Text(t("Active"), color = if (focused) White else Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.clip(RoundedCornerShape(15.dp)).background(if (removeFocused) Color(0xFFFF6B5E) else White.copy(alpha = .05f))
                .onFocusChanged { removeFocused = it.isFocused }.clickable(onClick = onRemove)
                .padding(horizontal = 16.dp, vertical = 19.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(t("Remove"), color = if (removeFocused) White else Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A box to pick one of several (a language): a check mark on the chosen one. */
@Composable
private fun ChoiceBox(label: String, chosen: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(White.copy(alpha = if (chosen) .12f else .05f)))
            .border(1.dp, White.copy(alpha = if (focused) .4f else if (chosen) .2f else .07f), RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = press)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = if (focused || chosen) White else White.copy(alpha = .7f), fontSize = 17.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        if (chosen) Text("✓", color = if (focused) White else Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * One compact settings row: the name and its value on the start side, the action (if any) on the
 * end side. Every row takes focus, so the remote can always reach and scroll to the last one.
 */
@Composable
private fun SettingItem(title: String, value: String, action: String? = null, onClick: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress { onClick?.invoke() }
    // No growing on focus here: rows that change size while the page scrolls made it stutter.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(White.copy(alpha = .06f)))
            .border(1.dp, White.copy(alpha = if (focused) .4f else .08f), RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(if (onClick != null) Modifier.clickable(onClick = press) else Modifier.focusable())
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(t(title), color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(t(value), color = if (focused) White.copy(alpha = .8f) else Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Text(t(action), color = if (focused) White else Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("  ›", color = if (focused) White else Muted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String) = SettingItem(title, subtitle)

@Composable
private fun FocusRow(
    title: String, trailing: String, selected: Boolean, modifier: Modifier = Modifier,
    /** Called when the row takes focus (Settings opens a section without a press). */
    onFocus: (() -> Unit)? = null,
    /** Shows the red "!" mark in place of the trailing text. */
    alert: Boolean = false,
    /** Something shown before the title (a country's flag). */
    leading: (@Composable () -> Unit)? = null,
    /** Picked up to be moved: larger, with a light frame. */
    lifted: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (lifted) 1.06f else if (pressed) .97f else if (focused) 1.025f else 1f, spring(stiffness = 430f), label = "rowScale")
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp).scale(scale).clip(RoundedCornerShape(15.dp))
        .border(if (lifted) 2.dp else 0.dp, if (lifted) White.copy(alpha = .85f) else Color.Transparent, RoundedCornerShape(15.dp))
        .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(if (selected) White.copy(alpha = .10f) else Color.Transparent))
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus?.invoke() }.clickable(onClick = press)
        .padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(11.dp)) }
        Text(t(title), Modifier.weight(1f), color = if (focused || selected) White else White.copy(alpha = .58f), fontSize = 19.sp, fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (alert) AlertBadge(size = 22.dp) else Text(trailing, color = if (focused) White.copy(alpha = .85f) else Muted, fontSize = 16.sp)
    }
}

/** A compact row of the Movies / Series glass menu. */
@Composable
private fun MenuRow(title: String, trailing: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) .97f else if (focused) 1.03f else 1f, spring(stiffness = 430f), label = "menuRowScale")
    Row(
        Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(12.dp))
            .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(if (selected) White.copy(alpha = .10f) else Color.Transparent))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = press)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(t(title), Modifier.weight(1f), color = if (focused || selected) White else White.copy(alpha = .62f), fontSize = 15.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Text(trailing, color = if (focused) White.copy(alpha = .85f) else Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column {
        Text(t(title), color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text(t(subtitle), color = Muted, fontSize = 17.sp)
    }
}

@Composable
private fun EmptyState(title: String, message: String, button: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 620.dp)) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) {
                Text("＋", color = White, fontSize = 48.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text(t(title), color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(t(message), color = Muted, fontSize = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            WhiteButton(button, onClick = onClick)
        }
    }
}

@Composable
private fun WhiteButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) .95f else if (focused) 1.04f else 1f, spring(stiffness = 430f), label = "buttonScale")
    Box(modifier.scale(scale).clip(CircleShape)
        .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(White.copy(alpha = .82f)))
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = press).padding(horizontal = 26.dp, vertical = 14.dp)) {
        Text(t(text), color = if (focused && !pressed) White else Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}


/**
 * Covers the first moments after the app opens with the wordmark, until the saved list is back:
 * no empty "no channels" page flashing by. Gone for good once it fades out.
 */
@Composable
fun StartCover(state: TvState) {
    var done by remember { mutableStateOf(false) }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(8000); waited = true }
    val starting = !state.ready ||
        (state.playlistUrl.isNotBlank() && state.channels.isEmpty() && state.error == null && !waited)
    LaunchedEffect(starting) {
        // A short minimum, so a fast start does not just blink.
        if (!starting) { delay(250); done = true }
    }
    Hints.paused = !done
    AnimatedVisibility(visible = !done, enter = fadeIn(tween(0)), exit = fadeOut(tween(420))) {
        Box(Modifier.fillMaxSize().background(LibraryBackdrop), contentAlignment = Alignment.Center) {
            val pulse = remember { Animatable(.55f) }
            LaunchedEffect(Unit) {
                while (true) {
                    pulse.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                    pulse.animateTo(.55f, tween(900, easing = FastOutSlowInEasing))
                }
            }
            Image(painterResource(ActivePalette.current.wordmark), contentDescription = "STV",
                modifier = Modifier.height(66.dp).graphicsLayer { alpha = pulse.value })
        }
    }
}
