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
        // The live list came from get.php itself (no API): it already holds everything.
        if (haveLive.any { it.kind() != ChannelKind.Live }) return@withContext emptyList()
        val movies = runCatching { XtreamApi(client, account, PlayerAgent).loadMovies() }
            .onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
        val series = runCatching { loadM3u(url, keep = { line -> line.substringBefore("?").contains("/series/", ignoreCase = true) }) }
            .onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
        movies + series
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
        /** What the app says it is to servers; accepted by most IPTV servers. */
        const val PlayerAgent = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
