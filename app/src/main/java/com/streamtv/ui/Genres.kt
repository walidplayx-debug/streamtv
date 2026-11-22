package com.streamtv.ui

import com.streamtv.data.Channel

/**
 * The ten genres the Movies page shows, the same for every provider, whatever its own categories
 * are called or however many it has. A title's genre is read from its provider category name
 * (in English, French or Arabic); a title that fits none is only under "All".
 */
internal object Genres {
    val order = listOf(
        "Action", "Comedy", "Drama", "Horror", "Romance",
        "Thriller", "Science fiction", "Animation", "Documentary", "Family"
    )

    // Checked in this order, so "Animation" wins over "Family" for "Kids animation" and so on.
    private val words: List<Pair<String, List<String>>> = listOf(
        "Documentary" to listOf("documentar", "documentaire", "وثائقي", "docu"),
        "Animation" to listOf("animation", "anime", "animé", "cartoon", "كرتون", "انمي", "أنمي", "رسوم متحركة"),
        "Horror" to listOf("horror", "horreur", "رعب", "terror", "épouvante"),
        "Science fiction" to listOf("sci-fi", "scifi", "sci fi", "science fiction", "science-fiction", "خيال علمي", "fantasy", "fantastique", "فانتازيا"),
        "Romance" to listOf("romance", "romantic", "romantique", "رومانس", "رومانسي"),
        "Comedy" to listOf("comedy", "comédie", "comedie", "كوميد", "humour", "stand-up", "standup"),
        "Thriller" to listOf("thriller", "crime", "policier", "mystery", "suspense", "إثارة", "اثارة", "جريمة", "غموض", "تشويق"),
        "Action" to listOf("action", "adventure", "aventure", "war", "guerre", "western", "martial", "أكشن", "اكشن", "حركة", "مغامرة", "حرب"),
        "Family" to listOf("family", "famille", "kids", "children", "enfant", "jeunesse", "عائلي", "أطفال", "اطفال", "disney"),
        "Drama" to listOf("drama", "drame", "دراما", "biograph", "سيرة")
    )

    private val cache = HashMap<String, String?>()

    /** The genre of a title, from its provider category; null when none fits. */
    fun of(channel: Channel): String? = synchronized(cache) {
        cache.getOrPut(channel.group) {
            val group = channel.group.lowercase()
            words.firstOrNull { (_, keys) -> keys.any { it in group } }?.first
        }
    }

    /** The titles of each genre, in [order]; genres without titles are left out. */
    fun group(titles: List<Channel>): LinkedHashMap<String, List<Channel>> {
        val byGenre = HashMap<String, MutableList<Channel>>()
        for (title in titles) of(title)?.let { byGenre.getOrPut(it) { ArrayList() } += title }
        val result = LinkedHashMap<String, List<Channel>>()
        for (genre in order) byGenre[genre]?.let { result[genre] = it }
        return result
    }

    private val year = Regex("""[(\[]?\b(19|20)\d{2}\b[)\]]?""")
    private val quality = Regex("""\b(4k|uhd|fhd|hd|sd|1080p|720p|480p|multi|vostfr|vf|vo)\b""", RegexOption.IGNORE_CASE)
    // A number or Roman numeral as a word of its own at the end ("Rocky II", "Part 2"), never the
    // last letters of a word ("Max").
    private val partEnd = Regex("""\s+(?:(?:part|partie|chapter|chapitre|الجزء|جزء|vol\.?|volume)\s*)?(\d{1,2}|ii|iii|iv|v|vi|vii|viii|ix|x)$""", RegexOption.IGNORE_CASE)

    /**
     * The name a film shares with its other parts: "Fast & Furious 7 (2015)" and "Fast & Furious 9"
     * both give "fast & furious". Null when nothing marks it as a part of something, or when what
     * is left is too short to be safe.
     */
    fun saga(name: String): String? {
        var base = name.substringBefore(":").substringBefore(" - ")
        base = quality.replace(year.replace(base, " "), " ").replace(Regex("\\s+"), " ").trim().lowercase()
        val match = partEnd.find(base) ?: return base.takeIf { it.length >= 4 && name.lowercase().trim() != base }
        return base.substring(0, match.range.first).trim().takeIf { it.length >= 4 }
    }
}
