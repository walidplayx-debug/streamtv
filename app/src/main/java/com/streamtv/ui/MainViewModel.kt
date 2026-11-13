package com.streamtv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamtv.data.Channel
import com.streamtv.data.PlaylistRepository
import com.streamtv.data.StreamDatabase
import com.streamtv.data.ChannelEntity
import com.streamtv.data.PlaylistPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TvState(
    val playlistUrl: String = "", val channels: List<Channel> = emptyList(),
    val selected: Channel? = null, val loading: Boolean = false, val error: String? = null,
    val favorites: Set<String> = emptySet(), val tab: String = "Home", val playing: Boolean = false
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository()
    private val dao = StreamDatabase.get(application).channelDao()
    private val preferences = PlaylistPreferences(application)
    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state
    init {
        viewModelScope.launch {
            preferences.playlistUrl.collect { saved ->
                if (saved.isNotBlank() && _state.value.playlistUrl.isBlank()) _state.value = _state.value.copy(playlistUrl = saved)
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
    fun setUrl(value: String) { _state.value = _state.value.copy(playlistUrl = value) }
    fun selectTab(value: String) { _state.value = _state.value.copy(tab = value) }
    fun select(channel: Channel) { _state.value = _state.value.copy(selected = channel, playing = true) }
    fun stopPlaying() { _state.value = _state.value.copy(playing = false) }
    fun toggleFavorite(channel: Channel) {
        val f = _state.value.favorites.toMutableSet()
        if (!f.add(channel.id)) f.remove(channel.id)
        _state.value = _state.value.copy(favorites = f)
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
