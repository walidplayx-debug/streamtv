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
import com.streamtv.data.ChannelKind
import com.streamtv.data.kind
import com.streamtv.data.hasFavorite
import com.streamtv.data.LogoIndex
import com.streamtv.data.XtreamAccount
import com.streamtv.data.Season
import com.streamtv.data.ShowDetails
import com.streamtv.data.TitleInfo
import com.streamtv.data.Shows
import com.streamtv.data.isShow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlaylistEntry(val name: String, val url: String)

/**
 * A PIN question on screen. purpose: "set", "repeat" (the new PIN typed a second time), "open",
 * "lock", "unlock", "hide", "verify" or "confirm".
 * [then]: for "set" with a channel, what happens to it once the new PIN is chosen ("lock" or "hide").
 * [mismatch]: the second entry of a new PIN did not match the first; it is chosen again.
 * [label] and [reason]: heading and message of a "confirm" question.
 */
data class PinPrompt(
    val purpose: String, val channel: Channel? = null, val from: List<Channel>? = null, val title: String? = null,
    val then: String = "lock", val mismatch: Boolean = false, val label: String? = null, val reason: String? = null
)

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
    /** The colour theme's id, see [Palettes]. */
    val theme: String = "default",
    /** How the Live TV side list divides channels: "provider", "language", "country" or "genre". */
    val groupMode: String = "provider",
    /** The side list's order chosen by the user, and the entries hidden, per grouping mode. */
    val sectionOrder: Map<String, List<String>> = emptyMap(),
    val sectionHidden: Map<String, List<String>> = emptyMap(),
    /** Channels the viewer removed from every list (by key). */
    val hidden: Set<String> = emptySet(),
    /** Channels that need the PIN before they play (by key). */
    val locked: Set<String> = emptySet(),
    /** Names the viewer gave to channels (key -> name). */
    val names: Map<String, String> = emptyMap(),
    /** Logos picked by the viewer (by channel key). */
    val logos: Map<String, String> = emptyMap(),
    val pinSet: Boolean = false,
    val pin: PinPrompt? = null,
    /** A series asked to open from outside its page (search): the Series page opens it. */
    val openShow: Channel? = null,
    val pinError: Boolean = false,
    /** What is typed in the Search tab; separate from the Live TV list choice (searchQuery). */
    val searchText: String = "",
    /** Movies and episodes watched, newest first (apart from the channels' [recent]). */
    val recentVod: List<String> = emptyList(),
    /** Where each movie or episode was left (key -> position to duration, in milliseconds). */
    val positions: Map<String, Pair<Long, Long>> = emptyMap(),
    /** Goes up when the public logo index is ready, so the lists pick up the logos it fills in. */
    val logoVersion: Int = 0
)

/** A channel with the viewer's own name and logo, where they chose one. */
private fun personal(channel: Channel, names: Map<String, String>, picked: Map<String, String>): Channel {
    val name = names[channel.key]
    val logo = picked[channel.key]
    return if (name == null && logo == null) channel else channel.copy(name = name ?: channel.name, logo = logo ?: channel.logo)
}

/**
 * Applies the viewer's hidden channels and custom names to a list, and fills in logos from the
 * public index for channels the playlist gives none, remembering the last result.
 */
private class ChannelView {
    private var list: List<Channel>? = null
    private var hidden: Set<String>? = null
    private var names: Map<String, String>? = null
    private var picked: Map<String, String>? = null
    private var logos: Int = -1
    private var result: List<Channel> = emptyList()
    fun of(source: List<Channel>, hidden: Set<String>, names: Map<String, String>, picked: Map<String, String>, logos: Int): List<Channel> {
        if (source === this.list && hidden === this.hidden && names === this.names && picked === this.picked && logos == this.logos) return result
        this.list = source; this.hidden = hidden; this.names = names; this.picked = picked; this.logos = logos
        val list = LogoIndex.fill(source)
        result = if (hidden.isEmpty() && names.isEmpty() && picked.isEmpty()) list else list.mapNotNull { channel ->
            val key = channel.key
            if (key in hidden) null else personal(channel, names, picked)
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
    /** A new PIN typed once, waiting to be typed again. */
    private var pendingPin: String = ""
    /**
     * The playlist being loaded. Only one at a time: a slow download that finished after the
     * viewer had already picked another playlist used to put the old channels back.
     */
    private var playlistJob: Job? = null
    private var epgJob: Job? = null
    /** When the guide was last loaded; it holds only the next day, so it is refreshed when older. */
    private var epgLoadedAt = 0L

    private fun startPlaylistJob(block: suspend CoroutineScope.() -> Unit) {
        playlistJob?.cancel()
        playlistJob = viewModelScope.launch(block = block)
    }

    /** The channels of a playlist address: downloaded, or read from a file imported earlier. */
    private suspend fun readChannels(url: String): List<Channel> =
        if (url.startsWith("local://")) withContext(Dispatchers.IO) {
            M3uParser.parse(File(playlistDirectory, url.removePrefix("local://")).reader())
                .also { require(it.isNotEmpty()) { "The saved M3U file contains no playable channels" } }
        } else repository.load(url)
    /** What the screens see: the raw state with hidden channels removed and custom names applied. */
    val state: StateFlow<TvState> = _state.map { raw ->
        raw.copy(
            channels = channelsView.of(raw.channels, raw.hidden, raw.names, raw.logos, raw.logoVersion),
            queue = queueView.of(raw.queue, raw.hidden, raw.names, raw.logos, raw.logoVersion),
            selected = raw.selected?.let { LogoIndex.fill(listOf(it)).first() }?.let { personal(it, raw.names, raw.logos) }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TvState())
    init {
        // Logos for channels without one: read once at start (downloaded at most once a week).
        viewModelScope.launch {
            if (LogoIndex.load(getApplication())) _state.value = _state.value.copy(logoVersion = _state.value.logoVersion + 1)
        }
        viewModelScope.launch { preferences.hiddenKeys.collect { keys -> _state.value = _state.value.copy(hidden = keys) } }
        viewModelScope.launch { preferences.lockedKeys.collect { keys -> _state.value = _state.value.copy(locked = keys) } }
        viewModelScope.launch { preferences.channelNames.collect { names -> _state.value = _state.value.copy(names = names) } }
        viewModelScope.launch { preferences.channelLogos.collect { logos -> _state.value = _state.value.copy(logos = logos) } }
        viewModelScope.launch { preferences.pin.collect { value -> pinCode = value; _state.value = _state.value.copy(pinSet = value.isNotBlank()) } }
    }
    init {
        viewModelScope.launch {
            // Cache first, as the big apps do: the last playlist is on screen at once, and it is
            // downloaded again only when it is old, quietly, while the viewer already watches.
            // (It used to be downloaded again at every start, before anything could be watched.)
            val cached = withContext(Dispatchers.IO) { dao.getAll().map { it.toChannel() } }
            if (cached.isNotEmpty() && _state.value.channels.isEmpty()) {
                _state.value = _state.value.copy(channels = cached, selected = cached.firstOrNull { it.id == lastChannelId } ?: cached.firstOrNull())
            }
            preferences.playlistUrl.collect { saved ->
                // No playlist is bundled: a fresh install shows the welcome screen instead.
                if (_state.value.playlistUrl.isBlank() && saved.isNotBlank()) {
                    _state.value = _state.value.copy(playlistUrl = saved)
                    val (stampUrl, stampAt) = preferences.cacheStamp()
                    val fresh = stampUrl == saved && System.currentTimeMillis() - stampAt < CacheMaxAgeMillis
                    when {
                        _state.value.channels.isEmpty() -> loadPlaylist()
                        !fresh && !saved.startsWith("local://") -> loadPlaylist(quiet = true)
                    }
                }
                _state.value = _state.value.copy(ready = true)
            }
        }
    }
    init {
        viewModelScope.launch {
            preferences.epgUrl.collect { saved ->
                if (saved.isNotBlank() && _state.value.epgUrl.isBlank()) {
                    _state.value = _state.value.copy(epgUrl = saved)
                    // The guide keeps only the playlist's own channels, so it waits for them.
                    withTimeoutOrNull(60_000) { _state.first { it.channels.isNotEmpty() } }
                    // The saved guide loads by itself on start; a failure here stays quiet.
                    loadEpg(silent = true)
                }
            }
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
                // Never swap out what is playing: a movie open on screen stays the movie.
                if (last != null && !_state.value.playing) _state.value = _state.value.copy(selected = last)
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
        viewModelScope.launch {
            preferences.recentVodKeys.collect { keys -> _state.value = _state.value.copy(recentVod = keys) }
        }
        viewModelScope.launch {
            preferences.positions.collect { saved -> _state.value = _state.value.copy(positions = saved) }
        }
    }
    init {
        viewModelScope.launch {
            preferences.language.collect { code -> _state.value = _state.value.copy(language = code) }
        }
        viewModelScope.launch {
            preferences.theme.collect { id ->
                val palette = Palettes.of(id)
                ActivePalette.current = palette
                _state.value = _state.value.copy(theme = palette.id)
            }
        }
        viewModelScope.launch {
            preferences.groupMode.collect { mode -> _state.value = _state.value.copy(groupMode = mode) }
        }
        viewModelScope.launch {
            preferences.sectionOrder.collect { order -> _state.value = _state.value.copy(sectionOrder = order) }
        }
        viewModelScope.launch {
            preferences.sectionHidden.collect { hidden -> _state.value = _state.value.copy(sectionHidden = hidden) }
        }
    }
    fun setSectionOrder(mode: String, tokens: List<String>) {
        val order = _state.value.sectionOrder + (mode to tokens)
        _state.value = _state.value.copy(sectionOrder = order)
        viewModelScope.launch { preferences.saveSectionOrder(order) }
    }
    fun hideSection(mode: String, token: String) {
        val hidden = _state.value.sectionHidden + (mode to (_state.value.sectionHidden[mode].orEmpty() + token).distinct())
        _state.value = _state.value.copy(sectionHidden = hidden, searchQuery = if (_state.value.searchQuery == token) "__all" else _state.value.searchQuery)
        viewModelScope.launch { preferences.saveSectionHidden(hidden) }
    }
    /** Back to the default order, with every hidden entry shown again. */
    fun resetSections() {
        _state.value = _state.value.copy(sectionOrder = emptyMap(), sectionHidden = emptyMap())
        viewModelScope.launch { preferences.saveSectionOrder(emptyMap()); preferences.saveSectionHidden(emptyMap()) }
    }
    fun setGroupMode(mode: String) {
        // The old choice in the side list belongs to the old sections; start again from the top.
        _state.value = _state.value.copy(groupMode = mode, searchQuery = "")
        viewModelScope.launch { preferences.saveGroupMode(mode) }
    }
    /** Repaints every screen at once and keeps the choice for the next start. */
    fun setTheme(id: String) {
        val palette = Palettes.of(id)
        ActivePalette.current = palette
        _state.value = _state.value.copy(theme = palette.id)
        viewModelScope.launch { preferences.saveTheme(palette.id) }
    }
    fun setLanguage(code: String) {
        _state.value = _state.value.copy(language = code)
        viewModelScope.launch { preferences.saveLanguage(code) }
    }
    /** A logo picked by the viewer for [channel]; null puts the playlist's own logo back. */
    fun setChannelLogo(channel: Channel, url: String?) {
        val logos = if (url.isNullOrBlank()) _state.value.logos - channel.key else _state.value.logos + (channel.key to url.trim())
        _state.value = _state.value.copy(logos = logos)
        viewModelScope.launch { preferences.saveChannelLogos(logos) }
    }
    fun showOpened() { _state.value = _state.value.copy(openShow = null) }

    // The last series opened, so going back and forth does not ask the server again.
    private val seasonCache = object : LinkedHashMap<String, ShowDetails>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShowDetails>?) = size > 12
    }
    // The last films opened: their genre and story, asked for once.
    private val infoCache = object : LinkedHashMap<String, TitleInfo?>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TitleInfo?>?) = size > 40
    }

    /** A series' seasons: from the server for an Xtream series (once), else from the playlist. */
    suspend fun seasons(show: Channel): List<Season> {
        if (Shows.xtreamId(show) == null) {
            val channels = _state.value.channels
            return withContext(Dispatchers.Default) { Shows.seasonsFromPlaylist(show, channels) }
        }
        val cacheKey = _state.value.playlistUrl + "|" + show.key
        synchronized(seasonCache) { seasonCache[cacheKey] }?.let { return it.seasons }
        val loaded = repository.show(_state.value.playlistUrl, show)
        if (loaded.seasons.isNotEmpty()) synchronized(seasonCache) { seasonCache[cacheKey] = loaded }
        return loaded.seasons
    }

    /** What the provider tells about a series, once its seasons were asked for; null otherwise. */
    fun showInfo(show: Channel): TitleInfo? =
        synchronized(seasonCache) { seasonCache[_state.value.playlistUrl + "|" + show.key] }?.info

    /** A film's genre, story, year and rating from its Xtream account (asked for once); null elsewhere. */
    suspend fun titleInfo(movie: Channel): TitleInfo? {
        val cacheKey = _state.value.playlistUrl + "|" + movie.key
        synchronized(infoCache) { if (infoCache.containsKey(cacheKey)) return infoCache[cacheKey] }
        val info = runCatching { repository.movieInfo(_state.value.playlistUrl, movie) }
            .onFailure { if (it is CancellationException) throw it }.getOrNull()
        synchronized(infoCache) { infoCache[cacheKey] = info }
        return info
    }

    private fun rememberWatched(channel: Channel) {
        if (channel.kind() != ChannelKind.Live) {
            // Movies and episodes have a history of their own.
            val vod = (listOf(channel.key) + _state.value.recentVod.filterNot { it == channel.key }).take(40)
            _state.value = _state.value.copy(recentVod = vod)
            viewModelScope.launch { preferences.saveRecentVod(vod) }
            return
        }
        val updated = (listOf(channel.key) + _state.value.recent.filterNot { it == channel.key }).take(20)
        _state.value = _state.value.copy(recent = updated)
        viewModelScope.launch { preferences.saveRecent(updated) }
    }

    fun setPlaylistName(value: String) { _state.value = _state.value.copy(playlistName = value) }
    fun setError(value: String) { _state.value = _state.value.copy(error = value) }
    fun importPlaylist(uri: Uri) {
        startPlaylistJob {
            _state.value = _state.value.copy(loading = true, error = null)
            val name = _state.value.playlistName.trim().ifBlank { "Imported playlist ${_state.value.playlists.size + 1}" }
            runCatching {
                // Reading, parsing and writing a large file must not freeze the screen: all of it
                // happens here, off the main thread (the file used to be read on the main thread).
                withContext(Dispatchers.IO) {
                    val fileName = "import-${System.currentTimeMillis()}.m3u"
                    playlistDirectory.mkdirs()
                    val file = File(playlistDirectory, fileName)
                    // Copied to the app's own file first, then read from there line by line.
                    try {
                        getApplication<Application>().contentResolver.openInputStream(uri)
                            ?.use { source -> file.outputStream().use { source.copyTo(it) } }
                            ?: error("Unable to open the selected M3U file")
                        val channels = M3uParser.parse(file.reader()).also {
                            require(it.isNotEmpty()) { "The selected file contains no playable M3U channels" }
                        }
                        Triple(channels, name, fileName)
                    } catch (e: Throwable) {
                        file.delete()
                        throw e
                    }
                }
            }
                .onSuccess { (channels, name, fileName) ->
                    val localUrl = "local://$fileName"
                    val updated = (_state.value.playlists.filterNot { it.url == localUrl } + PlaylistEntry(name, localUrl))
                    _state.value = _state.value.copy(
                        channels = channels, selected = channels.firstOrNull { it.id == lastChannelId } ?: channels.firstOrNull(), loading = false,
                        tab = "Live TV", playlistName = name, playlistUrl = localUrl, playlists = updated
                    )
                    preferences.savePlaylists(updated.map { "${it.name}\t${it.url}" }.toSet())
                    // Remembered as the active playlist, so the app reopens on it.
                    preferences.savePlaylist(localUrl)
                    dao.replaceAll(channels)
                    preferences.saveCacheStamp(localUrl)
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to import playlist")
                }
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
        val wasActive = entry.url == _state.value.playlistUrl
        val nothingLeft = updated.isEmpty()
        // A load still on its way would bring the channels of a removed playlist back.
        if (nothingLeft) playlistJob?.cancel()
        _state.value = if (nothingLeft) {
            // No playlist left: drop its channels too, so the app goes back to the welcome screen.
            _state.value.copy(
                playlists = updated, playlistUrl = "", playlistName = "", channels = emptyList(), queue = emptyList(),
                selected = null, playing = false, error = null, searchQuery = "", searchText = "",
                // The next playlist is added from the welcome screen; afterwards the app opens on Live TV.
                tab = "Live TV"
            )
        } else {
            _state.value.copy(playlists = updated)
        }
        viewModelScope.launch {
            // Clear the active address first, so nothing reloads the playlist that was just removed.
            if (nothingLeft) preferences.savePlaylist("")
            preferences.savePlaylists(updated.map { "${it.name}\t${it.url}" }.toSet())
            if (nothingLeft) dao.clear()
            if (entry.url.startsWith("local://")) {
                File(playlistDirectory, entry.url.removePrefix("local://")).delete()
            }
        }
        // The playing playlist was removed but others remain: move to the first of them.
        if (!nothingLeft && wasActive) selectPlaylist(updated.first())
    }
    fun selectPlaylist(entry: PlaylistEntry) {
        _state.value = _state.value.copy(playlistName = entry.name, playlistUrl = entry.url)
        if (!entry.url.startsWith("local://")) {
            loadPlaylist(entry.url)
            return
        }
        startPlaylistJob {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { readChannels(entry.url) }.onSuccess { channels ->
                _state.value = _state.value.copy(channels = channels, selected = channels.firstOrNull { it.id == lastChannelId } ?: channels.firstOrNull(), loading = false, tab = "Live TV")
                // Remembered as the active playlist, so the app reopens on it.
                preferences.savePlaylist(entry.url)
                dao.replaceAll(channels)
                preferences.saveCacheStamp(entry.url)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load saved playlist")
            }
        }
    }
    fun loadEpg(silent: Boolean = false) {
        val url = _state.value.epgUrl
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            // Only programmes of this playlist's channels (matched by id or by name, as the player does).
            val channels = _state.value.channels
            val ids = HashSet<String>(channels.size * 2)
            for (c in channels) if (c.kind() == ChannelKind.Live) { ids += c.id; ids += c.name.lowercase() }
            val wanted: ((String) -> Boolean)? = if (ids.isEmpty()) null else { id -> id in ids || id.lowercase() in ids }
            runCatching { epgRepository.load(url, wanted) }.onSuccess { items ->
                _state.value = _state.value.copy(programs = items)
                epgLoadedAt = System.currentTimeMillis()
                preferences.saveEpg(url)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (!silent) _state.value = _state.value.copy(error = e.message ?: "Unable to load EPG")
            }
        }
    }
    /**
     * Called whenever the app comes back on screen. The guide holds only the next 24 hours, and a
     * TV keeps the app in memory for days, so a guide loaded long ago is quietly loaded again.
     */
    fun refreshEpgIfStale() {
        if (_state.value.epgUrl.isBlank() || epgJob?.isActive == true) return
        if (System.currentTimeMillis() - epgLoadedAt < EpgMaxAgeMillis) return
        loadEpg(silent = true)
    }
    private companion object {
        const val EpgMaxAgeMillis = 6L * 60 * 60 * 1000
        /** A cached playlist older than this is downloaded again (quietly) at start. */
        const val CacheMaxAgeMillis = 24L * 60 * 60 * 1000
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
        // A series is not a stream: it opens on its seasons and episodes.
        if (channel.isShow()) {
            _state.value = _state.value.copy(tab = "Series", openShow = channel)
            return
        }
        if (channel.kind() != ChannelKind.Live) {
            // A movie plays on its own: no channel list, no zapping, and the last channel stays the last channel.
            _state.value = _state.value.copy(selected = channel, playing = true)
            rememberWatched(channel)
            return
        }
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
    /** Remembers where a movie was left, so it can be resumed. Very start or very end: nothing to resume. */
    fun savePosition(channel: Channel, position: Long, duration: Long) {
        val keep = duration > 0 && position > 30_000L && position < duration - 60_000L
        val current = _state.value.positions
        val updated = if (keep) (current - channel.key) + (channel.key to (position to duration)) else current - channel.key
        if (updated == current) return
        val trimmed = if (updated.size > 200) updated.entries.drop(updated.size - 200).associate { it.key to it.value } else updated
        _state.value = _state.value.copy(positions = trimmed)
        viewModelScope.launch { preferences.savePositions(trimmed) }
    }
    /** Privacy: forget that this channel was watched. */
    fun removeFromHistory(channel: Channel) {
        val updated = _state.value.recent.filterNot { it == channel.key }
        val vod = _state.value.recentVod.filterNot { it == channel.key }
        _state.value = _state.value.copy(recent = updated, recentVod = vod, positions = _state.value.positions - channel.key)
        viewModelScope.launch {
            preferences.saveRecentVod(vod)
            preferences.savePositions(_state.value.positions)
            preferences.saveRecent(updated)
            // It is also remembered as "the last channel"; forget that too.
            if (lastChannelId == channel.id) { lastChannelId = ""; preferences.saveLastChannel("") }
        }
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
            // Locking asks for the PIN too, so nobody can lock channels by accident or as a prank.
            else -> _state.value = _state.value.copy(pin = PinPrompt("lock", channel), pinError = false)
        }
    }
    /** Hiding asks for the PIN on the card, the same way locking does (a new PIN first if none is set). */
    fun requestHide(channel: Channel) {
        _state.value = _state.value.copy(
            pin = if (pinCode.isBlank()) PinPrompt("set", channel, then = "hide") else PinPrompt("hide", channel),
            pinError = false
        )
    }
    private fun setLocked(keys: Set<String>) {
        _state.value = _state.value.copy(locked = keys)
        viewModelScope.launch { preferences.saveLocked(keys) }
    }
    fun changePin() {
        _state.value = _state.value.copy(pin = PinPrompt(if (pinCode.isBlank()) "set" else "verify"), pinError = false)
    }
    /** Runs [action] straight away when no PIN is set, otherwise only after the PIN is entered. */
    fun protect(label: String? = null, reason: String? = null, action: () -> Unit) {
        if (pinCode.isBlank()) { action(); return }
        pendingAction = action
        _state.value = _state.value.copy(pin = PinPrompt("confirm", label = label, reason = reason), pinError = false)
    }
    fun cancelPin() {
        pendingAction = null
        pendingPin = ""
        _state.value = _state.value.copy(pin = null, pinError = false)
    }
    /** Returns true when the PIN was accepted and the question is finished (nothing more to ask). */
    fun submitPin(value: String): Boolean {
        val prompt = _state.value.pin ?: return false
        if (prompt.purpose == "set") {
            if (!Regex("\\d{4}").matches(value)) { _state.value = _state.value.copy(pinError = true); return false }
            // A new PIN is typed twice, so a slip of the finger cannot lock the viewer out.
            pendingPin = value
            _state.value = _state.value.copy(pin = prompt.copy(purpose = "repeat", mismatch = false), pinError = false)
            return false
        }
        if (prompt.purpose == "repeat") {
            val first = pendingPin
            pendingPin = ""
            if (value != first) {
                _state.value = _state.value.copy(pin = prompt.copy(purpose = "set", mismatch = true), pinError = false)
                return false
            }
            pinCode = value
            _state.value = _state.value.copy(pin = null, pinError = false, pinSet = true)
            viewModelScope.launch { preferences.savePin(value) }
            prompt.channel?.let { if (prompt.then == "hide") hideChannel(it) else setLocked(_state.value.locked + it.key) }
            return true
        }
        if (value != pinCode) { _state.value = _state.value.copy(pinError = true); return false }
        _state.value = _state.value.copy(pin = null, pinError = false)
        when (prompt.purpose) {
            "open" -> prompt.channel?.let { play(it, prompt.from, prompt.title) }
            "unlock" -> prompt.channel?.let { setLocked(_state.value.locked - it.key) }
            "lock" -> prompt.channel?.let { setLocked(_state.value.locked + it.key) }
            "hide" -> prompt.channel?.let { hideChannel(it) }
            // The old PIN was right: the same card now asks for the new one.
            "verify" -> { _state.value = _state.value.copy(pin = PinPrompt("set")); return false }
            "confirm" -> { val action = pendingAction; pendingAction = null; action?.invoke() }
        }
        return true
    }
    /** Favourites are kept per playlist entry, so two streams that share an id stay independent. */
    fun toggleFavorite(channel: Channel) {
        val f = _state.value.favorites.toMutableSet()
        if (f.hasFavorite(channel)) {
            if (f.remove(channel.id)) {
                // An entry saved the old way, by the shared id: the other streams with that id stay.
                _state.value.channels.filter { it.id == channel.id && it.key != channel.key }.forEach { f.add(it.key) }
            }
            f.remove(channel.key)
        } else {
            f.add(channel.key)
        }
        _state.value = _state.value.copy(favorites = f)
        viewModelScope.launch { preferences.saveFavorites(f) }
    }
    /** Moves favourites saved by the old shared id over to the playlist entries that have that id. */
    private fun moveOldFavorites(channels: List<Channel>) {
        val saved = _state.value.favorites
        if (saved.isEmpty() || channels.isEmpty()) return
        val keys = channels.mapTo(HashSet()) { it.key }
        val byId = channels.groupBy { it.id }
        // Only entries that match this playlist are moved; others may belong to another playlist.
        val old = saved.filter { it !in keys && byId.containsKey(it) }
        if (old.isEmpty()) return
        val updated = (saved - old.toSet()) + old.flatMap { id -> byId[id].orEmpty().map { it.key } }
        _state.value = _state.value.copy(favorites = updated)
        viewModelScope.launch { preferences.saveFavorites(updated) }
    }
    /**
     * [openLive]: a playlist that was just added opens on Live TV once its channels are in.
     * [quiet]: a background refresh of the playlist already on screen. No loading state and no
     * error: the cached channels stay until the new ones are complete, then swap in at once.
     *
     * An Xtream account arrives in two parts: its live channels first (seconds), then its movies
     * and series while the viewer can already watch, as the big IPTV apps do.
     */
    fun loadPlaylist(urlOverride: String? = null, openLive: Boolean = false, quiet: Boolean = false) {
        val url = (urlOverride ?: _state.value.playlistUrl).trim()
        startPlaylistJob {
            if (!quiet) _state.value = _state.value.copy(loading = true, error = null)
            // An imported file is read again from the app's copy (it is the active playlist after a restart too).
            runCatching { readChannels(url) }.onSuccess { first ->
                // A guide that comes with the playlist (an Xtream account's own, or the one an M3U names
                // in its first line) is used when no guide was set, as the big apps do.
                val guide = XtreamAccount.from(url)?.guideUrl ?: M3uParser.lastGuideUrl
                // A reload while something plays leaves it playing.
                val keep = quiet || _state.value.playing
                var channels = first
                if (quiet) {
                    channels = PlaylistRepository.merge(first, repository.loadMore(url, first))
                    _state.value = _state.value.copy(channels = channels)
                } else {
                    _state.value = _state.value.copy(channels = channels, loading = false, selected = if (keep) _state.value.selected else channels.firstOrNull())
                    if (openLive) _state.value = _state.value.copy(tab = "Live TV", searchQuery = "")
                }
                moveOldFavorites(channels)
                preferences.savePlaylist(url)
                dao.replaceAll(channels)
                if (guide != null && _state.value.epgUrl.isBlank()) {
                    _state.value = _state.value.copy(epgUrl = guide)
                    loadEpg(silent = true)
                }
                if (!quiet) {
                    val more = repository.loadMore(url, first)
                    if (more.isNotEmpty()) {
                        channels = PlaylistRepository.merge(first, more)
                        _state.value = _state.value.copy(channels = channels)
                        dao.replaceAll(channels)
                    }
                }
                preferences.saveCacheStamp(url)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (!quiet) _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load playlist")
            }
        }
    }
}