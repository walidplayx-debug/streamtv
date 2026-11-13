package com.streamtv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamtv.data.Channel
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

@Composable
fun MainScreen(state: TvState, model: MainViewModel) {
    if (state.playing && state.selected != null) {
        PlayerScreen(
            channel = state.selected,
            channels = state.channels,
            programs = state.programs,
            favorites = state.favorites,
            onBack = model::stopPlaying,
            onZap = model::zap,
            onSelect = model::select,
            onFavorite = model::toggleFavorite
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Ink).padding(start = 72.dp, end = 72.dp, top = 30.dp, bottom = 38.dp)) {
        TopNavigation(state.tab, model::selectTab)
        Spacer(Modifier.height(30.dp))
        when (state.tab) {
            "Live TV" -> LiveContent(state, model)
            "Categories" -> CategoryContent(state.channels, model)
            "Favorites" -> {
                ScreenHeading("Favorites", "${state.channels.count { it.id in state.favorites }} saved channels")
                Spacer(Modifier.height(20.dp))
                ChannelGrid(state.channels.filter { it.id in state.favorites }, state.programs, state.favorites, model)
            }
            "Search" -> SearchContent(state, model)
            "Settings" -> SettingsContent(state, model)
            else -> HomeContent(state, model)
        }
    }
}

@Composable
private fun TopNavigation(tab: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("STREEM", color = Accent, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).background(Glass.copy(alpha = .72f)).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Home", "Live TV", "Categories", "Favorites", "Search", "Settings").forEach { item ->
                val selected = tab == item
                FocusPill(item, selected, Modifier.padding(horizontal = 2.dp)) { onSelect(item) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), color = Muted, fontSize = 19.sp)
    }
}

@Composable
private fun FocusPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(stiffness = 500f), label = "pillScale")
    Box(
        modifier.scale(scale).clip(CircleShape)
            .background(if (selected) White.copy(alpha = .18f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).focusable().padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) White else White.copy(alpha = .65f), fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) }
}

@Composable
private fun HomeContent(state: TvState, model: MainViewModel) {
    if (state.channels.isEmpty()) {
        EmptyState(
            title = "Your TV starts here",
            message = "Add an M3U playlist to bring your channels together.",
            button = if (state.loading) "Loading…" else "Add your playlist",
            onClick = { model.selectTab("Settings") }
        )
        return
    }
    val hero = state.selected ?: state.channels.first()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Accent))
                    Spacer(Modifier.width(10.dp))
                    Text("LIVE  ·  ${hero.name}", color = Muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(18.dp))
                Text(hero.name, color = White, fontSize = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(currentProgram(hero, state.programs)?.title ?: hero.group, color = Muted, fontSize = 21.sp, maxLines = 2)
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WhiteButton("Watch live") { model.select(hero) }
                    Spacer(Modifier.width(14.dp))
                    GlassCircle(if (hero.id in state.favorites) "★" else "+") { model.toggleFavorite(hero) }
                }
            }
            Box(Modifier.weight(1.15f).fillMaxHeight(.92f).clip(RoundedCornerShape(24.dp))) {
                ChannelArtwork(hero, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Ink.copy(alpha = .48f), Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .82f)))))
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your channels", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Browse categories  ›", color = Muted, fontSize = 17.sp, modifier = Modifier.clickable { model.selectTab("Categories") }.focusable())
        }
        Spacer(Modifier.height(14.dp))
        ChannelShelf(state.channels, state.programs, state.favorites, model)
    }
}

@Composable
private fun LiveContent(state: TvState, model: MainViewModel) {
    val countryGroups = state.channels.filter { it.country.isNotBlank() }
        .groupBy { it.country.trim().uppercase() }.toSortedMap()
    val categoryGroups = state.channels.groupBy { it.group.ifBlank { "Other" } }.toSortedMap()
    val languageGroups = listOf(
        Triple("العربية", "__language_ar", state.channels.count { it.detectedLanguage() == "ar" }),
        Triple("Français", "__language_fr", state.channels.count { it.detectedLanguage() == "fr" })
    ).filter { it.third > 0 }
    val filtered = when {
        state.searchQuery.isBlank() || state.searchQuery == "__all" -> state.channels
        state.searchQuery == "__language_ar" -> state.channels.filter { it.detectedLanguage() == "ar" }
        state.searchQuery == "__language_fr" -> state.channels.filter { it.detectedLanguage() == "fr" }
        state.searchQuery.startsWith("__country:") -> state.channels.filter { it.country.equals(state.searchQuery.removePrefix("__country:"), true) }
        else -> state.channels.filter { it.group.equals(state.searchQuery, true) }
    }
    val heading = when {
        state.searchQuery == "__language_ar" -> "العربية"
        state.searchQuery == "__language_fr" -> "Français"
        state.searchQuery.startsWith("__country:") -> state.searchQuery.removePrefix("__country:")
        state.searchQuery.isBlank() || state.searchQuery == "__all" -> "All channels"
        else -> state.searchQuery
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            Text("Live TV", color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { FocusRow("All channels", state.channels.size.toString(), state.searchQuery.isBlank() || state.searchQuery == "__all") { model.setSearchQuery("__all") } }
                if (languageGroups.isNotEmpty()) {
                    item { Text("BY LANGUAGE", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                    items(languageGroups) { item ->
                        FocusRow(item.first, item.third.toString(), state.searchQuery == item.second) { model.setSearchQuery(item.second) }
                    }
                }
                if (countryGroups.isNotEmpty()) {
                    item { Text("BY COUNTRY", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                    items(countryGroups.toList()) { (country, channels) ->
                        val token = "__country:$country"
                        FocusRow(country, channels.size.toString(), state.searchQuery == token) { model.setSearchQuery(token) }
                    }
                }
                item { Text("BY CATEGORY", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 5.dp)) }
                items(categoryGroups.toList()) { (name, channels) ->
                    FocusRow(name, channels.size.toString(), state.searchQuery == name) { model.setSearchQuery(name) }
                }
            }
        }
        Spacer(Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(heading, color = White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} channels", color = Muted, fontSize = 17.sp)
            }
            Spacer(Modifier.height(14.dp))
            ChannelGrid(filtered, state.programs, state.favorites, model)
        }
    }
}

@Composable
private fun ChannelGrid(channels: List<Channel>, programs: List<EpgProgram>, favorites: Set<String>, model: MainViewModel) {
    if (channels.isEmpty()) {
        EmptyState("No channels yet", "Add a playlist or choose another group.", "Settings") { model.selectTab("Settings") }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp, end = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        gridItems(channels, key = { it.id }) { channel ->
            ChannelCard(channel, programs, channel.id in favorites, Modifier.fillMaxWidth().height(82.dp)) { model.select(channel) }
        }
    }
}

@Composable
private fun ChannelShelf(channels: List<Channel>, programs: List<EpgProgram>, favorites: Set<String>, model: MainViewModel) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(end = 30.dp, bottom = 12.dp)
    ) {
        items(channels.take(18), key = { it.id }) { channel ->
            Column(Modifier.width(232.dp)) {
                ChannelCard(channel, programs, channel.id in favorites, Modifier.width(232.dp).height(130.dp)) { model.select(channel) }
                Spacer(Modifier.height(8.dp))
                Text(channel.name, color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(currentProgram(channel, programs)?.title ?: channel.group, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, programs: List<EpgProgram>, favorite: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, spring(dampingRatio = .82f, stiffness = 420f), label = "cardScale")
    Box(
        modifier.scale(scale).then(modifier).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF252A33), Color(0xFF171B22))))
            .border(if (focused) 2.dp else 1.dp, White.copy(alpha = if (focused) .82f else .07f), RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) {
        if (!channel.logo.isNullOrBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(channel.name, color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (favorite) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = Accent, fontSize = 15.sp)
    }
}

@Composable
private fun ChannelArtwork(channel: Channel, modifier: Modifier = Modifier) {
    if (!channel.logo.isNullOrBlank()) {
        Box(modifier.background(channelColor(channel.name))) {
            AsyncImage(model = channel.logo, contentDescription = channel.name, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .22f)))))
        }
    } else {
        Box(modifier.background(Brush.linearGradient(channelGradient(channel.name)))) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-18).dp).size(110.dp).background(White.copy(alpha = .10f), CircleShape))
            Text(channel.name.take(1).uppercase(), Modifier.align(Alignment.Center), color = White.copy(alpha = .72f), fontSize = 54.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun channelColor(name: String): Color {
    val colors = listOf(Color(0xFF25272B), Color(0xFF292624), Color(0xFF25292A), Color(0xFF2B282D), Color(0xFF292A25))
    return colors[(name.hashCode().ushr(1) % colors.size)]
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
private fun CategoryContent(channels: List<Channel>, model: MainViewModel) {
    ScreenHeading("Browse", "Explore channels by category")
    Spacer(Modifier.height(22.dp))
    val groups = channels.groupBy { it.group.ifBlank { "Other" } }.toList()
    if (groups.isEmpty()) {
        EmptyState("Nothing to browse", "Add a playlist to see your channel categories.", "Add playlist") { model.selectTab("Settings") }
    } else {
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            gridItems(groups, key = { it.first }) { (name, items) ->
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(stiffness = 360f), label = "categoryScale")
                Column(Modifier.scale(scale).fillMaxWidth().height(160.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(channelGradient(name))).onFocusChanged { focused = it.isFocused }
                    .clickable { model.selectTab("Live TV"); model.setSearchQuery(name) }.focusable().padding(18.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(name, color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${items.size} channels", color = White.copy(alpha = .62f), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchContent(state: TvState, model: MainViewModel) {
    ScreenHeading("Search", "Find a channel or category")
    Spacer(Modifier.height(16.dp))
    GlassInput(state.searchQuery, "Search channels", model::setSearchQuery, Modifier.fillMaxWidth(.62f))
    Spacer(Modifier.height(16.dp))
    val q = state.searchQuery.trim()
    val matches = if (q.isEmpty()) emptyList() else state.channels.filter { it.name.contains(q, true) || it.group.contains(q, true) || it.country.contains(q, true) }
    Text(if (q.isEmpty()) "Start typing to search" else "${matches.size} results", color = Muted, fontSize = 17.sp)
    Spacer(Modifier.height(14.dp))
    ChannelGrid(matches, state.programs, state.favorites, model)
}

@Composable
private fun SettingsContent(state: TvState, model: MainViewModel) {
    var section by remember { mutableStateOf("Playlist") }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(270.dp)) {
            Text("Settings", color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(26.dp))
            listOf("Playback", "Playlist", "Language", "Parental control", "TV guide", "Appearance", "About").forEach { item ->
                FocusRow(item, if (item == section) "●" else "", item == section) { section = item }
            }
        }
        Spacer(Modifier.width(44.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(section, color = White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            when (section) {
                "Playlist" -> {
                    SettingsRow("M3U playlist URL", "Paste your playlist address") {
                        GlassInput(state.playlistUrl, "https://…", model::setUrl, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(14.dp))
                    WhiteButton(if (state.loading) "Loading…" else "Load playlist") { if (!state.loading) model.loadPlaylist() }
                    state.error?.let { Text(it, color = Color(0xFFFF8B7B), fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp)) }
                    Text("${state.channels.size} channels loaded", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp))
                }
                "TV guide" -> {
                    SettingsRow("XMLTV guide URL", "Optional programme guide") {
                        GlassInput(state.epgUrl, "https://…", model::setEpgUrl, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(14.dp))
                    WhiteButton("Load TV guide") { model.loadEpg() }
                    Text("${state.programs.size} programmes loaded", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp))
                }
                "Playback" -> SettingsRow("Playback engine", "Automatic · HLS / DASH")
                "Language" -> SettingsRow("Interface language", "System default")
                "Parental control" -> SettingsRow("Parental lock", "Not configured")
                "Appearance" -> SettingsRow("Theme", "Dark · tvOS inspired")
                else -> SettingsRow("streamtv", "Version 1.0.0")
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(White.copy(alpha = .07f)).padding(20.dp)) {
        Text(title, color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun FocusRow(title: String, trailing: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, spring(stiffness = 430f), label = "rowScale")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).scale(scale).clip(RoundedCornerShape(15.dp))
        .background(if (focused) White else if (selected) White.copy(alpha = .10f) else Color.Transparent)
        .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable()
        .padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = if (focused) Ink else if (selected) White else White.copy(alpha = .58f), fontSize = 19.sp, fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal)
        Text(trailing, color = if (focused) Ink.copy(alpha = .55f) else Muted, fontSize = 16.sp)
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column {
        Text(title, color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
        Text(subtitle, color = Muted, fontSize = 17.sp)
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
            Text(title, color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Muted, fontSize = 19.sp)
            Spacer(Modifier.height(22.dp))
            WhiteButton(button, onClick)
        }
    }
}

@Composable
private fun WhiteButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(stiffness = 430f), label = "buttonScale")
    Box(Modifier.scale(scale).clip(CircleShape).background(White).onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick).focusable().padding(horizontal = 26.dp, vertical = 14.dp)) {
        Text(text, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlassCircle(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.size(58.dp).scale(if (focused) 1.06f else 1f).clip(CircleShape).background(Glass)
        .border(1.dp, White.copy(alpha = .12f), CircleShape).onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick).focusable(), contentAlignment = Alignment.Center) {
        Text(text, color = if (text == "★") Accent else White, fontSize = 25.sp)
    }
}

@Composable
private fun GlassInput(value: String, hint: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(Glass).border(1.dp, White.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(value = value, onValueChange = onValueChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 18.sp), modifier = Modifier.fillMaxWidth(), decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = Muted, fontSize = 18.sp)
            inner()
        })
    }
}

private fun currentProgram(channel: Channel, programs: List<EpgProgram>): EpgProgram? {
    val now = System.currentTimeMillis()
    return programs.firstOrNull { (it.channelId == channel.id || it.channelId.equals(channel.name, true)) && it.startEpochMillis <= now && it.endEpochMillis > now }
}
