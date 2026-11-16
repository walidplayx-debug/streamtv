package com.streamtv.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Logos for channels the playlist gives none, from the free public iptv-org database.
 *
 * The index (channel id -> logo address, and channel name -> channel id) is downloaded at most
 * once a week, read as a stream so the large files never sit in memory whole, and kept on the
 * device as a small text file. Matching is then done on the device: first by the playlist's
 * tvg-id, then by the channel's name with quality marks (HD, 1080p, ...) left out.
 */
object LogoIndex {
    private const val LogosUrl = "https://iptv-org.github.io/api/logos.json"
    private const val ChannelsUrl = "https://iptv-org.github.io/api/channels.json"
    private const val MaxAgeMillis = 7L * 24 * 60 * 60 * 1000

    @Volatile private var byId: Map<String, String> = emptyMap()
    @Volatile private var byName: Map<String, String> = emptyMap()
    // Answers already worked out, per playlist entry, so a list is filled in once and not on every change.
    private val answers = java.util.concurrent.ConcurrentHashMap<String, String>()
    private const val None = ""

    val ready: Boolean get() = byId.isNotEmpty()

    /** Reads the saved index, refreshing it first when it is missing or over a week old. True when an index is ready. */
    suspend fun load(context: Context): Boolean = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "logo-index.tsv")
        val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < MaxAgeMillis
        // A failed refresh keeps the old index (or none); the app works the same without it.
        if (!fresh) runCatching { download(file) }
        if (!file.isFile) return@withContext false
        runCatching { read(file) }.isSuccess && ready
    }

    /** The playlist entries, with a logo filled in where the playlist had none and the index has one. */
    fun fill(channels: List<Channel>): List<Channel> {
        if (!ready) return channels
        return channels.map { channel ->
            if (!channel.logo.isNullOrBlank()) channel
            else {
                val url = answers.getOrPut(channel.key) { lookup(channel) ?: None }
                if (url == None) channel else channel.copy(logo = url)
            }
        }
    }

    private fun lookup(channel: Channel): String? {
        val id = channel.id.trim()
        byId[id]?.let { return it }
        // iptv-org ids may carry a feed: "France3.fr@Paris".
        byId[id.substringBefore('@')]?.let { return it }
        val name = normalize(channel.name)
        if (name.length < 2) return null
        return byName[name]?.let { byId[it] }
    }

    private val bracketed = Regex("""[(\[].*?[)\]]""")
    private val quality = Regex("""\b(hd|fhd|uhd|sd|4k|8k|hevc|h265|h264|backup|\d{3,4}p)\b""")

    /** "MBC 3 HD (1080p)" and "mbc3" both become "mbc3". Arabic and other letters are kept. */
    fun normalize(name: String): String =
        name.lowercase().replace(bracketed, " ").replace(quality, " ").filter { it.isLetterOrDigit() }

    private fun read(file: File) {
        val ids = HashMap<String, String>()
        val names = HashMap<String, String>()
        file.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size == 3) when (parts[0]) {
                "i" -> ids[parts[1]] = parts[2]
                "n" -> names.putIfAbsent(parts[1], parts[2])
            }
        }
        byId = ids
        byName = names
        answers.clear()
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "STV-Android-App")
        }

    private fun download(target: File) {
        // 1. One logo per channel: in use, not SVG (the app cannot draw SVG), the main feed first.
        val logos = HashMap<String, String>()
        val ranks = HashMap<String, Int>()
        val logoConnection = open(LogosUrl)
        try {
            if (logoConnection.responseCode !in 200..299) return
            JsonReader(InputStreamReader(logoConnection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var channel: String? = null
                    var feed: String? = null
                    var inUse = true
                    var format = ""
                    var url: String? = null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "channel" -> channel = reader.stringOrNull()
                            "feed" -> feed = reader.stringOrNull()
                            "in_use" -> inUse = if (reader.peek() == JsonToken.BOOLEAN) reader.nextBoolean() else { reader.skipValue(); true }
                            "format" -> format = reader.stringOrNull().orEmpty()
                            "url" -> url = reader.stringOrNull()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (channel == null || url == null || !url.startsWith("http")) continue
                    if (format.equals("SVG", true) || url.endsWith(".svg", true)) continue
                    val rank = (if (inUse) 2 else 0) + (if (feed == null) 1 else 0)
                    if (rank > (ranks[channel] ?: -1)) {
                        ranks[channel] = rank
                        logos[channel] = url
                    }
                }
                reader.endArray()
            }
        } finally {
            logoConnection.disconnect()
        }
        if (logos.isEmpty()) return

        // 2. Names (and other names) of the channels that have a logo.
        val names = HashMap<String, String>()
        val channelConnection = open(ChannelsUrl)
        try {
            if (channelConnection.responseCode in 200..299) {
                JsonReader(InputStreamReader(channelConnection.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var id: String? = null
                        val found = ArrayList<String>(2)
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "id" -> id = reader.stringOrNull()
                                "name" -> reader.stringOrNull()?.let { found += it }
                                "alt_names" -> if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    reader.beginArray()
                                    while (reader.hasNext()) reader.stringOrNull()?.let { found += it }
                                    reader.endArray()
                                } else reader.skipValue()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (id == null || id !in logos) continue
                        for (name in found) {
                            val key = normalize(name)
                            if (key.length >= 2) names.putIfAbsent(key, id)
                        }
                    }
                    reader.endArray()
                }
            }
        } finally {
            channelConnection.disconnect()
        }

        // 3. Saved as a small text file; written aside first so a cut-off download never leaves half an index.
        val temp = File(target.parentFile, target.name + ".part")
        temp.bufferedWriter().use { out ->
            for ((id, url) in logos) {
                if ('\t' in id || '\n' in id || '\t' in url || '\n' in url) continue
                out.write("i\t$id\t$url\n")
            }
            for ((name, id) in names) {
                if ('\t' in name || '\n' in name) continue
                out.write("n\t$name\t$id\n")
            }
        }
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    private fun JsonReader.stringOrNull(): String? =
        if (peek() == JsonToken.STRING) nextString() else { skipValue(); null }
}
