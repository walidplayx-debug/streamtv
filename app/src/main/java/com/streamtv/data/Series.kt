package com.streamtv.data

/**
 * A series is shown as one title (its poster) and opens on its seasons and episodes. The title is
 * a [Channel] whose address starts with [ShowScheme]: it is never played itself.
 *
 * - An Xtream account's series come from its API ("stv-show:xtream:<series id>"); their episodes
 *   are asked for only when the series is opened, as the big apps do.
 * - Episodes in an M3U playlist ("Show S01E02") are gathered under one title per show
 *   ("stv-show:m3u:<show name>"), their seasons and episodes read from the names.
 */
const val ShowScheme = "stv-show:"

fun Channel.isShow(): Boolean = url.startsWith(ShowScheme)

data class Episode(
    val id: String, val season: Int, val number: Int, val title: String, val url: String,
    val image: String? = null, val plot: String = "", val seconds: Int = 0
)

data class Season(val number: Int, val episodes: List<Episode>)

/** What the provider tells about one film or series, asked for when its page opens. */
data class TitleInfo(val genres: List<String>, val plot: String, val year: Int, val rating: Float)

/** A series' page: its seasons, and what the provider tells about it. */
data class ShowDetails(val seasons: List<Season>, val info: TitleInfo?)

object Shows {
    private val marker = Regex("""\bS(\d{1,2})\s?E(\d{1,3})\b""", RegexOption.IGNORE_CASE)

    fun xtream(seriesId: String, name: String, cover: String?, group: String, rating: Float = 0f, year: Int = 0) =
        Channel("show:$seriesId", name, "${ShowScheme}xtream:$seriesId", cover, group, onDemand = true, rating = rating, year = year)

    fun xtreamId(show: Channel): String? = show.url.removePrefix(ShowScheme).takeIf { it.startsWith("xtream:") }?.removePrefix("xtream:")

    /** "Show Name S01E02 - Title" → "Show Name"; null for a name without an episode marker. */
    fun showName(episode: String): String? {
        val match = marker.find(episode) ?: return null
        return episode.substring(0, match.range.first).trim().trimEnd('-', '|', ':', '.', '_', ' ').trim().takeIf { it.isNotEmpty() }
    }

    /**
     * The Series page's titles: the playlist's shows (from the API), then one title per show for
     * episodes listed one by one. An episode whose name gives no show stays a title of its own.
     */
    fun of(channels: List<Channel>): List<Channel> {
        val result = ArrayList<Channel>()
        val seen = HashSet<String>()
        val byShow = LinkedHashMap<String, Channel>()
        for (c in channels) {
            if (c.kind() != ChannelKind.Series) continue
            if (c.isShow()) { if (seen.add(c.key)) result += c; continue }
            val name = showName(c.name)
            if (name == null) { result += c; continue }
            byShow.getOrPut(name.lowercase()) {
                Channel("show:m3u:$name", name, "${ShowScheme}m3u:$name", c.logo, c.group, onDemand = true)
            }
        }
        result += byShow.values
        return result
    }

    /** The seasons of a show gathered from a playlist's own episode entries. */
    fun seasonsFromPlaylist(show: Channel, channels: List<Channel>): List<Season> {
        val name = show.url.removePrefix(ShowScheme).removePrefix("m3u:").lowercase()
        val episodes = ArrayList<Episode>()
        for (c in channels) {
            if (c.isShow() || c.kind() != ChannelKind.Series) continue
            if (showName(c.name)?.lowercase() != name) continue
            val match = marker.find(c.name) ?: continue
            val season = match.groupValues[1].toIntOrNull() ?: 1
            val number = match.groupValues[2].toIntOrNull() ?: 0
            val title = c.name.substring(match.range.last + 1).trim().trimStart('-', '|', ':', '.').trim()
            episodes += Episode(c.key, season, number, title, c.url, c.logo)
        }
        return episodes.groupBy { it.season }.toSortedMap().map { (n, list) -> Season(n, list.sortedBy { it.number }) }
    }

    /** What the player gets for an episode: a playable entry named after the show and episode. */
    fun playable(show: Channel, episode: Episode): Channel {
        val label = "%s S%02dE%02d".format(show.name, episode.season, episode.number) +
            (episode.title.takeIf { it.isNotBlank() && !it.equals(show.name, true) }?.let { " - $it" } ?: "")
        return Channel("ep:${episode.id}", label, episode.url, episode.image ?: show.logo, show.group, onDemand = true)
    }
}
