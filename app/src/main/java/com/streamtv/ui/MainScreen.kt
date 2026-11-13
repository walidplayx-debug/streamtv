package com.streamtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamtv.data.Channel
import com.streamtv.data.EpgProgram

@Composable
fun MainScreen(state: TvState, model: MainViewModel) {
    if (state.playing && state.selected != null) {
        PlayerScreen(state.selected, model::stopPlaying)
        return
    }
    Row(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF17131B), Color(0xFF07070B)))).padding(28.dp)) {
        Column(Modifier.width(210.dp).fillMaxHeight()) {
            Text("streamtv", fontSize = 30.sp, color = Color(0xFFFF8A36))
            Spacer(Modifier.height(28.dp))
            listOf("Home", "Live TV", "Categories", "Favorites", "Search", "Settings").forEach { tab ->
                Surface(
                    color = if (state.tab == tab) Color(0x33FF7A32) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { model.selectTab(tab) }.focusable()
                ) { Text(tab, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), fontSize = 18.sp) }
            }
            Spacer(Modifier.weight(1f))
            Text("Your playlist. Your TV.", color = Color.LightGray, fontSize = 12.sp)
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            when (state.tab) {
                "Settings" -> SettingsContent(state, model)
                "Categories" -> CategoryContent(state.channels, model)
                "Search" -> SearchContent(state, model)
                "Favorites" -> ChannelList(state.channels.filter { it.id in state.favorites }, model, state.favorites, state.programs)
                else -> {
                    Text(if (state.tab == "Home") "Welcome back" else "Live TV", fontSize = 30.sp)
                    Spacer(Modifier.height(18.dp))
                    if (state.channels.isEmpty()) SetupCard(state, model)
                    else {
                        Text("${state.channels.size} channels available", color = Color.LightGray)
                        Spacer(Modifier.height(18.dp))
                        ChannelList(state.channels, model, state.favorites, state.programs)
                    }
                }
            }
        }
    }
}

@Composable private fun SetupCard(state: TvState, model: MainViewModel) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF19171D), RoundedCornerShape(26.dp)).padding(28.dp)) {
        Text("Add your playlist", fontSize = 25.sp)
        Spacer(Modifier.height(8.dp))
        Text("Paste your M3U / M3U8 link. You can add an optional XMLTV guide from Settings.", color = Color.LightGray)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = state.playlistUrl, onValueChange = model::setUrl, label = { Text("Playlist URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = model::loadPlaylist, enabled = !state.loading && state.playlistUrl.isNotBlank()) {
            Text(if (state.loading) "Loading…" else "Load channels")
        }
        state.error?.let { Text(it, color = Color(0xFFFF806F), modifier = Modifier.padding(top = 12.dp)) }
    }
}


@Composable private fun SearchContent(state: TvState, model: MainViewModel) {
    Text("Search channels", fontSize = 30.sp)
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(value = state.searchQuery, onValueChange = model::setSearchQuery, label = { Text("Channel name or category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    val query = state.searchQuery.trim()
    val matches = if (query.isEmpty()) emptyList() else state.channels.filter {
        it.name.contains(query, true) || it.group.contains(query, true) || it.country.contains(query, true)
    }
    Text("${matches.size} matches", color = Color.LightGray)
    Spacer(Modifier.height(8.dp))
    ChannelList(matches, model, state.favorites, state.programs)
}

@Composable private fun SettingsContent(state: TvState, model: MainViewModel) {
    Text("Settings", fontSize = 30.sp)
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(value = state.playlistUrl, onValueChange = model::setUrl, label = { Text("M3U playlist URL") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Button(onClick = model::loadPlaylist, enabled = state.playlistUrl.isNotBlank() && !state.loading) { Text("Refresh playlist") }
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(value = state.epgUrl, onValueChange = model::setEpgUrl, label = { Text("Optional XMLTV / XMLTV.GZ URL") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Button(onClick = model::loadEpg, enabled = state.epgUrl.isNotBlank()) { Text("Load TV guide") }
    state.error?.let { Text(it, color = Color(0xFFFF806F)) }
    Text("${state.programs.size} guide programmes loaded", color = Color.LightGray)
}

@Composable private fun CategoryContent(channels: List<Channel>, model: MainViewModel) {
    Text("Categories", fontSize = 30.sp)
    Spacer(Modifier.height(16.dp))
    val groups = channels.groupBy { it.group }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups.keys.toList()) { group ->
            Row(Modifier.fillMaxWidth().background(Color(0xFF1A181F), RoundedCornerShape(14.dp)).clickable { model.selectTab("Live TV") }.padding(16.dp)) {
                Text(group, modifier = Modifier.weight(1f))
                Text("${groups[group]?.size ?: 0} channels", color = Color.LightGray)
            }
        }
    }
}

@Composable private fun ChannelList(channels: List<Channel>, model: MainViewModel, favorites: Set<String>, programs: List<EpgProgram>) {
    if (channels.isEmpty()) Text("Nothing here yet. Add a playlist in Home.", color = Color.LightGray)
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(channels, key = { it.id }) { channel ->
            Row(Modifier.fillMaxWidth().background(Color(0xFF1A181F), RoundedCornerShape(16.dp))
                .clickable { model.select(channel) }.focusable().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(channel.name, fontSize = 18.sp)
                    Text(channel.group, color = Color.LightGray, fontSize = 13.sp)
                    val now = System.currentTimeMillis()
                    val current = programs.firstOrNull { (it.channelId == channel.id || it.channelId.equals(channel.name, true)) && it.startEpochMillis <= now && it.endEpochMillis > now }
                    val next = programs.filter { (it.channelId == channel.id || it.channelId.equals(channel.name, true)) && it.startEpochMillis > now }.minByOrNull { it.startEpochMillis }
                    if (current != null) Text("NOW · ${current.title}", color = Color(0xFFFFB27A), fontSize = 12.sp)
                    else if (next != null) Text("NEXT · ${next.title}", color = Color.LightGray, fontSize = 12.sp)
                }
                Text(if (channel.id in favorites) "★" else "☆", color = Color(0xFFFF8A36), fontSize = 24.sp, modifier = Modifier.clickable { model.toggleFavorite(channel) }.padding(horizontal = 16.dp))
                Text("▶", color = Color(0xFFFF8A36), fontSize = 20.sp)
            }
        }
    }
}
