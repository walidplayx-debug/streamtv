package com.streamtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Logo pictures for a channel name, for the viewer to pick one. An image search first (DuckDuckGo,
 * whose pictures come from Bing, then Bing itself), as close to a Google image search as an app can
 * get without a paid key; then the iptv-org channel logos and the logo files on Wikimedia Commons
 * and Wikipedia. The image searches are read from their web pages, so a change on their side can
 * stop them; the other sources still answer then.
 */
object LogoSearch {
    /** Sent to Wikimedia, which refuses requests that do not say who is asking. */
    const val UserAgent = "STV-Android-App/1.0 (https://github.com/walidplayx-debug/streamtv)"
    /** The search sites answer an ordinary browser. */
    private const val Browser = "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    /** Country and language words of playlists ("UK", "AR", "French"...), as a search engine reads them. */
    private val places = mapOf(
        "uk" to "UK", "gb" to "UK", "us" to "USA", "usa" to "USA", "ca" to "Canada", "fr" to "France", "france" to "France",
        "french" to "France", "be" to "Belgium", "nl" to "Netherlands", "de" to "Germany", "germany" to "Germany",
        "german" to "Germany", "es" to "Spain", "spain" to "Spain", "it" to "Italy", "italy" to "Italy", "pt" to "Portugal",
        "tr" to "Turkey", "turkey" to "Turkey", "turkish" to "Turkey", "in" to "India", "ar" to "Arabic", "arab" to "Arabic",
        "arabic" to "Arabic", "ma" to "Morocco", "dz" to "Algeria", "tn" to "Tunisia", "eg" to "Egypt", "sa" to "Saudi Arabia",
        "ae" to "UAE", "lb" to "Lebanon", "sy" to "Syria", "iq" to "Iraq", "kw" to "Kuwait", "qa" to "Qatar"
    )
    private val namePrefix = Regex("""^\s*[|\[(]?\s*([A-Za-z]{2,3})\s*[|\]):\-]+\s*""")
    private val nameNoise = Regex("""[(\[].*?[)\]]|\b(hd|fhd|uhd|sd|4k|8k|hevc|h265|h264|backup|\d{3,4}p)\b""", RegexOption.IGNORE_CASE)

    /**
     * What is searched for a channel: its name without the playlist's extras ("AR: … FHD (1080p)"),
     * said to be a TV logo, with its country or language when the playlist tells it
     * (its country, a prefix on the name, the first word of its group, or Arabic letters).
     * "5Star" alone found hotels and stars; "5Star TV logo UK" finds the channel. ("TV channel
     * logo" brought made-up channel logos from picture banks instead.)
     */
    internal fun logoQuery(name: String, country: String, group: String): String {
        val prefix = namePrefix.find(name)?.groupValues?.get(1)?.lowercase()?.takeIf { it in places }
        val clean = (if (prefix != null) name.replaceFirst(namePrefix, "") else name)
            .replace(nameNoise, " ").replace(Regex("""\s+"""), " ").trim().ifBlank { name.trim() }
        val place = places[country.trim().lowercase().split(Regex("""[^a-z]+""")).firstOrNull().orEmpty()]
            ?: prefix?.let { places[it] }
            ?: places[group.split('|', '-', ':', '/').first().trim().lowercase()]
            ?: "Arabic".takeIf { name.any { it in '\u0600'..'\u06FF' } }
        return listOfNotNull(clean, "TV logo", place).joinToString(" ")
    }

    suspend fun search(query: String, limit: Int = 10, country: String = "", group: String = ""): List<String> = withContext(Dispatchers.IO) {
        val name = query.trim()
        if (name.length < 2) return@withContext emptyList()
        val web = logoQuery(name, country, group)
        coroutineScope {
            val images = async { runCatching { duckDuckGo(web, limit) }.getOrDefault(emptyList()) }
            val bing = async { runCatching { bing(web, limit) }.getOrDefault(emptyList()) }
            val wikis = listOf("commons.wikimedia.org", "en.wikipedia.org", "ar.wikipedia.org").map { host ->
                async { runCatching { wiki(host, name, limit) }.getOrDefault(emptyList()) }
            }
            val found = LinkedHashSet<String>()
            // The image search first; Bing only when DuckDuckGo gave nothing (they show the same pictures).
            found += images.await().take(limit)
            if (found.isEmpty()) found += bing.await().take(limit)
            // Then the logo databases, taken in turns so one does not fill every place.
            val lists = listOf(LogoIndex.search(name, limit)) + wikis.map { it.await() }
            var i = 0
            while (found.size < limit && lists.any { i < it.size }) {
                for (list in lists) if (i < list.size && found.size < limit) found += list[i]
                i++
            }
            found.toList()
        }
    }

    /**
     * A picture address pasted in the search field (from a phone's remote app, say), or null when
     * the text is a name to search. Google's result links ("…/imgres?imgurl=…") give the picture's
     * own address; a picture pasted as data ("data:image/…;base64,…", what Google often copies for
     * small pictures) is kept as a file in the app, so the logo still shows later.
     */
    fun pasted(context: android.content.Context, text: String): String? {
        val value = text.trim()
        if (value.startsWith("data:image/", true)) {
            val bytes = runCatching { android.util.Base64.decode(value.substringAfter(','), android.util.Base64.DEFAULT) }.getOrNull()
                ?: return null
            val folder = java.io.File(context.filesDir, "logos").apply { mkdirs() }
            val file = java.io.File(folder, "pasted-${value.hashCode().toUInt()}.img")
            if (!file.isFile) file.writeBytes(bytes)
            return android.net.Uri.fromFile(file).toString()
        }
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) return null
        val google = runCatching { android.net.Uri.parse(value) }.getOrNull()
        val inner = google?.takeIf { it.path?.contains("imgres") == true }?.getQueryParameter("imgurl")
        return inner?.takeIf { it.startsWith("http") } ?: value
    }

    /**
     * The picture behind a pasted link. A link to a picture is kept as it is; a link to a page (a
     * Google "share" link such as share.google/images/…, a Google result, any web page) is opened
     * and the picture found in it: Google's "imgurl", even inside the consent page Google shows in
     * Europe, then the page's preview picture, then Google's own small copy of the picture.
     */
    suspend fun pastedPicture(context: android.content.Context, text: String): String? {
        val link = pasted(context, text) ?: return null
        if (!link.startsWith("http")) return link
        return withContext(Dispatchers.IO) { runCatching { pictureBehind(link) }.getOrNull() ?: link }
    }

    private val imgurlPattern = Regex("""imgurl=([^&"'\s\\]+)""")
    private val ogImage = listOf(
        Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image""", RegexOption.IGNORE_CASE)
    )
    private val googleThumb = Regex("""https://encrypted-tbn\d\.gstatic\.com/images\?q=tbn:[A-Za-z0-9_-]+""")

    /** Google's "imgurl" in [text], undone from its (sometimes double) escaping. */
    private fun imgurlIn(text: String): String? {
        var value = imgurlPattern.find(text)?.groupValues?.get(1) ?: return null
        repeat(3) {
            if (value.startsWith("http") && !value.contains("%3A", true)) return value
            value = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }
        return value.takeIf { it.startsWith("http") }
    }

    private fun pictureBehind(link: String): String? {
        imgurlIn(link)?.let { return it }
        val connection = (URL(link).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", Browser)
            setRequestProperty("Accept-Language", "ar,en;q=0.8")
        }
        try {
            val code = connection.responseCode
            val final = connection.url.toString()
            imgurlIn(final)?.let { return it }
            if (connection.contentType.orEmpty().startsWith("image/")) return final
            if (code !in 200..299) return null
            // Pages are read up to 2 MB: enough for the parts that name the picture.
            val page = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(2 * 1024 * 1024)
                var total = 0
                while (total < buffer.size) {
                    val read = reader.read(buffer, total, buffer.size - total)
                    if (read < 0) break
                    total += read
                }
                String(buffer, 0, total)
            }.replace("\\u003d", "=").replace("\\u0026", "&").replace("\\x3d", "=").replace("\\x26", "&").replace("&amp;", "&")
            imgurlIn(page)?.let { return it }
            for (pattern in ogImage) pattern.find(page)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }?.let { return it }
            return googleThumb.find(page)?.value
        } finally {
            connection.disconnect()
        }
    }

    private fun get(url: String, agent: String, referer: String? = null): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", agent)
            setRequestProperty("Accept-Language", "ar,en;q=0.8")
            referer?.let { setRequestProperty("Referer", it) }
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private val vqdPattern = Regex("""vqd=["']?([0-9A-Za-z_-]+)""")

    /** DuckDuckGo images: a token from the search page, then the picture list as JSON. */
    private fun duckDuckGo(query: String, limit: Int): List<String> {
        val q = URLEncoder.encode(query, "UTF-8")
        val page = get("https://duckduckgo.com/?q=$q&iax=images&ia=images", Browser) ?: return emptyList()
        val vqd = vqdPattern.find(page)?.groupValues?.get(1) ?: return emptyList()
        val json = get("https://duckduckgo.com/i.js?l=wt-wt&o=json&q=$q&vqd=$vqd&f=,,,,,&p=1", Browser, "https://duckduckgo.com/")
            ?: return emptyList()
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val found = ArrayList<String>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            // The small copy on Bing's own servers: always reachable, unlike some original sites.
            val picture = item.optString("thumbnail").ifBlank { item.optString("image") }
            if (picture.startsWith("http")) found += picture
            if (found.size >= limit) break
        }
        return found
    }

    private val bingThumb = Regex("""turl&quot;:&quot;(https?://[^&"]+)""")

    /** Bing images: the small copies listed in the results page. */
    private fun bing(query: String, limit: Int): List<String> {
        val q = URLEncoder.encode(query, "UTF-8")
        val page = get("https://www.bing.com/images/search?q=$q&form=HDRSC2&first=1", Browser) ?: return emptyList()
        return bingThumb.findAll(page).map { it.groupValues[1] }.distinct().take(limit).toList()
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
