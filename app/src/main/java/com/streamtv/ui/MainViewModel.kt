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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class PlaylistEntry(val name: String, val url: String)

/** A PIN question on screen. purpose: "set", "open", "unlock", "verify" or "confirm". */
data class PinPrompt(val purpose: String, val channel: Channel? = null, val from: List<Channel>? = null, val title: String? = null)

data class TvState(
    val playlistUrl: String = "", val playlistName: String = "", val playlists: List<PlaylistEntry> = emptyList(),
    val epgUrl: String = "", val programs: List<EpgProgram> = emptyList(), val channels: List<Channel> = emptyList(),
    val selected: Channel? = null, val loading: Boolean = false, val error: String? = null,
    val favorites: Set<String> = emptySet(), val tab: String = "Live TV", val playing: Boolean = false, val searchQuery: String = "",
    /** The list the playing channel was opened from; the player browses and zaps inside it. */
    val queue: List<Channel> = emptyList(),
    /** Name of that list, shown as the heading of the in-player channel list. */
    val queueTitle: String = "All channels",
    /** Keys of recently watched channels, newest first. */
    val recent: List<String> = emptyList(),
    /** True once the saved settings were read, so first-run screens do not flash. */
    val ready: Boolean = false,
    /** Interface language: "en", "fr" or "ar". */
    val language: String = "en",
    /** Channels the viewer removed from every list (by key). */
    val hidden: Set<String> = emptySet(),
    /** Channels that need the PIN before they play (by key). */
    val locked: Set<String> = emptySet(),
    /** Names the viewer gave to channels (key -> name). */
    val names: Map<String, String> = emptyMap(),
    val pinSet: Boolean = false,
    val pin: PinPrompt? = null,
    val pinError: Boolean = false,
    /** What is typed in the Search tab; separate from the Live TV list choice (searchQuery). */
    val searchText: String = ""
)

/** Applies the viewer's hidden channels and custom names to a list, remembering the last result. */
private class ChannelView {
    private var list: List<Channel>? = null
    private var hidden: Set<String>? = null
    private var names: Map<String, String>? = null
    private var result: List<Channel> = emptyList()
    fun of(list: List<Channel>, hidden: Set<String>, names: Map<String, String>): List<Channel> {
        if (list === this.list && hidden === this.hidden && names === this.names) return result
        this.list = list; this.hidden = hidden; this.names = names
        result = if (hidden.isEmpty() && names.isEmpty()) list else list.mapNotNull { channel ->
            val key = channel.key
            if (key in hidden) null else names[key]?.let { channel.copy(name = it) } ?: channel
        }
        return result
    }
}
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository()
    private val epgRepository = EpgRepository()
    private val dao = StreamDatabase.get(application).channelDao()
    private val preferences = PlaylistPreferences(application)
    private val playlistDirectory = File(application.filesDir, "playlists")
    private var lastChannelId: String = ""
    private val _state = MutableStateFlow(TvState())
    private val channelsView = ChannelView()
    private val queueView = ChannelView()
    private var pinCode: String = ""
    private var pendingAction: (() -> Unit)? = null
    /** What the screens see: the raw state with hidden channels removed and custom names applied. */
    val state: StateFlow<TvState> = _state.map { raw ->
        raw.copy(
            channels = channelsView.of(raw.channels, raw.hidden, raw.names),
            queue = queueView.of(raw.queue, raw.hidden, raw.names),
            selected = raw.selected?.let { channel -> raw.names[channel.key]?.let { channel.copy(name = it) } ?: channel }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TvState())
    init {
        viewModelScope.launch { preferences.hiddenKeys.collect { keys -> _state.value = _state.value.copy(hidden = keys) } }
        viewModelScope.launch { preferences.lockedKeys.collect { keys -> _state.value = _state.value.copy(locked = keys) } }
        viewModelScope.launch { preferences.channelNames.collect { names -> _state.value = _state.value.copy(names = names) } }
        viewModelScope.launch { preferences.pin.collect { value -> pinCode = value; _state.value = _state.value.copy(pinSet = value.isNotBlank()) } }
    }
    init {
        viewModelScope.launch {
            preferences.playlistUrl.collect { saved ->
                // No playlist is bundled: a fresh install shows the welcome screen instead.
                if (_state.value.playlistUrl.isBlank() && saved.isNotBlank()) {
                    _state.value = _state.value.copy(playlistUrl = saved)
                    if (_state.value.channels.isEmpty()) loadPlaylist()
                }
                _state.value = _state.value.copy(ready = true)
            }
        }
        viewModelScope.launch {
            dao.observeAll().collect { cached ->
                if (_state.value.channels.isEmpty() && cached.isNotEmpty()) {
                    val channels = cached.map { it.toChannel() }
                    _state.value = _state.value.copy(channels = channels, selected = channels.firstOrNull { it.id == lastChannelId } ?: channels.firstOrNull())
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
            preferences.lastChannelId.collect { id ->
                lastChannelId = id
                val last = _state.value.channels.firstOrNull { it.id == id }
                if (last != null) _state.value = _state.value.copy(selected = last)
            }
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

    init {
        viewModelScope.launch {
            preferences.recentKeys.collect { keys -> _state.value = _state.value.copy(recent = keys) }
        }
    }
    init {
        viewModelScope.launch {
            preferences.language.collect { code -> _state.value = _state.value.copy(language = code) }
        }
    }
    fun setLanguage(code: String) {
        _state.value = _state.value.copy(language = code)
        viewModelScope.launch { preferences.saveLanguage(code) }
    }
    private fun rememberWatched(channel: Channel) {
        val updated = (listOf(channel.key) + _state.value.recent.filterNot { it == channel.key }).take(20)
        _state.value = _state.value.copy(recent = updated)
        viewModelScope.launch { preferences.saveRecent(updated) }
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
                        channels = channels, selected = channels.firstOrNull { it.id == lastChannelId } ?: channels.firstOrNull(), loading = false,
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
                _state.value = _state.value.copy(channels = channels, selected = channels.firstOrNull { it.id == lastChannelId } ?: channels.firstOrNull(), loading = false, tab = "Live TV")
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
    fun setSearchText(value: String) { _state.value = _state.value.copy(searchText = value) }
    fun setSearchQuery(value: String) { _state.value = _state.value.copy(searchQuery = value) }
    fun setUrl(value: String) { _state.value = _state.value.copy(playlistUrl = value) }
    fun selectTab(value: String) { _state.value = _state.value.copy(tab = value) }
    fun select(channel: Channel, from: List<Channel>? = null, title: String? = null) {
        // A locked channel asks for the PIN first.
        if (channel.key in _state.value.locked) {
            _state.value = _state.value.copy(pin = PinPrompt("open", channel, from, title), pinError = false)
            return
        }
        play(channel, from, title)
    }
    private fun play(channel: Channel, from: List<Channel>?, title: String?) {
        lastChannelId = channel.id
        val current = _state.value.queue
        val queue = from ?: if (current.any { it.key == channel.key }) current else _state.value.channels
        _state.value = _state.value.copy(
            selected = channel, playing = true, queue = queue,
            queueTitle = title ?: if (from == null && queue === current) _state.value.queueTitle else "All channels"
        )
        rememberWatched(channel)
        viewModelScope.launch { preferences.saveLastChannel(channel.id) }
    }
    fun stopPlaying() { _state.value = _state.value.copy(playing = false) }
    fun zap(delta: Int) {
        val channels = _state.value.queue.ifEmpty { _state.value.channels }
        if (channels.isEmpty()) return
        val current = channels.indexOfFirst { it.key == _state.value.selected?.key }.let { if (it < 0) 0 else it }
        // Zapping skips channels that are hidden or locked.
        val skip = _state.value.hidden + _state.value.locked
        var next = current
        var selected: Channel? = null
        for (step in 1..channels.size) {
            next = (next + delta + channels.size) % channels.size
            if (channels[next].key !in skip) { selected = channels[next]; break }
        }
        if (selected == null || selected.key == _state.value.selected?.key) return
        lastChannelId = selected.id
        _state.value = _state.value.copy(selected = selected)
        rememberWatched(selected)
        viewModelScope.launch { preferences.saveLastChannel(selected.id) }
    }
    fun hideChannel(channel: Channel) {
        val hidden = _state.value.hidden + channel.key
        _state.value = _state.value.copy(hidden = hidden)
        viewModelScope.launch { preferences.saveHidden(hidden) }
        if (_state.value.playing && _state.value.selected?.key == channel.key) {
            zap(1)
            if (_state.value.selected?.key == channel.key) stopPlaying()
        }
    }
    fun restoreHidden() {
        _state.value = _state.value.copy(hidden = emptySet())
        viewModelScope.launch { preferences.saveHidden(emptySet()) }
    }
    fun renameChannel(channel: Channel, name: String) {
        val clean = name.trim().replace("\t", " ").replace("\n", " ")
        val names = if (clean.isBlank()) _state.value.names - channel.key else _state.value.names + (channel.key to clean)
        _state.value = _state.value.copy(names = names)
        viewModelScope.launch { preferences.saveChannelNames(names) }
    }
    fun toggleLock(channel: Channel) {
        when {
            channel.key in _state.value.locked -> _state.value = _state.value.copy(pin = PinPrompt("unlock", channel), pinError = false)
            pinCode.isBlank() -> _state.value = _state.value.copy(pin = PinPrompt("set", channel), pinError = false)
            else -> setLocked(_state.value.locked + channel.key)
        }
    }
    private fun setLocked(keys: Set<String>) {
        _state.value = _state.value.copy(locked = keys)
        viewModelScope.launch { preferences.saveLocked(keys) }
    }
    fun changePin() {
        _state.value = _state.value.copy(pin = PinPrompt(if (pinCode.isBlank()) "set" else "verify"), pinError = false)
    }
    /** Runs [action] straight away when no PIN is set, otherwise only after the PIN is entered. */
    fun protect(action: () -> Unit) {
        if (pinCode.isBlank()) { action(); return }
        pendingAction = action
        _state.value = _state.value.copy(pin = PinPrompt("confirm"), pinError = false)
    }
    fun cancelPin() {
        pendingAction = null
        _state.value = _state.value.copy(pin = null, pinError = false)
    }
    fun submitPin(value: String) {
        val prompt = _state.value.pin ?: return
        if (prompt.purpose == "set") {
            if (!Regex("\\d{4}").matches(value)) { _state.value = _state.value.copy(pinError = true); return }
            pinCode = value
            _state.value = _state.value.copy(pin = null, pinError = false, pinSet = true)
            viewModelScope.launch { preferences.savePin(value) }
            prompt.channel?.let { setLocked(_state.value.locked + it.key) }
            return
        }
        if (value != pinCode) { _state.value = _state.value.copy(pinError = true); return }
        _state.value = _state.value.copy(pin = null, pinError = false)
        when (prompt.purpose) {
            "open" -> prompt.channel?.let { play(it, prompt.from, prompt.title) }
            "unlock" -> prompt.channel?.let { setLocked(_state.value.locked - it.key) }
            "verify" -> _state.value = _state.value.copy(pin = PinPrompt("set"))
            "confirm" -> { val action = pendingAction; pendingAction = null; action?.invoke() }
        }
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