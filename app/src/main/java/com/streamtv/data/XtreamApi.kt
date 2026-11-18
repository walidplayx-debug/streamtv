package com.streamtv.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * An Xtream Codes account, read back from the address the app saves for it
 * (`…/get.php?username=…&password=…`).
 */
class XtreamLogin(val base: String, val username: String, val password: String) {
    companion object {
        fun from(url: String): XtreamLogin? {
            val http = url.toHttpUrlOrNull() ?: return null
            if (!http.encodedPath.endsWith("/get.php")) return null
            val username = http.queryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
            val password = http.queryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
            val port = if (http.port != HttpUrl.defaultPort(http.scheme)) ":${http.port}" else ""
            return XtreamLogin("${http.scheme}://${http.host}$port${http.encodedPath.removeSuffix("/get.php")}", username, password)
        }
    }
}

/** The server answered, but not with a list: wrong login, or an expired account. */
class XtreamLoginException : Exception("Wrong username or password, or the account has expired.")

/**
 * Loads an account the way other players do, through the server's API (`player_api.php`):
 * the live channels and the movies as two compact lists, ready in seconds. The one-file M3U of
 * the same account (live, every movie and every episode) can take minutes for the server to
 * build and is too big for a TV's memory.
 */
class XtreamApi(private val client: OkHttpClient) {
    fun load(login: XtreamLogin): List<Channel> {
        val liveGroups = categories(login, "get_live_categories")
        val live = streams(login, "get_live_streams") { item ->
            val streamId = item["stream_id"] ?: return@streams null
            val name = item["name"]?.trim().orEmpty().ifBlank { return@streams null }
            // A provider's heading ("##### SPORTS #####") is not a channel.
            if (isSeparatorName(name)) return@streams null
            Channel(
                id = item["epg_channel_id"]?.trim().orEmpty().ifBlank { "xtream-live-$streamId" },
                name = name,
                url = "${login.base}/live/${login.username}/${login.password}/$streamId.ts",
                logo = item["stream_icon"]?.takeIf { it.isNotBlank() },
                group = liveGroups[item["category_id"]] ?: "Other"
            )
        }
        // Movies are a bonus: a server that refuses them still gives its live channels.
        val movies = runCatching {
            val movieGroups = categories(login, "get_vod_categories")
            streams(login, "get_vod_streams") { item ->
                val streamId = item["stream_id"] ?: return@streams null
                val name = item["name"]?.trim().orEmpty().ifBlank { return@streams null }
                val extension = item["container_extension"]?.takeIf { it.isNotBlank() } ?: "mp4"
                Channel(
                    id = "xtream-movie-$streamId",
                    name = name,
                    url = "${login.base}/movie/${login.username}/${login.password}/$streamId.$extension",
                    logo = item["stream_icon"]?.takeIf { it.isNotBlank() },
                    group = movieGroups[item["category_id"]] ?: "Other"
                )
            }
        }.getOrDefault(emptyList())
        return live + movies
    }

    private fun apiUrl(login: XtreamLogin, action: String): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return "${login.base}/player_api.php?username=${enc(login.username)}&password=${enc(login.password)}&action=$action"
    }

    /** category id -> name. */
    private fun categories(login: XtreamLogin, action: String): Map<String, String> {
        val result = HashMap<String, String>()
        read(login, action) { item ->
            val id = item["category_id"]
            val name = item["category_name"]?.trim()
            if (id != null && !name.isNullOrBlank()) result[id] = name
        }
        return result
    }

    private fun streams(login: XtreamLogin, action: String, make: (Map<String, String?>) -> Channel?): List<Channel> {
        val result = ArrayList<Channel>()
        read(login, action) { item -> make(item)?.let { result += it } }
        return result
    }

    /**
     * Reads the answer, a JSON array of objects, one object at a time (never the whole text at
     * once), handing each object's plain values, as text, to [each].
     */
    private fun read(login: XtreamLogin, action: String, each: (Map<String, String?>) -> Unit) {
        val request = Request.Builder().url(apiUrl(login, action)).header("User-Agent", "streamtv/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Xtream request failed: HTTP ${response.code}")
            val body = response.body ?: error("Xtream request failed: empty answer")
            JsonReader(body.charStream()).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_ARRAY) throw XtreamLoginException()
                reader.beginArray()
                val item = HashMap<String, String?>()
                while (reader.hasNext()) {
                    item.clear()
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        when (reader.peek()) {
                            JsonToken.STRING, JsonToken.NUMBER -> item[key] = reader.nextString()
                            JsonToken.BOOLEAN -> item[key] = reader.nextBoolean().toString()
                            JsonToken.NULL -> { reader.nextNull(); item[key] = null }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    each(item)
                }
                reader.endArray()
            }
        }
    }
}
