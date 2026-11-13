package com.streamtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlaylistRepository {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    suspend fun load(url: String): List<Channel> = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Enter a valid http(s) playlist URL" }
        val request = Request.Builder().url(url).header("User-Agent", "streamtv/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Playlist request failed: HTTP ${response.code}")
            M3uParser.parse(response.body?.string().orEmpty())
        }
    }
}
