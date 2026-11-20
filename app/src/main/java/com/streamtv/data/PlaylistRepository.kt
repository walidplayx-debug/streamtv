package com.streamtv.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlaylistRepository {
    // A slow provider may take time between JSON chunks; readTimeout is an idle-read limit,
    // not a cap on the total size/time of a playlist response.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Enter a valid http(s) playlist URL"
        }

        // Xtream credentials are a get.php URL, but importing that generated M3U downloads
        // every episode and can take minutes. Use player_api.php directly instead.
        XtreamLogin.from(url)?.let { login ->
            return@withContext try {
                XtreamApi(client).load(login)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }

        val request = Request.Builder().url(url).header("User-Agent", "STV/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Playlist request failed: HTTP ${response.code}")
            val body = response.body ?: return@use emptyList()
            M3uParser.parse(body.charStream())
        }
    }
}
