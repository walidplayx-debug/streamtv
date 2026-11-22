package com.streamtv.data

data class Channel(
    val id: String, val name: String, val url: String, val logo: String? = null,
    val group: String = "Other", val country: String = "", val language: String = "",
    /** The playlist gives it a running time (#EXTINF:5400): a film or an episode, never a channel. */
    val onDemand: Boolean = false,
    /** Out of 10, as the provider rates it (0: not rated). Films and series of an Xtream account. */
    val rating: Float = 0f,
    /** Year of release, when the provider gives it (0: unknown). */
    val year: Int = 0
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

private val videoFileExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".wmv", ".flv")
private val episodeMarker = Regex("""\bS\d{1,2}\s?E\d{1,3}\b""", RegexOption.IGNORE_CASE)

/** Playlists mix live channels with on-demand files; tell them apart by the stream address. */
fun Channel.kind(): ChannelKind {
    val path = url.substringBefore("|").substringBefore("?").lowercase()
    val file = videoFileExtensions.any { path.endsWith(it) }
    return when {
        // A series title (see Series.kt): opens on its seasons, never played itself.
        path.startsWith(ShowScheme) -> ChannelKind.Series
        path.contains("/series/") || (file && episodeMarker.containsMatchIn(name)) -> ChannelKind.Series
        path.contains("/movie/") || path.contains("/movies/") || file -> ChannelKind.Movie
        // A stream (HLS and the like) the playlist gives a running time: a film, or an episode.
        onDemand -> if (episodeMarker.containsMatchIn(name)) ChannelKind.Series else ChannelKind.Movie
        else -> ChannelKind.Live
    }
}
