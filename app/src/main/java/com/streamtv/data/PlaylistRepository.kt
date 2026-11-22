package com.streamtv.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlaylistRepository {
    // Big Xtream playlists (tens of megabytes from a slow server) took longer than a fixed 45 s
    // and failed; now only a stalled connection fails, and the whole download may take minutes.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    /**
     * The first, quick part of a playlist. For an Xtream account that is its live channels, read
     * through the account's API as the big apps do (seconds, not minutes); its movies and series
     * follow from [loadMore]. Any other address is read whole as an M3U playlist.
     */
    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Enter a valid http(s) playlist URL" }
        val account = XtreamAccount.from(url) ?: return@withContext loadM3u(url)
        val live = runCatching { XtreamApi(client, account, PlayerAgent).loadLive() }
        live.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        live.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        // A server without a working API: its whole get.php playlist, as before.
        val m3u = runCatching { loadM3u(url) }
        m3u.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        m3u.getOrNull()?.takeIf { it.isNotEmpty() } ?: throw (live.exceptionOrNull() ?: m3u.exceptionOrNull()
            ?: IllegalStateException("The Xtream account has no channels"))
    }

    /**
     * What [load] left for later on an Xtream account: its movies (from the API) and its series
     * episodes (from get.php, keeping only the series lines, so the rest of that big file never
     * stays in memory). Empty for other playlists or when the server offers neither. Errors are
     * swallowed: the live channels are already on screen.
     */
    suspend fun loadMore(url: String, haveLive: List<Channel>): List<Channel> = withContext(Dispatchers.IO) {
        val account = XtreamAccount.from(url) ?: return@withContext emptyList()
        val api = XtreamApi(client, account, PlayerAgent)
        // The live list came from get.php itself (no API): it already holds the films.
        val fromPlaylist = haveLive.any { it.kind() != ChannelKind.Live }
        val movies = if (fromPlaylist) emptyList() else runCatching { api.loadMovies() }
            .onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
        // Series: the API's list of shows, their episodes asked for only when one is opened (many
        // servers leave series out of get.php altogether). get.php's episode lines only when the
        // API has no series list.
        val shows = if (fromPlaylist && haveLive.any { it.kind() == ChannelKind.Series }) emptyList()
            else runCatching { api.loadSeries() }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
        val series = if (shows.isNotEmpty() || fromPlaylist) shows else runCatching {
            loadM3u(url, keep = { line -> line.substringBefore("?").contains("/series/", ignoreCase = true) })
        }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
        movies + series
    }

    /** The seasons, episodes and details of an Xtream series, asked for when it is opened. */
    suspend fun show(playlistUrl: String, show: Channel): ShowDetails = withContext(Dispatchers.IO) {
        val id = Shows.xtreamId(show) ?: return@withContext ShowDetails(emptyList(), null)
        val account = XtreamAccount.from(playlistUrl) ?: error("This series belongs to another playlist")
        XtreamApi(client, account, PlayerAgent).loadShow(id)
    }

    /** A film's details from an Xtream account (its id is the last part of its address); null elsewhere. */
    suspend fun movieInfo(playlistUrl: String, movie: Channel): TitleInfo? = withContext(Dispatchers.IO) {
        val account = XtreamAccount.from(playlistUrl) ?: return@withContext null
        val path = movie.url.substringBefore("?")
        if (!path.contains("/movie/")) return@withContext null
        val id = path.substringAfterLast('/').substringBefore('.').takeIf { it.isNotBlank() } ?: return@withContext null
        XtreamApi(client, account, PlayerAgent).loadMovieInfo(id)
    }

    private fun loadM3u(url: String, keep: ((String) -> Boolean)? = null): List<Channel> {
        // The same player name as the streams: some servers refuse playlists to unknown apps.
        val request = Request.Builder().url(url).header("User-Agent", PlayerAgent).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Playlist request failed: HTTP ${response.code}")
            // Streamed, not read whole into one string: big playlists ran out of memory on small TVs.
            val body = response.body ?: return@use emptyList()
            if (keep == null) M3uParser.parse(body.charStream()) else M3uParser.parse(body.charStream(), keep)
        }
    }

    companion object {
        /**
         * The live channels and the rest together, without the "channels" that are the account's
         * own films again (same name and picture). Some servers, test servers among them, answer
         * the live list with their films and mark them as live.
         */
        fun merge(live: List<Channel>, more: List<Channel>): List<Channel> {
            if (more.isEmpty()) return live
            fun sign(c: Channel) = c.name.trim().lowercase() + "\u0000" + c.logo.orEmpty()
            val films = more.mapTo(HashSet()) { sign(it) }
            return live.filter { it.kind() != ChannelKind.Live || sign(it) !in films } + more
        }

        /** What the app says it is to servers; accepted by most IPTV servers. */
        const val PlayerAgent = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
