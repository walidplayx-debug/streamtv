package com.streamtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class EpgRepository {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    suspend fun load(url: String): List<EpgProgram> = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Enter a valid XMLTV URL" }
        val request = Request.Builder().url(url).header("User-Agent", "streamtv/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("EPG request failed: HTTP ${response.code}")
            val gzipped = url.endsWith(".gz", true) || response.header("Content-Type").orEmpty().contains("gzip", true)
            XmlTvParser.parse(response.body?.byteStream() ?: error("Empty EPG response"), gzipped)
        }
    }
}
