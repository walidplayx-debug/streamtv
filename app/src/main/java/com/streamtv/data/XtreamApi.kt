package com.streamtv.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** An Xtream Codes account, read from the get.php address the app saves for it. */
class XtreamAccount(val base: String, val username: String, val password: String) {
    /** The account's own TV guide, which every Xtream server offers. */
    val guideUrl: String get() = "$base/xmltv.php?username=${enc(username)}&password=${enc(password)}"
    fun api(action: String? = null): String =
        "$base/player_api.php?username=${enc(username)}&password=${enc(password)}" + (action?.let { "&action=$it" } ?: "")
    fun live(id: String) = "$base/live/${path(username)}/${path(password)}/$id.ts"
    fun movie(id: String, extension: String) = "$base/movie/${path(username)}/${path(password)}/$id.${extension.ifBlank { "mp4" }}"
    fun episode(id: String, extension: String) = "$base/series/${path(username)}/${path(password)}/$id.${extension.ifBlank { "mp4" }}"

    companion object {
        private fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
        private fun path(value: String) = enc(value).replace("+", "%20")

        /** The account behind a get.php playlist address; null for any other address. */
        fun from(url: String): XtreamAccount? {
            if (!url.contains("/get.php?")) return null
            val parsed = url.toHttpUrlOrNull() ?: return null
            val username = parsed.queryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
            val password = parsed.queryParameter("password") ?: return null
            return XtreamAccount(url.substringBefore("/get.php"), username, password)
        }
    }
}

/**
 * Reads an Xtream account the way the big IPTV apps do, through its player_api.php: the account
 * status first (so a wrong password or an expired account says so), then the live channels with
 * their categories, and only after them the movies. The get.php playlist holds everything at once
 * (often tens of megabytes) and is only a fallback. Every list is read as a stream, so a large one never sits in memory whole.
 */
class XtreamApi(private val client: OkHttpClient, private val account: XtreamAccount, private val agent: String) {

    /** Stops with a clear message when the login is refused or the account cannot be used. */
    fun checkAccount() {
        read(account.api()) { reader ->
            var auth = 1
            var status = ""
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return@read }
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "user_info" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                    reader.beginObject()
                    while (reader.hasNext()) when (reader.nextName()) {
                        "auth" -> auth = reader.text()?.toIntOrNull() ?: 1
                        "status" -> status = reader.text().orEmpty()
                        else -> reader.skipValue()
                    }
                    reader.endObject()
                } else reader.skipValue()
            }
            reader.endObject()
            if (auth == 0) error("Wrong Xtream username or password")
            if (status.isNotBlank() && !status.equals("Active", true)) error("This Xtream account is $status")
        }
    }

    /**
     * The live channels only: one small list, so they are on screen in seconds. Movies come
     * afterwards through [loadMovies], while the viewer is already watching.
     */
    fun loadLive(): List<Channel> {
        checkAccount()
        val result = ArrayList<Channel>()
        val liveGroups = categories("get_live_categories")
        streams("get_live_streams") { s ->
            val id = s["stream_id"] ?: return@streams
            // Some servers answer every list with everything; a film is never a live channel.
            if (!s.isLive()) return@streams
            val name = s["name"].orEmpty().ifBlank { "Channel $id" }
            val url = account.live(id)
            result += Channel(s["epg_channel_id"]?.takeIf { it.isNotBlank() } ?: "$name|$url", name, url,
                s["stream_icon"]?.takeIf { it.startsWith("http") }, liveGroups[s["category_id"]] ?: "Other")
        }
        if (result.isEmpty()) error("The Xtream account has no channels")
        return result
    }

    /** The account's movies; empty when the server has none or refuses the list. */
    fun loadMovies(): List<Channel> {
        val result = ArrayList<Channel>()
        val movieGroups = categories("get_vod_categories")
        streams("get_vod_streams") { s ->
            val id = s["stream_id"] ?: return@streams
            if (s["stream_type"].equals("live", ignoreCase = true)) return@streams
            val name = s["name"].orEmpty().ifBlank { "Movie $id" }
            val url = account.movie(id, s["container_extension"].orEmpty())
            result += Channel("$name|$url", name, url, s["stream_icon"]?.takeIf { it.startsWith("http") }, movieGroups[s["category_id"]] ?: "Other")
        }
        return result
    }

    /** The account's series, one title each (their episodes come from [loadEpisodes] when opened). */
    fun loadSeries(): List<Channel> {
        val result = ArrayList<Channel>()
        val groups = categories("get_series_categories")
        streams("get_series") { s ->
            val id = s["series_id"] ?: return@streams
            val name = s["name"]?.trim().orEmpty()
            if (name.isEmpty()) return@streams
            result += Shows.xtream(id, name, s["cover"]?.takeIf { it.startsWith("http") }, groups[s["category_id"]] ?: "Other")
        }
        return result
    }

    /**
     * A series' seasons and episodes (get_series_info). Servers give "episodes" either as an
     * object keyed by season number or as a list of per-season lists; both are read.
     */
    fun loadEpisodes(seriesId: String): List<Season> {
        val episodes = ArrayList<Episode>()
        read(account.api("get_series_info") + "&series_id=" + java.net.URLEncoder.encode(seriesId, "UTF-8")) { reader ->
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return@read }
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() != "episodes") { reader.skipValue(); continue }
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val season = reader.nextName().toIntOrNull() ?: 0
                            readEpisodeList(reader, season, episodes)
                        }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        var index = 0
                        while (reader.hasNext()) readEpisodeList(reader, ++index, episodes)
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        return episodes.groupBy { it.season }.toSortedMap().map { (n, list) -> Season(n, list.sortedBy { it.number }) }
    }

    private fun readEpisodeList(reader: JsonReader, season: Int, into: MutableList<Episode>) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return }
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
            val f = HashMap<String, String>()
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (key == "info" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val inner = reader.nextName()
                        if (inner == "movie_image" || inner == "plot" || inner == "duration_secs" || inner == "cover_big") reader.text()?.let { f["info." + inner] = it }
                        else reader.skipValue()
                    }
                    reader.endObject()
                } else reader.text()?.let { f[key] = it }
            }
            reader.endObject()
            val id = f["id"] ?: f["stream_id"] ?: continue
            val number = f["episode_num"]?.toDoubleOrNull()?.toInt() ?: (into.count { it.season == season } + 1)
            val inSeason = f["season"]?.toIntOrNull() ?: season
            into += Episode(
                id, inSeason, number, f["title"]?.trim().orEmpty(), account.episode(id, f["container_extension"].orEmpty()),
                (f["info.movie_image"] ?: f["info.cover_big"])?.takeIf { it.startsWith("http") },
                f["info.plot"].orEmpty().take(400), f["info.duration_secs"]?.toDoubleOrNull()?.toInt() ?: 0
            )
        }
        reader.endArray()
    }

    private fun categories(action: String): Map<String, String> {
        val groups = HashMap<String, String>()
        runCatching {
            streams(action) { c -> val id = c["category_id"]; val name = c["category_name"]; if (id != null && !name.isNullOrBlank()) groups[id] = name }
        }
        return groups
    }

    /** Each object of a JSON list, as its plain (text or number) fields. */
    private fun streams(action: String, each: (Map<String, String>) -> Unit) {
        read(account.api(action)) { reader ->
            if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return@read }
            reader.beginArray()
            val fields = HashMap<String, String>()
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
                fields.clear()
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    reader.text()?.let { fields[key] = it }
                }
                reader.endObject()
                each(fields)
            }
            reader.endArray()
        }
    }

    private fun read(url: String, block: (JsonReader) -> Unit) {
        val request = Request.Builder().url(url).header("User-Agent", agent).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Xtream server answered HTTP ${response.code}")
            val body = response.body ?: error("Xtream server sent nothing")
            JsonReader(body.charStream()).use { reader -> reader.isLenient = true; block(reader) }
        }
    }

    /**
     * A live channel or radio, by what the server says it is: its stream type when it gives one,
     * else the absence of a file type (films and episodes have one, "mp4", "mkv"...).
     */
    private fun Map<String, String>.isLive(): Boolean {
        val type = this["stream_type"]?.lowercase()
        if (!type.isNullOrBlank()) return type != "movie" && type != "series"
        return this["container_extension"].isNullOrBlank()
    }

    /** A text or number value; null for anything else (skipped). */
    private fun JsonReader.text(): String? = when (peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> { skipValue(); null }
    }
}
