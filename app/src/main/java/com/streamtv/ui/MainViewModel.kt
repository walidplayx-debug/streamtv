package com.streamtv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamtv.data.Channel
import com.streamtv.data.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TvState(
    val playlistUrl: String = "", val channels: List<Channel> = emptyList(),
    val selected: Channel? = null, val loading: Boolean = false, val error: String? = null,
    val favorites: Set<String> = emptySet(), val tab: String = "Home"
)
class MainViewModel : ViewModel() {
    private val repository = PlaylistRepository()
    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state
    fun setUrl(value: String) { _state.value = _state.value.copy(playlistUrl = value) }
    fun selectTab(value: String) { _state.value = _state.value.copy(tab = value) }
    fun select(channel: Channel) { _state.value = _state.value.copy(selected = channel) }
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
            }.onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load playlist") }
        }
    }
}
