package com.streamtv.data

data class Channel(
    val id: String, val name: String, val url: String, val logo: String? = null,
    val group: String = "Other", val country: String = "", val language: String = ""
) {
    /** Unique per playlist entry: several streams may share one tvg-id. */
    val key: String get() = "$id|$url"
}

/**
 * Favourites are kept per playlist entry (its [Channel.key]). Older versions kept the shared id
 * instead, so taking one stream out of the favourites took out every stream with the same id;
 * an entry saved that way still counts until it is moved over.
 */
fun Set<String>.hasFavorite(channel: Channel): Boolean = channel.key in this || channel.id in this


fun Channel.detectedLanguage(): String {
    val explicit = language.lowercase()
    if (explicit == "ar" || explicit.contains("arab") || explicit.contains("ara")) return "ar"
    if (explicit == "fr" || explicit.contains("french") || explicit.contains("fre") || explicit.contains("français")) return "fr"
    val metadata = "${country} ${group}".lowercase()
    if (name.any { it in '\u0600'..'\u06FF' } || metadata.contains("arab") || metadata.contains("العربية")) return "ar"
    if (metadata.contains("french") || metadata.contains("français") || metadata.contains("francais") ||
        country.equals("FR", true) || country.contains("France", true) ||
        group.contains("France", true) || group.contains("French", true) ||
        group.contains("Français", true) || group.contains("Francais", true)) return "fr"
    return "other"
}

enum class ChannelKind { Live, Movie, Series }

// No ".flv": on playlists that is mostly a live stream (HTTP-FLV), not a file to watch from the start.
private val videoFileExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".wmv")
private val streamingManifests = listOf(".m3u8", ".mpd")
/** A registered TV channel's id ("Canal12Web.ar", "Canal12Web.ar@SD"): a channel, whatever its address ends with. */
private val tvChannelId = Regex("""^[A-Za-z0-9]+\.[a-z]{2}(@.*)?$""")
private val episodeMarker = Regex("""\bS\d{1,2}\s?E\d{1,3}\b""", RegexOption.IGNORE_CASE)

/** Playlists mix live channels with on-demand files; tell them apart by the stream address. */
fun Channel.kind(): ChannelKind {
    val path = url.substringBefore("|").substringBefore("?").lowercase()
    val file = videoFileExtensions.any { path.endsWith(it) }
    // A live playlist (HLS / DASH) stays live even under a "movies" folder: a 24/7 movie channel.
    val manifest = streamingManifests.any { path.endsWith(it) }
    val vodFolder = !manifest && (path.contains("/movie/") || path.contains("/movies/"))
    return when {
        path.contains("/series/") || (file && episodeMarker.containsMatchIn(name)) -> ChannelKind.Series
        vodFolder -> ChannelKind.Movie
        // A plain video file is a movie, unless the playlist names it as a TV channel.
        file && !tvChannelId.matches(id) -> ChannelKind.Movie
        else -> ChannelKind.Live
    }
}
