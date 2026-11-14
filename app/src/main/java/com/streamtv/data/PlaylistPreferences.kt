package com.streamtv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "streamtv_settings")
class PlaylistPreferences(private val context: Context) {
    private val playlistKey = stringPreferencesKey("playlist_url")
    private val epgKey = stringPreferencesKey("epg_url")
    private val favoritesKey = stringSetPreferencesKey("favorite_ids")
    private val playlistsKey = stringSetPreferencesKey("saved_playlists")
    private val lastChannelKey = stringPreferencesKey("last_channel_id")
    private val recentKey = stringPreferencesKey("recent_channel_keys")
    val playlistUrl: Flow<String> = context.dataStore.data.map { it[playlistKey].orEmpty() }
    val epgUrl: Flow<String> = context.dataStore.data.map { it[epgKey].orEmpty() }
    val favoriteIds: Flow<Set<String>> = context.dataStore.data.map { it[favoritesKey] ?: emptySet() }
    val savedPlaylists: Flow<Set<String>> = context.dataStore.data.map { it[playlistsKey] ?: emptySet() }
    val lastChannelId: Flow<String> = context.dataStore.data.map { it[lastChannelKey].orEmpty() }
    suspend fun savePlaylist(url: String) { context.dataStore.edit { it[playlistKey] = url } }
    suspend fun saveEpg(url: String) { context.dataStore.edit { it[epgKey] = url } }
    suspend fun saveFavorites(ids: Set<String>) { context.dataStore.edit { it[favoritesKey] = ids } }
    suspend fun savePlaylists(entries: Set<String>) { context.dataStore.edit { it[playlistsKey] = entries } }
    val recentKeys: Flow<List<String>> = context.dataStore.data.map { it[recentKey].orEmpty().split("\n").filter { key -> key.isNotBlank() } }
    suspend fun saveRecent(keys: List<String>) { context.dataStore.edit { it[recentKey] = keys.joinToString("\n") } }
    suspend fun saveLastChannel(id: String) { context.dataStore.edit { it[lastChannelKey] = id } }
}
