package com.streamtv.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

/** Credentials extracted from the Xtream get.php URL stored by the existing login screen. */
data class XtreamLogin(val base: String, val username: String, val password: String) {
    companion object {
        fun from(url: String): XtreamLogin? {
            val parsed = url.toHttpUrlOrNull() ?: return null
            if (!parsed.encodedPath.trimEnd('/').endsWith("/get.php")) return null
            val username = parsed.queryParameter("username")?.takeIf(String::isNotBlank) ?: return null
            val password = parsed.queryParameter("password")?.takeIf(String::isNotBlank) ?: return null
            val port = if (parsed.port != HttpUrl.defaultPort(parsed.scheme)) ":\u0024{parsed.port}" else ""
            val path = parsed.encodedPath.trimEnd('/').removeSuffix("/get.php")
            return XtreamLogin("\u0024{parsed.scheme}://\u0024{parsed.host}\u0024port\u0024path", username, password)
        }
    }
}

class XtreamLoginException : Exception("Wrong username or password, or the account has expired.")
class XtreamApiException(message: String) : Exception(message)

/**
 * Reads Xtream JSON incrementally: the response body is never converted to a giant String or
 * JSONObject. This initial integration imports live channels and movies; episodes are fetched
 * per series in a later stage rather than downloading the provider's full get.php M3U.
 */
class XtreamApi(private val client: OkHttpClient) {
    fun load(login: XtreamLogin): List<Channel> {
        val liveCategories = categories(login, "get_live_categories")
        val result = ArrayList<Channel>()
        read(login, "get_live_streams") { item ->
            val id = item["stream_id"] ?: return@read
            val name = item["name"]?.trim().orEmpty().ifBlank { return@read }
            result += Channel(
                id = item["epg_channel_id"]?.trim().orEmpty().ifBlank { "xtream-live-$id" },
                name = name,
                url = streamUrl(login, "live", id, "ts"),
                logo = item["stream_icon"]?.takeIf(String::isNotBlank),
                group = liveCategories[item["category_id"]] ?: "Other"
            )
        }

        // VOD is optional on some providers. Keep working live TV if its VOD endpoint is disabled.
        try {
            val vodCategories = categories(login, "get_vod_categories")
            read(login, "get_vod_streams") { item ->
                val id = item["stream_id"] ?: return@read
                val name = item["name"]?.trim().orEmpty().ifBlank { return@read }
                val extension = item["container_extension"]?.trim()?.takeIf {
                    it.matches(Regex("[A-Za-z0-9]{1,8}"))
                } ?: "mp4"
                result += Channel(
                    id = "xtream-movie-$id",
                    name = name,
                    url = streamUrl(login, "movie", id, extension),
                    logo = item["stream_icon"]?.takeIf(String::isNotBlank),
                    group = vodCategories[item["category_id"]] ?: "Other"
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A provider may allow live TV but deny VOD; don't discard the live list.
        }

        if (result.isEmpty()) throw XtreamApiException("The Xtream API returned no channels. Check the account and provider.")
        return result
    }

    private fun streamUrl(login: XtreamLogin, type: String, id: String, extension: String): String {
        fun path(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return "\u0024{login.base}/\u0024type/\u0024{path(login.username)}/\u0024{path(login.password)}/\u0024id.\u0024extension"
    }

    private fun apiUrl(login: XtreamLogin, action: String): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return "\u0024{login.base}/player_api.php?username=\u0024{enc(login.username)}&password=\u0024{enc(login.password)}&action=\u0024action"
    }

    private fun categories(login: XtreamLogin, action: String): Map<String, String> {
        val result = HashMap<String, String>()
        read(login, action) { item ->
            val id = item["category_id"]
            val name = item["category_name"]?.trim()
            if (id != null && !name.isNullOrBlank()) result[id] = name
        }
        return result
    }

    /** Each JSON object is consumed before the reusable map is cleared for the next object. */
    private fun read(login: XtreamLogin, action: String, each: (Map<String, String?>) -> Unit) {
        val request = Request.Builder().url(apiUrl(login, action))
            .header("User-Agent", "STV/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw XtreamApiException("Xtream request failed: HTTP ${response.code}")
            val body = response.body ?: throw XtreamApiException("Xtream returned an empty response.")
            JsonReader(body.charStream()).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                    // Authentication errors and many provider error payloads are JSON objects.
                    reader.skipValue()
                    throw XtreamLoginException()
                }
                reader.beginArray()
                val item = HashMap<String, String?>()
                while (reader.hasNext()) {
                    item.clear()
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                        reader.skipValue()
                        continue
                    }
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
