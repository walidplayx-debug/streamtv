package com.streamtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.io.BufferedInputStream

class EpgRepository {
    // A whole server's guide can be tens of megabytes: only a stalled download fails, not a long one.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()
    suspend fun load(url: String, wanted: ((String) -> Boolean)? = null): List<EpgProgram> = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Enter a valid XMLTV URL" }
        val request = Request.Builder().url(url).header("User-Agent", "streamtv/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("EPG request failed: HTTP ${response.code}")
            val stream = BufferedInputStream(response.body?.byteStream() ?: error("Empty EPG response"))
            stream.mark(2)
            val first = stream.read()
            val second = stream.read()
            stream.reset()
            val gzipped = first == 0x1f && second == 0x8b
            XmlTvParser.parse(stream, gzipped, wanted)
        }
    }
}
