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
    private val languageKey = stringPreferencesKey("interface_language")
    private val recentKey = stringPreferencesKey("recent_channel_keys")
    private val hiddenKey = stringSetPreferencesKey("hidden_channel_keys")
    private val lockedKey = stringSetPreferencesKey("locked_channel_keys")
    private val pinKey = stringPreferencesKey("parental_pin")
    private val namesKey = stringPreferencesKey("channel_names")
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
    val language: Flow<String> = context.dataStore.data.map { it[languageKey] ?: "en" }
    suspend fun saveLanguage(code: String) { context.dataStore.edit { it[languageKey] = code } }
    val hiddenKeys: Flow<Set<String>> = context.dataStore.data.map { it[hiddenKey] ?: emptySet() }
    suspend fun saveHidden(keys: Set<String>) { context.dataStore.edit { it[hiddenKey] = keys } }
    val lockedKeys: Flow<Set<String>> = context.dataStore.data.map { it[lockedKey] ?: emptySet() }
    suspend fun saveLocked(keys: Set<String>) { context.dataStore.edit { it[lockedKey] = keys } }
    val pin: Flow<String> = context.dataStore.data.map { it[pinKey].orEmpty() }
    suspend fun savePin(value: String) { context.dataStore.edit { it[pinKey] = value } }
    /** Custom channel names, one "key<TAB>name" per line. */
    val channelNames: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs[namesKey].orEmpty().split("\n").mapNotNull { line ->
            val cut = line.lastIndexOf('\t')
            if (cut <= 0) null else line.substring(0, cut) to line.substring(cut + 1)
        }.toMap()
    }
    suspend fun saveChannelNames(names: Map<String, String>) {
        context.dataStore.edit { it[namesKey] = names.entries.joinToString("\n") { (key, name) -> key + "\t" + name } }
    }
    suspend fun saveLastChannel(id: String) { context.dataStore.edit { it[lastChannelKey] = id } }
}
