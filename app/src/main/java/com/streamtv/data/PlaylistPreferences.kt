package com.streamtv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "streamtv_settings")
class PlaylistPreferences(private val context: Context) {
    private val playlistKey = stringPreferencesKey("playlist_url")
    private val epgKey = stringPreferencesKey("epg_url")
    private val favoritesKey = stringSetPreferencesKey("favorite_ids")
    private val playlistsKey = stringSetPreferencesKey("saved_playlists")
    private val lastChannelKey = stringPreferencesKey("last_channel_id")
    private val languageKey = stringPreferencesKey("interface_language")
    private val groupModeKey = stringPreferencesKey("channel_group_mode")
    private val sectionOrderKey = stringPreferencesKey("section_order")
    private val sectionHiddenKey = stringPreferencesKey("section_hidden")
    private val recentKey = stringPreferencesKey("recent_channel_keys")
    private val hiddenKey = stringSetPreferencesKey("hidden_channel_keys")
    private val lockedKey = stringSetPreferencesKey("locked_channel_keys")
    private val pinKey = stringPreferencesKey("parental_pin")
    private val namesKey = stringPreferencesKey("channel_names")
    private val recentVodKey = stringPreferencesKey("recent_vod_keys")
    private val positionsKey = stringPreferencesKey("vod_positions")
    private val logosKey = stringPreferencesKey("channel_logos")
    val playlistUrl: Flow<String> = context.dataStore.data.map { it[playlistKey].orEmpty() }
    val epgUrl: Flow<String> = context.dataStore.data.map { it[epgKey].orEmpty() }
    val favoriteIds: Flow<Set<String>> = context.dataStore.data.map { it[favoritesKey] ?: emptySet() }
    val savedPlaylists: Flow<Set<String>> = context.dataStore.data.map { it[playlistsKey] ?: emptySet() }
    // Only real changes: the settings store sends every key again whenever any one is saved.
    val lastChannelId: Flow<String> = context.dataStore.data.map { it[lastChannelKey].orEmpty() }.distinctUntilChanged()
    suspend fun savePlaylist(url: String) { context.dataStore.edit { it[playlistKey] = url } }
    /** Which playlist the channel cache holds, and when it was downloaded. */
    private val cacheStampKey = stringPreferencesKey("cache_stamp")
    suspend fun saveCacheStamp(url: String) { context.dataStore.edit { it[cacheStampKey] = url + "\t" + System.currentTimeMillis() } }
    suspend fun cacheStamp(): Pair<String, Long> {
        val raw = context.dataStore.data.first()[cacheStampKey].orEmpty()
        return raw.substringBeforeLast("\t", "") to (raw.substringAfterLast("\t", "").toLongOrNull() ?: 0L)
    }
    suspend fun saveEpg(url: String) { context.dataStore.edit { it[epgKey] = url } }
    suspend fun saveFavorites(ids: Set<String>) { context.dataStore.edit { it[favoritesKey] = ids } }
    suspend fun savePlaylists(entries: Set<String>) { context.dataStore.edit { it[playlistsKey] = entries } }
    val recentKeys: Flow<List<String>> = context.dataStore.data.map { it[recentKey].orEmpty().split("\n").filter { key -> key.isNotBlank() } }
    suspend fun saveRecent(keys: List<String>) { context.dataStore.edit { it[recentKey] = keys.joinToString("\n") } }
    // Until a language is chosen, the TV's own (when the app has it), else English.
    val language: Flow<String> = context.dataStore.data.map { it[languageKey] ?: systemLanguage() }
    private fun systemLanguage(): String =
        java.util.Locale.getDefault().language.takeIf { it in setOf("en", "fr", "ar", "es", "ru", "zh", "tr", "de", "pt", "nl") } ?: "en"
    suspend fun saveLanguage(code: String) { context.dataStore.edit { it[languageKey] = code } }
    val groupMode: Flow<String> = context.dataStore.data.map { it[groupModeKey] ?: "provider" }
    suspend fun saveGroupMode(mode: String) { context.dataStore.edit { it[groupModeKey] = mode } }
    // The side list's own order and hidden entries, one line per grouping mode: "mode<TAB>a<US>b<US>c".
    val sectionOrder: Flow<Map<String, List<String>>> = context.dataStore.data.map { decodeSections(it[sectionOrderKey]) }
    val sectionHidden: Flow<Map<String, List<String>>> = context.dataStore.data.map { decodeSections(it[sectionHiddenKey]) }
    suspend fun saveSectionOrder(map: Map<String, List<String>>) { context.dataStore.edit { it[sectionOrderKey] = encodeSections(map) } }
    suspend fun saveSectionHidden(map: Map<String, List<String>>) { context.dataStore.edit { it[sectionHiddenKey] = encodeSections(map) } }
    private fun encodeSections(map: Map<String, List<String>>) =
        map.filterValues { it.isNotEmpty() }.entries.joinToString("\n") { (mode, tokens) -> mode + "\t" + tokens.joinToString("\u001f") }
    private fun decodeSections(text: String?): Map<String, List<String>> =
        text.orEmpty().lines().filter { '\t' in it }.associate { line -> line.substringBefore('\t') to line.substringAfter('\t').split('\u001f').filter { it.isNotEmpty() } }
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
    /** Logos chosen by the viewer, one "key<TAB>address" per line. */
    val channelLogos: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs[logosKey].orEmpty().split("\n").mapNotNull { line ->
            val cut = line.lastIndexOf('\t')
            if (cut <= 0) null else line.substring(0, cut) to line.substring(cut + 1)
        }.toMap()
    }.distinctUntilChanged()
    suspend fun saveChannelLogos(logos: Map<String, String>) {
        context.dataStore.edit { it[logosKey] = logos.entries.joinToString("\n") { (key, url) -> key + "\t" + url } }
    }
    suspend fun saveLastChannel(id: String) { context.dataStore.edit { it[lastChannelKey] = id } }
    /** Movies and episodes watched, newest first; kept apart from the channels' history. */
    val recentVodKeys: Flow<List<String>> = context.dataStore.data.map { it[recentVodKey].orEmpty().split("\n").filter { key -> key.isNotBlank() } }
    suspend fun saveRecentVod(keys: List<String>) { context.dataStore.edit { it[recentVodKey] = keys.joinToString("\n") } }
    /** Where each movie was left: one "key<TAB>position<TAB>duration" (milliseconds) per line. */
    val positions: Flow<Map<String, Pair<Long, Long>>> = context.dataStore.data.map { prefs ->
        prefs[positionsKey].orEmpty().split("\n").mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val duration = parts[parts.size - 1].toLongOrNull() ?: return@mapNotNull null
            val position = parts[parts.size - 2].toLongOrNull() ?: return@mapNotNull null
            parts.dropLast(2).joinToString("\t") to (position to duration)
        }.toMap()
    }
    suspend fun savePositions(positions: Map<String, Pair<Long, Long>>) {
        context.dataStore.edit { prefs ->
            prefs[positionsKey] = positions.entries.joinToString("\n") { (key, value) -> key + "\t" + value.first + "\t" + value.second }
        }
    }
}
