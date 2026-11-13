package com.streamtv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamtv.data.Channel
import com.streamtv.data.PlaylistRepository
import com.streamtv.data.StreamDatabase
import com.streamtv.data.ChannelEntity
import com.streamtv.data.PlaylistPreferences
import com.streamtv.data.M3uParser
import com.streamtv.data.EpgProgram
import com.streamtv.data.EpgRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PlaylistEntry(val name: String, val url: String)

data class TvState(
    val playlistUrl: String = "", val playlistName: String = "", val playlists: List<PlaylistEntry> = emptyList(),
    val epgUrl: String = "", val programs: List<EpgProgram> = emptyList(), val channels: List<Channel> = emptyList(),
    val selected: Channel? = null, val loading: Boolean = false, val error: String? = null,
    val favorites: Set<String> = emptySet(), val tab: String = "Home", val playing: Boolean = false, val searchQuery: String = ""
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository()
    private val epgRepository = EpgRepository()
    private val dao = StreamDatabase.get(application).channelDao()
    private val preferences = PlaylistPreferences(application)
    private val playlistDirectory = File(application.filesDir, "playlists")
    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state
    init {
        viewModelScope.launch {
            preferences.playlistUrl.collect { saved ->
                if (_state.value.playlistUrl.isBlank()) {
                    val initial = saved.ifBlank { "https://iptv-org.github.io/iptv/countries/be.m3u" }
                    _state.value = _state.value.copy(playlistUrl = initial)
                    if (saved.isBlank()) preferences.savePlaylist(initial)
                    if (_state.value.channels.isEmpty()) loadPlaylist()
                }
            }
        }
        viewModelScope.launch {
            dao.observeAll().collect { cached ->
                if (_state.value.channels.isEmpty() && cached.isNotEmpty()) {
                    val channels = cached.map { it.toChannel() }
                    _state.value = _state.value.copy(channels = channels, selected = channels.firstOrNull())
                }
            }
        }
    }
    init {
        viewModelScope.launch {
            preferences.epgUrl.collect { saved -> if (saved.isNotBlank() && _state.value.epgUrl.isBlank()) _state.value = _state.value.copy(epgUrl = saved) }
        }
    }
    init {
        viewModelScope.launch {
            preferences.favoriteIds.collect { ids -> _state.value = _state.value.copy(favorites = ids) }
        }
    }
    init {
        viewModelScope.launch {
            preferences.savedPlaylists.collect { saved ->
                val entries = saved.mapNotNull { raw ->
                    val split = raw.split("\t", limit = 2)
                    if (split.size == 2) PlaylistEntry(split[0], split[1]) else null
                }.sortedBy { it.name.lowercase() }
                _state.value = _state.value.copy(playlists = entries)
            }
        }
    }

    fun setPlaylistName(value: String) { _state.value = _state.value.copy(playlistName = value) }
    fun setError(value: String) { _state.value = _state.value.copy(error = value) }
    fun importPlaylist(text: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val channels = M3uParser.parse(text).also {
                    require(it.isNotEmpty()) { "The selected file contains no playable M3U channels" }
                }
                val name = _state.value.playlistName.trim().ifBlank { "Imported playlist ${_state.value.playlists.size + 1}" }
                val fileName = "import-${System.currentTimeMillis()}.m3u"
                playlistDirectory.mkdirs()
                File(playlistDirectory, fileName).writeText(text)
                Triple(channels, name, fileName)
            }
                .onSuccess { (channels, name, fileName) ->
                    val localUrl = "local://$fileName"
                    val updated = (_state.value.playlists.filterNot { it.url == localUrl } + PlaylistEntry(name, localUrl))
                    _state.value = _state.value.copy(
                        channels = channels, selected = channels.firstOrNull(), loading = false,
                        tab = "Live TV", playlistName = name, playlistUrl = localUrl, playlists = updated
                    )
                    preferences.savePlaylists(updated.map { "${it.name}\t${it.url}" }.toSet())
                    dao.clear()
                    dao.insertAll(channels.map { ChannelEntity.from(it) })
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to import playlist") }
        }
    }
    fun setEpgUrl(value: String) { _state.value = _state.value.copy(epgUrl = value) }
    fun savePlaylistEntry() {
        val name = _state.value.playlistName.trim().ifBlank { "Playlist ${_state.value.playlists.size + 1}" }
        val url = _state.value.playlistUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.value = _state.value.copy(error = "Enter a valid http(s) playlist URL")
            return
        }
        val updated = (_state.value.playlists.filterNot { it.url == url } + PlaylistEntry(name, url))
        _state.value = _state.value.copy(playlists = updated, playlistName = name)
        viewModelScope.launch { preferences.savePlaylists(updated.map { "${it.name}\t${it.url}" }.toSet()) }
    }
    fun removePlaylist(entry: PlaylistEntry) {
        val updated = _state.value.playlists.filterNot { it.url == entry.url }
        _state.value = _state.value.copy(playlists = updated)
        viewModelScope.launch {
            preferences.savePlaylists(updated.map { "${it.name}\t${it.url}" }.toSet())
            if (entry.url.startsWith("local://")) {
                File(playlistDirectory, entry.url.removePrefix("local://")).delete()
            }
        }
    }
    fun selectPlaylist(entry: PlaylistEntry) {
        _state.value = _state.value.copy(playlistName = entry.name, playlistUrl = entry.url)
        if (!entry.url.startsWith("local://")) {
            loadPlaylist(entry.url)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                M3uParser.parse(File(playlistDirectory, entry.url.removePrefix("local://")).readText())
                    .also { require(it.isNotEmpty()) { "The saved M3U file contains no playable channels" } }
            }.onSuccess { channels ->
                _state.value = _state.value.copy(channels = channels, selected = channels.firstOrNull(), loading = false, tab = "Live TV")
                dao.clear()
                dao.insertAll(channels.map { ChannelEntity.from(it) })
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load saved playlist")
            }
        }
    }
    fun loadEpg() {
        val url = _state.value.epgUrl
        viewModelScope.launch {
            runCatching { epgRepository.load(url) }.onSuccess { items ->
                _state.value = _state.value.copy(programs = items)
                preferences.saveEpg(url)
            }.onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Unable to load EPG") }
        }
    }
    fun setSearchQuery(value: String) { _state.value = _state.value.copy(searchQuery = value) }
    fun setUrl(value: String) { _state.value = _state.value.copy(playlistUrl = value) }
    fun selectTab(value: String) { _state.value = _state.value.copy(tab = value) }
    fun select(channel: Channel) { _state.value = _state.value.copy(selected = channel, playing = true) }
    fun stopPlaying() { _state.value = _state.value.copy(playing = false) }
    fun zap(delta: Int) {
        val channels = _state.value.channels
        if (channels.isEmpty()) return
        val current = channels.indexOfFirst { it.id == _state.value.selected?.id }.let { if (it < 0) 0 else it }
        val next = (current + delta + channels.size) % channels.size
        _state.value = _state.value.copy(selected = channels[next])
    }
    fun toggleFavorite(channel: Channel) {
        val f = _state.value.favorites.toMutableSet()
        if (!f.add(channel.id)) f.remove(channel.id)
        _state.value = _state.value.copy(favorites = f)
        viewModelScope.launch { preferences.saveFavorites(f) }
    }
    fun loadPlaylist(urlOverride: String? = null) {
        val url = (urlOverride ?: _state.value.playlistUrl).trim()
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.load(url) }.onSuccess { channels ->
                _state.value = _state.value.copy(channels = channels, loading = false, selected = channels.firstOrNull())
                preferences.savePlaylist(url)
                dao.clear()
                dao.insertAll(channels.map { ChannelEntity.from(it) })
            }.onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load playlist") }
        }
    }
}