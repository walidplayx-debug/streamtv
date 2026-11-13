package com.streamtv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamtv.data.Channel
import com.streamtv.data.PlaylistRepository
import com.streamtv.data.StreamDatabase
import com.streamtv.data.ChannelEntity
import com.streamtv.data.PlaylistPreferences
import com.streamtv.data.EpgProgram
import com.streamtv.data.EpgRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TvState(
    val playlistUrl: String = "", val epgUrl: String = "", val programs: List<EpgProgram> = emptyList(), val channels: List<Channel> = emptyList(),
    val selected: Channel? = null, val loading: Boolean = false, val error: String? = null,
    val favorites: Set<String> = emptySet(), val tab: String = "Home", val playing: Boolean = false, val searchQuery: String = ""
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository()
    private val epgRepository = EpgRepository()
    private val dao = StreamDatabase.get(application).channelDao()
    private val preferences = PlaylistPreferences(application)
    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state
    init {
        viewModelScope.launch {
            preferences.playlistUrl.collect { saved ->
                if (_state.value.playlistUrl.isBlank()) {\n                    val initial = saved.ifBlank { "https://iptv-org.github.io/iptv/countries/be.m3u" }\n                    _state.value = _state.value.copy(playlistUrl = initial)\n                    if (saved.isBlank()) preferences.savePlaylist(initial)\n                    if (_state.value.channels.isEmpty()) loadPlaylist()\n                }
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
    fun setEpgUrl(value: String) { _state.value = _state.value.copy(epgUrl = value) }
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
    fun loadPlaylist() {
        val url = _state.value.playlistUrl
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
