package com.streamtv.ui

import android.content.ActivityNotFoundException

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
private val Accent = Color(0xFFD5DADF)
private val Glass = Color(0xFF29292C)
private const val ExamplePlaylistUrl = "https://iptv-org.github.io/iptv/countries/be.m3u"

/** One focus colour for every control: the soft sky blue of the tab pills. */
private val FocusBrush = Brush.verticalGradient(listOf(Color(0xFF8290A6), Color(0xFF647187)))
private val PressBrush = Brush.verticalGradient(listOf(Color(0xFFC6D1E3), Color(0xFF9FADC6)))

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

    // PIN question (locking a channel, opening a locked one, changing the PIN).
    state.pin?.let { prompt ->
        val choosing = prompt.purpose == "set"
        SingleFieldScreen(
            title = if (choosing) "Choose a 4-digit PIN" else "Enter PIN", hint = "••••", initial = "", pin = true,
            error = if (!state.pinError) null else if (choosing) "The PIN must be 4 digits" else "Wrong PIN",
            saveLabel = "Save", onSave = model::submitPin, onCancel = model::cancelPin
        )
        return
    }
    var renaming by remember { mutableStateOf<Channel?>(null) }
    renaming?.let { target ->
        SingleFieldScreen(
            title = "Rename channel", hint = "Channel name", initial = target.name, pin = false, error = null, saveLabel = "Save",
            onSave = { name -> model.renameChannel(target, name); renaming = null },
            onCancel = { renaming = null }
        )
        return
    }
    if (state.playing && state.selected != null) {
        SideEffect { returnKey = state.selected.key }
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
            onFavorite = model::toggleFavorite,
            locked = state.selected.key in state.locked,
            onOption = { option ->
                when (option) {
                    "hide" -> model.hideChannel(state.selected)
                    "lock" -> model.toggleLock(state.selected)
                    "rename" -> renaming = state.selected
                }
            }
        )
        return
    }

    // Text entry is a screen of its own: a floating TV keyboard would cover an inline form.
    var entry by remember { mutableStateOf("") }
    var settingsSection by remember { mutableStateOf("Playlist") }
    when (entry) {
        "playlist" -> {
            TextEntryScreen(
                title = "Add M3U playlist", urlHint = "Playlist address  https://…", initialUrl = "",
                exampleUrl = ExamplePlaylistUrl, showName = true, saveLabel = "Save & load",
                onSave = { name, url ->
                    model.setPlaylistName(name)
                    model.setUrl(url)
                    model.savePlaylistEntry()
                    model.loadPlaylist()
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
                    model.loadPlaylist()
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
            onExample = {
                model.setPlaylistName("Example")
                model.setUrl(ExamplePlaylistUrl)
                model.savePlaylistEntry()
                model.loadPlaylist()
            }
        )
        return
    }

    // TV safe area: 72dp sides and 30/38dp top/bottom keep everything inside the overscan margin.
    Column(Modifier.fillMaxSize().background(Ink).padding(start = 72.dp, end = 72.dp, top = 30.dp, bottom = 38.dp)) {
        // Back first returns to the tab bar; from the tab bar it leaves the app.
        val tabFocus = remember { FocusRequester() }
        var topBarFocused by remember { mutableStateOf(false) }
        BackHandler(enabled = !topBarFocused) {
            if (runCatching { tabFocus.requestFocus() }.isFailure) topBarFocused = true
        }
        TopNavigation(state.tab, model::selectTab, tabFocus) { topBarFocused = it }
        Spacer(Modifier.height(if (state.tab == "Live TV") 18.dp else 30.dp))
        when (state.tab) {
            "Movies" -> LibraryContent("Movies", ChannelKind.Movie, state, model, returnKey) { returnKey = null }
            "Series" -> LibraryContent("Series", ChannelKind.Series, state, model, returnKey) { returnKey = null }
            "Favorites" -> {
                ScreenHeading("Favorites", "${state.channels.count { it.id in state.favorites }} ${t("saved channels")}")
                Spacer(Modifier.height(20.dp))
                ChannelGrid(state.channels.filter { it.id in state.favorites }, "Favorites", state.programs, state.favorites, model, returnKey) { returnKey = null }
            }
            "Search" -> SearchContent(state, model, returnKey) { returnKey = null }
            "Settings" -> SettingsContent(state, model, settingsSection, { settingsSection = it }) { entry = it }
            else -> LiveContent(state, model, returnKey) { returnKey = null }
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
            Image(painterResource(R.drawable.stv_wordmark), contentDescription = "STV", modifier = Modifier.height(24.dp))
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
        Text(
            t(text),
            color = White.copy(alpha = if (focused || selected) .98f else .72f),
            fontSize = 16.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun LiveContent(state: TvState, model: MainViewModel, restoreKey: String?, onRestored: () -> Unit) {
    val live = remember(state.channels) { state.channels.filter { it.kind() == ChannelKind.Live } }
    val recent = remember(live, state.recent) {
        val byKey = live.associateBy { it.key }
        state.recent.mapNotNull { byKey[it] }
    }
    // Nothing chosen yet (app just opened): start on "Last watched" when there is one.
    val query = state.searchQuery.ifBlank { if (recent.isNotEmpty()) "__recent" else "__all" }
    val countryGroups = live.filter { it.country.isNotBlank() }
        .groupBy { it.country.trim().uppercase() }.toSortedMap()
    val categoryGroups = live.groupBy { it.group.ifBlank { "Other" } }.toSortedMap()
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
        else -> live.filter { it.group.equals(query, true) }
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
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        }
        Spacer(Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} ${t("channels")}", color = Muted, fontSize = 17.sp)
            }
            Spacer(Modifier.height(14.dp))
            ChannelGrid(filtered, heading, state.programs, state.favorites, model, restoreKey, onRestored)
        }
    }
}

@Composable
private fun LibraryContent(title: String, kind: ChannelKind, state: TvState, model: MainViewModel, restoreKey: String?, onRestored: () -> Unit) {
    val items = remember(state.channels, kind) { state.channels.filter { it.kind() == kind } }
    ScreenHeading(title, "${items.size} ${t("titles")}")
    Spacer(Modifier.height(20.dp))
    if (items.isEmpty()) {
        EmptyState("No ${title.lowercase()} yet", "Your playlist has no ${title.lowercase()}. Add one that includes them in Settings.", "Settings") {
            model.selectTab("Settings")
        }
    } else {
        val restoreIndex = remember(items, restoreKey) { if (restoreKey == null) -1 else items.indexOfFirst { it.key == restoreKey } }
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = if (restoreIndex >= 0) (restoreIndex / 6 * 6 - 6).coerceAtLeast(0) else 0)
        val restoreFocus = remember { FocusRequester() }
        RestoreFocus(restoreKey, restoreIndex >= 0, restoreFocus, onRestored)
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                gridItems(items, key = { it.key }) { item ->
                    PosterCard(item, item.id in state.favorites, if (item.key == restoreKey) Modifier.focusRequester(restoreFocus) else Modifier) { model.select(item, items, title) }
                }
            }
        }
    }
}

/** Poster-shaped card with the title underneath, for movies and series. */
@Composable
private fun PosterCard(channel: Channel, favorite: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, spring(dampingRatio = .7f, stiffness = 380f), label = "posterScale")
    Column(
        modifier.zIndex(if (focused) 1f else 0f).scale(scale).onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .shadow(if (focused) 18.dp else 0.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .border(if (focused) 2.dp else 1.dp, White.copy(alpha = if (focused) .9f else .08f), RoundedCornerShape(12.dp))
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

@Composable
private fun WelcomeScreen(loading: Boolean, error: String?, onAdd: () -> Unit, onXtream: () -> Unit, onExample: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16161A), Ink))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 760.dp)) {
            Image(painterResource(R.drawable.banner), contentDescription = "STV",
                modifier = Modifier.width(240.dp).height(135.dp).clip(RoundedCornerShape(20.dp)))
            Spacer(Modifier.height(26.dp))
            Text(t("Welcome to STV"), color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(t("Add your M3U playlist to start watching live TV, movies and series.\nYou can change it any time in Settings."),
                color = Muted, fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 26.sp)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                WhiteButton(if (loading) "Loading…" else "Add M3U playlist", Modifier.focusRequester(first)) { if (!loading) onAdd() }
                WhiteButton("Xtream login") { if (!loading) onXtream() }
                WhiteButton("Try an example") { if (!loading) onExample() }
            }
            error?.let {
                Spacer(Modifier.height(18.dp))
                Text(it, color = Color(0xFFFF8B7B), fontSize = 16.sp, textAlign = TextAlign.Center)
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
    restoreKey: String? = null, onRestored: () -> Unit = {}
) {
    if (channels.isEmpty()) {
        EmptyState("No channels yet", "Add a playlist or choose another group.", "Settings") { model.selectTab("Settings") }
        return
    }
    val restoreIndex = remember(channels, restoreKey) { if (restoreKey == null) -1 else channels.indexOfFirst { it.key == restoreKey } }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = if (restoreIndex >= 0) (restoreIndex / 4 * 4 - 4).coerceAtLeast(0) else 0)
    val restoreFocus = remember { FocusRequester() }
    RestoreFocus(restoreKey, restoreIndex >= 0, restoreFocus, onRestored)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            // Room around the grid so the enlarged, focused tile is never cut off.
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 24.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            gridItems(channels, key = { it.key }) { channel ->
                ChannelCard(channel, channel.id in favorites, Modifier.fillMaxWidth().height(72.dp).then(if (channel.key == restoreKey) Modifier.focusRequester(restoreFocus) else Modifier)) { model.select(channel, channels, title) }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, favorite: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val (pressed, press) = rememberPress(onClick)
    val scale by animateFloatAsState(if (pressed) 1.05f else if (focused) 1.14f else 1f, spring(dampingRatio = .7f, stiffness = 380f), label = "cardScale")
    Box(
        // The focused tile grows and sits above its neighbours.
        modifier.zIndex(if (focused) 1f else 0f).scale(scale)
            .shadow(if (focused) 18.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(if (focused) listOf(Color(0xFF323946), Color(0xFF20252E)) else listOf(Color(0xFF252A33), Color(0xFF171B22))))
            .border(if (focused) 2.dp else 1.dp, White.copy(alpha = if (focused) .9f else .07f), RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
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
private fun SearchContent(state: TvState, model: MainViewModel, restoreKey: String?, onRestored: () -> Unit) {
    ScreenHeading("Search", "Find a channel or category")
    Spacer(Modifier.height(16.dp))
    GlassInput(state.searchQuery, "Search channels", model::setSearchQuery, Modifier.fillMaxWidth(.62f))
    Spacer(Modifier.height(16.dp))
    val q = state.searchQuery.trim()
    val matches = if (q.isEmpty()) emptyList() else state.channels.filter { it.name.contains(q, true) || it.group.contains(q, true) || it.country.contains(q, true) }
    Text(if (q.isEmpty()) t("Start typing to search") else "${matches.size} ${t("results")}", color = Muted, fontSize = 17.sp)
    Spacer(Modifier.height(14.dp))
    ChannelGrid(matches, "Search results", state.programs, state.favorites, model, restoreKey, onRestored)
}

@Composable
private fun SettingsContent(
    state: TvState, model: MainViewModel, section: String, onSection: (String) -> Unit, onEntry: (String) -> Unit
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
        Column(Modifier.width(270.dp)) {
            Text(t("Settings"), color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(26.dp))
            listOf("Playback", "Playlist", "Language", "About", "Parental control", "TV guide").forEach { item ->
                FocusRow(item, if (item == section) "●" else "", item == section) { onSection(item) }
            }
        }
        Spacer(Modifier.width(44.dp))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text(t(section), color = White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
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
                            PlaylistRow(item, item.url == state.playlistUrl, { model.selectPlaylist(item) }) { model.removePlaylist(item) }
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
                    Text(t("Interface language"), color = Muted, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    for ((code, name) in AppLanguages) {
                        FocusRow(name, if (state.language == code) "●" else "", state.language == code) { model.setLanguage(code) }
                    }
                }
                "Parental control" -> {
                    SettingsRow("PIN", if (state.pinSet) "Set" else "Not set")
                    Spacer(Modifier.height(12.dp))
                    WhiteButton(if (state.pinSet) "Change PIN" else "Set PIN") { model.changePin() }
                    Spacer(Modifier.height(18.dp))
                    SettingsRow("Locked channels", state.locked.size.toString())
                    Spacer(Modifier.height(12.dp))
                    SettingsRow("Hidden channels", state.hidden.size.toString())
                    if (state.hidden.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        WhiteButton("Restore hidden channels") { model.restoreHidden() }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                "About" -> AppUpdateSection()
                else -> SettingsRow("streamtv", "Version 1.0.0")
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

@Composable
private fun SettingsRow(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(White.copy(alpha = .07f)).padding(20.dp)) {
        Text(t(title), color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(t(subtitle), color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

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
