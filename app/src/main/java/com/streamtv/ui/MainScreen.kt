package com.streamtv.ui

import android.content.ActivityNotFoundException

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.streamtv.data.detectedLanguage
import com.streamtv.data.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF000000)
private val Surface = Color(0xFF0C0C0E)
private val Raised = Color(0xFF1C1C1E)
private val White = Color(0xFFFFFFFF)
private val Muted = Color(0xFF99999E)
// Warm orange, from the S of the logo: picked values, check marks, the focus.
private val Accent = Color(0xFFF2683F)
private val Glass = Color(0xFF3A3B3F)

/** Something destructive waiting for the viewer to say yes. */
private class ConfirmRequest(val question: String, val subject: String, val confirmLabel: String, val action: () -> Unit)

/** One focus colour for every control: the soft sky blue of the tab pills. */
private val FocusBrush = Brush.verticalGradient(listOf(Color(0xFFF27A50), Color(0xFFDC5530)))
private val PressBrush = Brush.verticalGradient(listOf(Color(0xFFFFA483), Color(0xFFF27A50)))
/** Warm graphite, a little lighter at the top. */
private val LibraryBackdrop = Brush.verticalGradient(listOf(Color(0xFF3A3B3F), Color(0xFF2A2B2E), Color(0xFF1D1E20)))
/** A soft orange glow rising from the bottom-left corner, over the graphite. */
private val WarmGlow = Brush.linearGradient(
    0f to Color(0x47C2652E), .55f to Color.Transparent,
    start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
)
/** Dark glass: see-through black with a lit edge, brighter where the light falls (top left). */
private val GlassFill = Brush.verticalGradient(listOf(Color(0x59202124), Color(0x73151618)))
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
    // The Movies and Series pages keep their place (menu choice, open title) while a title plays.
    val movieUi = remember { LibraryUi() }
    val seriesUi = remember { LibraryUi() }
    // Where the next movie starts: 0, or the place it was left when "Resume" was chosen.
    var vodStart by remember { mutableLongStateOf(0L) }

    // With no PIN set, hiding a channel or removing a playlist asks a plain yes/no question on the
    // back of the card instead; with a PIN, the card asks for the PIN and nothing else.
    var question by remember { mutableStateOf<CardQuestion?>(null) }
    var renaming by remember { mutableStateOf<Channel?>(null) }
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
    val handleOption: (Channel, String) -> Unit = { target, option ->
        // Locking and hiding keep the card open: it turns over to ask for the PIN. A favourite keeps
        // it open too, so the star can be seen filling in. Everything else closes it.
        if (option != "lock" && option != "hide" && option != "favorite") optionsFor = null
        when (option) {
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
    if (cardChannel != null || prompt != null || question != null) {
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
                    else -> { optionsFor = null }
                }
            },
            // Left alone for a while, the card puts itself away, PIN question and all.
            onIdle = { if (prompt != null) model.cancelPin(); question = null; optionsFor = null },
            options = cardChannel?.let { target ->
                CardOptions(
                    channel = target, starred = target.id in state.favorites, locked = target.key in state.locked,
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
            channel = title, favorite = title.id in state.favorites, startAt = vodStart,
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

    // TV safe area: 72dp sides and 30/38dp top/bottom keep everything inside the overscan margin.
    // Every page sits on the softer, less black backdrop.
    Column(
        Modifier.fillMaxSize()
            .background(LibraryBackdrop).background(WarmGlow)
            .padding(start = 72.dp, end = 72.dp, top = 30.dp, bottom = 38.dp)
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
            // Movies uses the Live TV style of list for its menu.
            "Movies" -> LibraryContent(ChannelKind.Movie, state, model, movieUi, returnKey, { returnKey = null }, { returnKey = it }, playTitle, liveMenu = true)
            "Series" -> LibraryContent(ChannelKind.Series, state, model, seriesUi, returnKey, { returnKey = null }, { returnKey = it }, playTitle)
            "Favorites" -> {
                ScreenHeading("Favorites", "${state.channels.count { it.id in state.favorites }} ${t("saved channels")}")
                Spacer(Modifier.height(20.dp))
                ChannelGrid(state.channels.filter { it.id in state.favorites }, "Favorites", state.programs, state.favorites, model, returnKey, { returnKey = null }, openOptions)
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
                listOf("Live TV", "Movies", "Series", "Search", "Settings").forEach { item ->
                    val selected = tab == item
                    FocusPill(item, selected, Modifier.weight(1f).fillMaxWidth().then(if (selected) Modifier.focusRequester(tabFocus) else Modifier)) { onSelect(item) }
                }
            }
        }
        Box(Modifier.width(124.dp), contentAlignment = Alignment.CenterEnd) {
            // Quiet in the corner: a little smaller and dimmed, so the eye goes to the content.
            Image(painterResource(R.drawable.stv_wordmark), contentDescription = "STV", modifier = Modifier.height(20.dp).graphicsLayer { alpha = .6f })
        }
    }
}

/** 24h clock with Western digits in left-to-right order, whatever the TV language is. */
@Composable
private fun Clock() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 100L)
            now = System.currentTimeMillis()
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
            color = if (selected && !focused && !pressed) Accent else White.copy(alpha = if (focused) .98f else .72f),
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
    val live = remember(state.channels) { state.channels.filter { it.kind() == ChannelKind.Live } }
    val recent = remember(live, state.recent) {
        val byKey = live.associateBy { it.key }
        state.recent.mapNotNull { byKey[it] }
    }
    // Nothing chosen yet (app just opened): start on "Last watched" when there is one.
    val countryGroups = live.filter { it.country.isNotBlank() }
        .groupBy { it.country.trim().uppercase() }.toSortedMap()
    val categoryGroups = live.groupBy { it.group.ifBlank { "Other" } }.toSortedMap()
    // A choice that no longer matches anything falls back to the default list instead of "no channels".
    val chosen = state.searchQuery
    val stillValid = when {
        chosen.isBlank() -> false
        chosen == "__recent" -> recent.isNotEmpty()
        chosen == "__favorites" -> live.any { it.id in state.favorites }
        chosen.startsWith("__") -> true
        else -> categoryGroups.keys.any { it.equals(chosen, true) }
    }
    val query = if (stillValid) chosen else if (recent.isNotEmpty()) "__recent" else "__all"
    val languageGroups = listOf(
        Triple("العربية", "__language_ar", live.count { it.detectedLanguage() == "ar" }),
        Triple("Français", "__language_fr", live.count { it.detectedLanguage() == "fr" })
    ).filter { it.third > 0 }
    val filtered = when {
        query == "__recent" -> recent
        query == "__favorites" -> live.filter { it.id in state.favorites }
        query == "__all" -> live
        query == "__language_ar" -> live.filter { it.detectedLanguage() == "ar" }
        query == "__language_fr" -> live.filter { it.detectedLanguage() == "fr" }
        query.startsWith("__country:") -> live.filter { it.country.equals(query.removePrefix("__country:"), true) }
        else -> live.filter { it.group.ifBlank { "Other" }.equals(query, true) }
    }
    val heading = when {
        query == "__recent" -> "Last watched"
        query == "__favorites" -> "Favorites"
        query == "__language_ar" -> "العربية"
        query == "__language_fr" -> "Français"
        query.startsWith("__country:") -> query.removePrefix("__country:")
        query == "__all" -> "All channels"
        else -> query
    }
    Row(Modifier.fillMaxSize()) {
        val groupsState = rememberLazyListState()
        Box(Modifier.width(250.dp).fillMaxHeight()) {
            LazyColumn(state = groupsState, modifier = Modifier.fillMaxSize().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (recent.isNotEmpty()) {
                    item { FocusRow("Last watched", recent.size.toString(), query == "__recent") { model.setSearchQuery("__recent") } }
                }
                if (state.favorites.any { favoriteId -> live.any { it.id == favoriteId } }) {
                    item { FocusRow("Favorites", live.count { it.id in state.favorites }.toString(), query == "__favorites") { model.setSearchQuery("__favorites") } }
                }
                item { FocusRow("All channels", live.size.toString(), query == "__all") { model.setSearchQuery("__all") } }
                if (languageGroups.isNotEmpty()) {
                    item { Text(t("BY LANGUAGE"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                    items(languageGroups) { item ->
                        FocusRow(item.first, item.third.toString(), query == item.second) { model.setSearchQuery(item.second) }
                    }
                }
                if (countryGroups.isNotEmpty()) {
                    item { Text(t("BY COUNTRY"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                    items(countryGroups.toList()) { (country, channels) ->
                        val token = "__country:$country"
                        FocusRow(country, channels.size.toString(), query == token) { model.setSearchQuery(token) }
                    }
                }
                item { Text(t("BY CATEGORY"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                items(categoryGroups.toList()) { (name, channels) ->
                    FocusRow(name, channels.size.toString(), query == name) { model.setSearchQuery(name) }
                }
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
    val titles = remember(state.channels, kind) { state.channels.filter { it.kind() == kind } }
    val name = if (kind == ChannelKind.Movie) "movies" else "series"
    if (titles.isEmpty()) {
        EmptyState("No $name yet", "Your playlist has no $name. Add one that includes them in Settings.", "Settings") {
            model.selectTab("Settings")
        }
        return
    }
    ui.details?.let { item ->
        TitleDetails(
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
    val favorites = remember(titles, state.favorites) { titles.filter { it.id in state.favorites } }
    val genres = remember(titles) { titles.groupBy { it.group.ifBlank { "Other" } }.toSortedMap() }
    val query = ui.search.trim()
    val chosen = ui.section
    val section = when {
        chosen == "__history" -> if (history.isEmpty()) "__all" else chosen
        chosen == "__favorites" -> if (favorites.isEmpty()) "__all" else chosen
        chosen == "__all" || genres.containsKey(chosen) -> chosen
        else -> "__all"
    }
    val shown = when {
        query.isNotEmpty() -> titles.filter { it.name.contains(query, true) || it.group.contains(query, true) }
        section == "__history" -> history
        section == "__favorites" -> favorites
        section == "__all" -> titles
        else -> genres[section].orEmpty()
    }
    Row(Modifier.fillMaxSize()) {
        val menuState = rememberLazyListState()
        val searchHint = if (kind == ChannelKind.Movie) "Search movies" else "Search series"
        if (liveMenu) {
            // The same list as Live TV: plain rows, section headings, the scroll bar on its edge.
            Box(Modifier.width(250.dp).fillMaxHeight()) {
                LazyColumn(state = menuState, modifier = Modifier.fillMaxSize().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item { LibrarySearch(ui.search, searchHint, large = true) { ui.search = it } }
                    item { FocusRow("All", titles.size.toString(), query.isEmpty() && section == "__all") { ui.search = ""; ui.section = "__all" } }
                    if (history.isNotEmpty()) {
                        item { FocusRow("Watch history", history.size.toString(), query.isEmpty() && section == "__history") { ui.search = ""; ui.section = "__history" } }
                    }
                    if (favorites.isNotEmpty()) {
                        item { FocusRow("Favorites", favorites.size.toString(), query.isEmpty() && section == "__favorites") { ui.search = ""; ui.section = "__favorites" } }
                    }
                    item { Text(t("BY CATEGORY"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                    items(genres.toList()) { (genre, list) ->
                        FocusRow(genre, list.size.toString(), query.isEmpty() && section == genre) { ui.search = ""; ui.section = genre }
                    }
                }
                ScrollIndicator(menuState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp))
            }
            Spacer(Modifier.width(30.dp))
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
                PosterGrid(shown, if (query.isNotEmpty()) "?$query" else section, state.favorites, restoreKey, onRestored) { ui.details = it }
            }
        }
    }
}

@Composable
private fun PosterGrid(
    titles: List<Channel>, listKey: String, favorites: Set<String>, restoreKey: String?, onRestored: () -> Unit, onOpen: (Channel) -> Unit
) {
    // Each list starts at its own top.
    key(listKey) {
        val restoreIndex = remember(titles, restoreKey) { if (restoreKey == null) -1 else titles.indexOfFirst { it.key == restoreKey } }
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = if (restoreIndex >= 0) (restoreIndex - 4).coerceAtLeast(0) else 0)
        val restoreFocus = remember { FocusRequester() }
        RestoreFocus(restoreKey, restoreIndex >= 0, restoreFocus, onRestored)
        var focusedKey by remember { mutableStateOf<String?>(null) }
        GlideToFocus(gridState, focusedKey)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                // About 10% smaller posters than before.
                columns = GridCells.FixedSize(106.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                // Free room under the last row, so every row can glide up to the focus height.
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 16.dp, bottom = maxHeight),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItems(titles, key = { it.key }) { item ->
                    PosterCard(item, item.id in favorites, if (item.key == restoreKey) Modifier.focusRequester(restoreFocus) else Modifier, { focusedKey = item.key }) { onOpen(item) }
                }
            }
            ScrollIndicator(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp))
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
                textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = if (large) 18.sp else 15.sp),
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
    item: Channel, kind: ChannelKind, titles: List<Channel>, state: TvState,
    onPlay: (Long) -> Unit, onFavorite: () -> Unit, onOpen: (Channel) -> Unit, onClose: () -> Unit
) {
    BackHandler { onClose() }
    val saved = state.positions[item.key]
    val starred = item.id in state.favorites
    val primary = remember(item.key) { FocusRequester() }
    LaunchedEffect(item.key) {
        repeat(30) {
            if (runCatching { primary.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    val more = remember(item.key, titles) { titles.filter { it.group == item.group && it.key != item.key }.take(24) }
    // The page eases in when a title opens.
    val enter = remember(item.key) { Animatable(0f) }
    LaunchedEffect(item.key) { enter.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    // On the "More in" row, the poster and its details are pushed up under the tab bar, and the
    // row takes their place; back up, they glide down again. Nothing shrinks.
    var lower by remember(item.key) { mutableStateOf(false) }
    val lift by animateFloatAsState(if (lower) 1f else 0f, tween(480, easing = FastOutSlowInEasing), label = "detailsLift")
    var topHeight by remember { mutableStateOf(0) }
    val gap = with(LocalDensity.current) { 16.dp.toPx() }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // The poster, faint and wide, behind the page.
        AsyncImage(
            model = item.logo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).graphicsLayer { alpha = .14f }
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF232427)))))
        // Everything fits on one screen: nothing scrolls away, so the buttons are always one press up.
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 48f }
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.onSizeChanged { topHeight = it.height }
                    .graphicsLayer { translationY = -(topHeight + gap) * lift; alpha = 1f - lift }
            ) {
                Box(
                    Modifier.width(124.dp).aspectRatio(2f / 3f).shadow(18.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                        .border(1.dp, White.copy(alpha = .12f), RoundedCornerShape(14.dp))
                ) {
                    ChannelLogo(
                        item.name, item.logo, Modifier.fillMaxSize(), fontSize = 26.sp, corner = 14.dp,
                        placeholder = Brush.linearGradient(channelGradient(item.name)), contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(34.dp))
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(item.name, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 36.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, White.copy(alpha = .35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(t(if (kind == ChannelKind.Movie) "Movie" else "Series"), color = White.copy(alpha = .85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(t(item.group.ifBlank { "Other" }), color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (saved != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val done = (saved.first.toFloat() / saved.second.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            Box(Modifier.width(200.dp).height(4.dp).clip(CircleShape).background(White.copy(alpha = .2f))) {
                                Box(Modifier.fillMaxWidth(done).fillMaxHeight().clip(CircleShape).background(White))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("${formatTime((saved.second - saved.first).coerceAtLeast(0L))} ${t("left")}", color = Muted, fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (saved != null) {
                            WhiteButton("Resume", Modifier.focusRequester(primary)) { onPlay(saved.first) }
                            WhiteButton("Start over") { onPlay(0L) }
                        } else {
                            WhiteButton("Play", Modifier.focusRequester(primary)) { onPlay(0L) }
                        }
                        WhiteButton(if (starred) "Remove from favorites" else "Add to favorites") { onFavorite() }
                    }
                }
            }
            if (more.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.graphicsLayer { translationY = -(topHeight + gap) * lift }) {
                Text("${t("More in")} ${t(item.group.ifBlank { "Other" })}", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(
                    // Up from this row always goes back to Play / Resume.
                    modifier = Modifier.onFocusChanged { lower = it.hasFocus }.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            runCatching { primary.requestFocus() }
                            true
                        } else false
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(more, key = { it.key }) { other ->
                        PosterCard(other, other.id in state.favorites, Modifier.width(70.dp)) { onOpen(other) }
                    }
                }
                }
            }
        }
    }
}

/** Poster-shaped card with the title underneath, for movies and series. */
@Composable
private fun PosterCard(channel: Channel, favorite: Boolean, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, spring(dampingRatio = .7f, stiffness = 380f), label = "posterScale")
    Column(
        modifier.zIndex(if (focused) 1f else 0f).scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .shadow(if (focused) 24.dp else 0.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, White.copy(alpha = if (focused) .16f else .08f), RoundedCornerShape(12.dp))
        ) {
            ChannelLogo(
                channel.name, channel.logo, Modifier.fillMaxSize(), fontSize = 26.sp, corner = 12.dp,
                placeholder = Brush.linearGradient(channelGradient(channel.name)), contentScale = ContentScale.Crop
            )
            if (favorite) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = White, fontSize = 15.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(channel.name, color = if (focused) White else White.copy(alpha = .78f), fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
    }
}

/** First screen of a fresh install: the wordmark and the three ways to add a playlist. */
@Composable
private fun WelcomeScreen(
    loading: Boolean, error: String?, onAdd: () -> Unit, onXtream: () -> Unit,
    onImport: (String) -> Unit, onError: (String) -> Unit
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to open the selected M3U file")
            }.onSuccess(onImport).onFailure { onError(it.message ?: "Unable to import playlist") }
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Box(
        Modifier.fillMaxSize().background(LibraryBackdrop).background(WarmGlow),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.stv_wordmark), contentDescription = "STV", modifier = Modifier.height(66.dp))
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
    val restoreIndex = remember(channels, restoreKey) { if (restoreKey == null) -1 else channels.indexOfFirst { it.key == restoreKey } }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = if (restoreIndex >= 0) (restoreIndex - 8).coerceAtLeast(0) else 0)
    val restoreFocus = remember { FocusRequester() }
    RestoreFocus(restoreKey, restoreIndex >= 0, restoreFocus, onRestored)
    var focusedKey by remember { mutableStateOf<String?>(null) }
    GlideToFocus(gridState, focusedKey)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            // Tiles keep one size: four across beside the Live TV lists, more when the grid is wider (Search).
            columns = GridCells.Adaptive(110.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            // Room around the grid so the enlarged, focused tile is never cut off.
            // The free room under the last row lets every row glide up to the focus height.
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = maxHeight, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            gridItems(channels, key = { it.key }) { channel ->
                ChannelCard(channel, channel.id in favorites, { onOptions(channel) }, Modifier.fillMaxWidth().height(72.dp).then(if (channel.key == restoreKey) Modifier.focusRequester(restoreFocus) else Modifier), { focusedKey = channel.key }) { model.select(channel, channels, title) }
            }
        }
        // A thin, faint bar on the far side of the tiles (the left in Arabic) shows where in the list you are.
        ScrollIndicator(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp))
    }
}

@Composable
private fun ChannelCard(channel: Channel, favorite: Boolean, onLongPress: () -> Unit, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // Holding OK: the first repeat of the key opens the options, and its release must not also open the channel.
    val held = remember { booleanArrayOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) 1.07f else if (focused) 1.17f else 1f, spring(dampingRatio = .7f, stiffness = 380f), label = "cardScale")
    Box(
        // The focused tile grows and sits above its neighbours.
        modifier.zIndex(if (focused) 1f else 0f).scale(scale)
            .shadow(if (focused) 24.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(if (focused) listOf(Color(0xFF5C5D62), Color(0xFF3E3F43)) else listOf(Color(0xFF45464A), Color(0xFF303134))))
            // No white frame: the tile is picked out by its size, its lighter face and its shadow.
            .border(1.dp, White.copy(alpha = if (focused) .16f else .07f), RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
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
            channel.name, channel.logo, Modifier.fillMaxSize().padding(8.dp), fontSize = 25.sp, corner = 8.dp,
            placeholder = Brush.linearGradient(channelGradient(channel.name)), logoPadding = 2.dp
        )
        if (favorite) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = Accent, fontSize = 15.sp)
    }
}

private fun channelGradient(name: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF30363B), Color(0xFF202428), Color(0xFF141719)),
        listOf(Color(0xFF3A3430), Color(0xFF29231F), Color(0xFF171513)),
        listOf(Color(0xFF34343A), Color(0xFF25252B), Color(0xFF151519)),
        listOf(Color(0xFF303A39), Color(0xFF222B2A), Color(0xFF141A19))
    )
    return palettes[name.hashCode().ushr(1) % palettes.size]
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
                            textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 18.sp),
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
                        PosterCard(item, item.id in state.favorites, onFocus = { posterFocus = item.key }) { model.select(item, matches, "Search results") }
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
private fun SettingsContent(
    state: TvState, model: MainViewModel, section: String, onSection: (String) -> Unit,
    onConfirm: (ConfirmRequest) -> Unit, onEntry: (String) -> Unit
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to open the selected M3U file")
            }.onSuccess(model::importPlaylist).onFailure { model.setError(it.message ?: "Unable to import playlist") }
        }
    }
    Row(Modifier.fillMaxSize()) {
        // The sections as neat boxes of one size on a glass card, in a sensible order.
        Column(
            Modifier.width(260.dp).padding(top = 4.dp).clip(RoundedCornerShape(24.dp)).background(GlassFill)
                .border(1.dp, GlassEdge, RoundedCornerShape(24.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Playlist", "Playback", "TV guide", "Language", "Parental control", "About").forEach { item ->
                SectionRow(item, item == section) { onSection(item) }
            }
        }
        Spacer(Modifier.width(36.dp))
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
                        FocusPill("M3U link", false, Modifier.weight(1f)) { if (!state.loading) onEntry("playlist") }
                        FocusPill("Xtream login", false, Modifier.weight(1f)) { if (!state.loading) onEntry("xtream") }
                        FocusPill("Import file", false, Modifier.weight(1f)) {
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
                    Spacer(Modifier.height(24.dp))
                }
                "TV guide" -> {
                    SettingsRow("XMLTV guide URL", state.epgUrl.ifBlank { "Optional programme guide · not set" })
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WhiteButton("Set guide address") { onEntry("epg") }
                        if (state.epgUrl.isNotBlank()) WhiteButton("Reload TV guide") { model.loadEpg() }
                    }
                    state.error?.let { Text(it, color = Color(0xFFFF8B7B), fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp)) }
                    Text("${state.programs.size} ${t("programmes loaded")}", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp))
                }
                "Playback" -> SettingsRow("Playback engine", "Automatic · HLS / DASH")
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

/** One section of Settings: a box of fixed height; the open one is lit and carries a mark on its edge. */
@Composable
private fun SectionRow(title: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
            .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(White.copy(alpha = if (selected) .12f else .04f)))
            .border(1.dp, White.copy(alpha = if (focused) .4f else if (selected) .14f else .05f), RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = press)
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(if (selected) White else Color.Transparent))
        Spacer(Modifier.width(11.dp))
        Text(t(title), Modifier.weight(1f), color = if (focused || selected) White else White.copy(alpha = .62f), fontSize = 17.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun FocusRow(title: String, trailing: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) .97f else if (focused) 1.025f else 1f, spring(stiffness = 430f), label = "rowScale")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).scale(scale).clip(RoundedCornerShape(15.dp))
        .background(if (pressed) PressBrush else if (focused) FocusBrush else SolidColor(if (selected) White.copy(alpha = .10f) else Color.Transparent))
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = press)
        .padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t(title), Modifier.weight(1f), color = if (focused || selected) White else White.copy(alpha = .58f), fontSize = 19.sp, fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal)
        Text(trailing, color = if (focused) White.copy(alpha = .85f) else Muted, fontSize = 16.sp)
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

@Composable
private fun GlassInput(value: String, hint: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(Glass).border(1.dp, White.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(value = value, onValueChange = onValueChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 18.sp), modifier = Modifier.fillMaxWidth(), decorationBox = { inner ->
            if (value.isEmpty()) Text(t(hint), color = Muted, fontSize = 18.sp)
            inner()
        })
    }
}

private fun currentProgram(channel: Channel, programs: List<EpgProgram>): EpgProgram? {
    val now = System.currentTimeMillis()
    return programs.firstOrNull { (it.channelId == channel.id || it.channelId.equals(channel.name, true)) && it.startEpochMillis <= now && it.endEpochMillis > now }
}
