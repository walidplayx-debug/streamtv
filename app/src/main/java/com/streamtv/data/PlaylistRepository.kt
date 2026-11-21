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

    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Enter a valid http(s) playlist URL" }
        val account = XtreamAccount.from(url)
        val m3u = runCatching { loadM3u(url) }
        m3u.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val channels = m3u.getOrNull()
        if (!channels.isNullOrEmpty() || account == null) return@withContext channels ?: throw m3u.exceptionOrNull()!!
        // Many Xtream servers refuse or empty get.php; the big apps read the account's API instead.
        // Its own words (wrong password, expired account) also say more than an HTTP code.
        XtreamApi(client, account, PlayerAgent).load()
    }

    private fun loadM3u(url: String): List<Channel> {
        // The same player name as the streams: some servers refuse playlists to unknown apps.
        val request = Request.Builder().url(url).header("User-Agent", PlayerAgent).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Playlist request failed: HTTP ${response.code}")
            // Streamed, not read whole into one string: big playlists ran out of memory on small TVs.
            val body = response.body ?: return@use emptyList()
            M3uParser.parse(body.charStream())
        }
    }

    companion object {
        /** What the app says it is to servers; accepted by most IPTV servers. */
        const val PlayerAgent = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
