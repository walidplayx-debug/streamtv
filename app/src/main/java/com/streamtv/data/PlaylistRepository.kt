package com.streamtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlaylistRepository {
    // No limit on the whole download: a big Xtream playlist from a slow server needs more than a
    // minute. It gives up only when the server cannot be reached or stops sending for a minute.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Enter a valid http(s) playlist URL" }
        // An Xtream login: through the server's API first, like other players, ready in seconds.
        // A server without the API (or one that fails it) still gets the one-file M3U below.
        XtreamLogin.from(url)?.let { login ->
            try {
                val channels = XtreamApi(client).load(login)
                if (channels.isNotEmpty()) return@withContext channels
            } catch (e: XtreamLoginException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Any other failure (no API, an odd answer): fall through to the M3U.
            }
        }
        val request = Request.Builder().url(url).header("User-Agent", "streamtv/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Playlist request failed: HTTP ${response.code}")
            // Streamed, not read whole into one string: big playlists ran out of memory on small TVs.
            val body = response.body ?: return@use emptyList()
            M3uParser.parse(body.charStream())
        }
    }
}
