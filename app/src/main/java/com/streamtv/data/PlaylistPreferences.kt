package com.streamtv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "streamtv_settings")
class PlaylistPreferences(private val context: Context) {
    private val playlistKey = stringPreferencesKey("playlist_url")
    private val epgKey = stringPreferencesKey("epg_url")
    val playlistUrl: Flow<String> = context.dataStore.data.map { it[playlistKey].orEmpty() }
    val epgUrl: Flow<String> = context.dataStore.data.map { it[epgKey].orEmpty() }
    suspend fun savePlaylist(url: String) { context.dataStore.edit { it[playlistKey] = url } }
    suspend fun saveEpg(url: String) { context.dataStore.edit { it[epgKey] = url } }
}
