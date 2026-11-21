package com.streamtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Logo pictures for a channel name, for the viewer to pick one. Free sources that need no account
 * or key: the iptv-org channel logos already used by the app, then logo files on Wikimedia Commons
 * and Wikipedia (which also holds most TV logos that are not free to reuse elsewhere).
 */
object LogoSearch {
    /** Sent with every request: Wikimedia refuses requests that do not say who is asking. */
    const val UserAgent = "STV-Android-App/1.0 (https://github.com/walidplayx-debug/streamtv)"

    suspend fun search(query: String, limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val name = query.trim()
        if (name.length < 2) return@withContext emptyList()
        coroutineScope {
            val wikis = listOf("commons.wikimedia.org", "en.wikipedia.org", "ar.wikipedia.org").map { host ->
                async { runCatching { wiki(host, name, limit) }.getOrDefault(emptyList()) }
            }
            val index = LogoIndex.search(name, limit)
            val found = LinkedHashSet<String>(index)
            // Taken in turns from each site, so one site does not fill every place.
            val lists = wikis.map { it.await() }
            var i = 0
            while (found.size < limit && lists.any { i < it.size }) {
                for (list in lists) if (i < list.size && found.size < limit) found += list[i]
                i++
            }
            found.toList()
        }
    }

    /** Picture files whose name or text matches "<name> logo" on one wiki, as small PNG/JPEG previews. */
    private fun wiki(host: String, name: String, limit: Int): List<String> {
        val search = URLEncoder.encode("$name logo", "UTF-8")
        val url = "https://$host/w/api.php?action=query&format=json&generator=search&gsrnamespace=6" +
            "&gsrlimit=$limit&gsrsearch=$search&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=320"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", UserAgent)
        }
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val found = ArrayList<Pair<Int, String>>()
            for (key in pages.keys()) {
                val page = pages.optJSONObject(key) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime")
                if (!mime.startsWith("image/") || mime == "image/gif" || mime == "image/tiff") continue
                // The preview is a PNG even for an SVG file, which the app could not draw itself.
                val thumb = info.optString("thumburl").ifBlank { info.optString("url") }
                if (thumb.startsWith("http")) found += page.optInt("index", Int.MAX_VALUE) to thumb
            }
            return found.sortedBy { it.first }.map { it.second }
        } finally {
            connection.disconnect()
        }
    }
}
