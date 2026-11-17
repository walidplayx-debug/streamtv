package com.streamtv.ui

import com.streamtv.data.Channel
import com.streamtv.data.detectedLanguage
import java.util.Locale

/** How the Live TV side list divides the channels; chosen in Settings › Playlist. */
enum class GroupMode(val key: String, val label: String, val heading: String) {
    Provider("provider", "Provider", "BY CATEGORY"),
    Language("language", "Language", "BY LANGUAGE"),
    Country("country", "Country", "BY COUNTRY"),
    Genre("genre", "Genre", "BY GENRE");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Provider
    }
}

/** One entry of the side list: what is saved as the choice, what is shown, and its channels. */
class ChannelSection(val token: String, val title: String, val channels: List<Channel>)

/**
 * The side list's sections for a mode. A channel can sit in several (two languages, two
 * countries, two genres); what cannot be placed goes to "Other", always last.
 * Provider: the playlist's own categories, in its order (the token is the category name).
 * Language and Country: from the playlist's language and country tags; failing those, from a
 * prefix such as "AR | " or "FR: " on the category or the name; most channels first.
 * Genre: the categories mapped onto a short set (News, Sports...), combined ones like
 * "Comedy;Kids" split, in a fixed order.
 */
fun channelSections(live: List<Channel>, mode: GroupMode, appLanguage: String): List<ChannelSection> = when (mode) {
    GroupMode.Provider -> live.groupBy { it.group.ifBlank { "Other" } }.map { (name, list) -> ChannelSection(name, name, list) }
    GroupMode.Language -> ranked(live, "__lang:") { languagesOf(it) }.map { (code, list) ->
        ChannelSection("__lang:$code", if (code == OTHER) "Other" else languageName(code), list)
    }
    GroupMode.Country -> {
        val shownIn = Locale.forLanguageTag(appLanguage)
        ranked(live, "__country:") { countriesOf(it) }.map { (code, list) ->
            ChannelSection("__country:$code", if (code == OTHER) "Other" else countryName(code, shownIn), list)
        }
    }
    GroupMode.Genre -> {
        val byGenre = LinkedHashMap<String, MutableList<Channel>>()
        live.forEach { channel -> genresOf(channel).forEach { byGenre.getOrPut(it) { mutableListOf() } += channel } }
        (Genres.map { it.first } + "Other").mapNotNull { name -> byGenre[name]?.let { ChannelSection("__genre:$name", name, it) } }
    }
}

private const val OTHER = "zz"

private fun ranked(live: List<Channel>, prefix: String, keys: (Channel) -> Set<String>): List<Pair<String, List<Channel>>> {
    val groups = HashMap<String, MutableList<Channel>>()
    live.forEach { channel -> keys(channel).ifEmpty { setOf(OTHER) }.forEach { groups.getOrPut(it) { mutableListOf() } += channel } }
    return groups.entries.sortedWith(compareBy<Map.Entry<String, MutableList<Channel>>> { it.key == OTHER }.thenByDescending { it.value.size })
        .map { it.key to it.value }
}

/** A short code in front of a category or name: "AR | MBC", "FR: TF1", "[UK] BBC". */
private val prefixCode = Regex("""^\W*([A-Za-z]{2,3})\s*[|:\]\-–]""")
private fun prefixOf(channel: Channel): String? =
    (prefixCode.find(channel.group) ?: prefixCode.find(channel.name))?.groupValues?.get(1)?.lowercase()

/** English names, native names and ISO codes (2 and 3 letters) of every language Android knows. */
private val languageCodes: Map<String, String> by lazy {
    val map = HashMap<String, String>()
    Locale.getAvailableLocales().forEach { locale ->
        val code = locale.language.takeIf { it.length == 2 } ?: return@forEach
        map[code] = code
        runCatching { map[locale.isO3Language] = code }
        map[locale.getDisplayLanguage(Locale.ENGLISH).lowercase()] = code
        map[locale.getDisplayLanguage(locale).lowercase()] = code
    }
    map["fre"] = "fr"; map["ger"] = "de"; map["dut"] = "nl"; map["per"] = "fa"; map["chi"] = "zh"; map["farsi"] = "fa"
    map
}

private fun languagesOf(channel: Channel): Set<String> {
    val tagged = channel.language.split(';', ',', '/').mapNotNull { languageCodes[it.trim().lowercase()] }.toSet()
    if (tagged.isNotEmpty()) return tagged
    when (val prefix = prefixOf(channel)) {
        null -> Unit
        "uk", "us", "gb" -> return setOf("en")
        else -> languageCodes[prefix]?.let { return setOf(it) }
    }
    return when (channel.detectedLanguage()) {
        "ar" -> setOf("ar")
        "fr" -> setOf("fr")
        else -> emptySet()
    }
}

/** A language's own name, as its speakers write it: العربية, Français, English. */
private fun languageName(code: String): String {
    val locale = Locale(code)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

private val countryCodes: Set<String> by lazy { Locale.getISOCountries().toSet() }

private fun countriesOf(channel: Channel): Set<String> {
    val tagged = channel.country.split(';', ',', '/').map { it.trim().uppercase() }
        .map { if (it == "UK") "GB" else it }.filter { it in countryCodes }.toSet()
    if (tagged.isNotEmpty()) return tagged
    // "AR | " in IPTV lists means Arabic, not Argentina, so it says nothing about a country.
    val prefix = prefixOf(channel)?.uppercase()?.let { if (it == "UK") "GB" else it } ?: return emptySet()
    return if (prefix != "AR" && prefix in countryCodes) setOf(prefix) else emptySet()
}

/** A country with its flag, named in the app's language: "🇫🇷 France", "🇸🇦 السعودية". */
private fun countryName(code: String, shownIn: Locale): String {
    val flag = code.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
    return "$flag  " + Locale("", code).getDisplayCountry(shownIn).ifBlank { code }
}

/** The genres, in the order the side list shows them, with words that point to each. */
private val Genres = listOf(
    "News" to listOf("news", "info", "actual", "أخبار", "اخبار", "nachrichten", "noticia", "haber", "legislat", "politi"),
    "Sports" to listOf("sport", "رياض", "deporte", "spor", "football", "foot"),
    "Movies" to listOf("movie", "film", "cinema", "ciné", "cine", "أفلام", "افلام", "سينما", "kino"),
    "Series" to listOf("series", "série", "serie", "مسلسل", "dizi", "drama"),
    "Kids" to listOf("kid", "child", "enfant", "cartoon", "anime", "animation", "أطفال", "اطفال", "kinder", "çocuk", "infantil"),
    "Music" to listOf("music", "musique", "أغاني", "اغاني", "موسيق", "müzik", "radio"),
    "Documentary" to listOf("docu", "وثائق", "belgesel", "nature", "history", "histoire"),
    "Religious" to listOf("relig", "islam", "quran", "coran", "قرآن", "قران", "ديني", "إسلام", "christian", "church", "catholic", "bible"),
    "Entertainment" to listOf("entertain", "comedy", "family", "classic", "divert", "variet", "منوعات", "ترفيه", "show", "عائل"),
    "General" to listOf("general", "généraliste", "عامة", "public"),
    "Business" to listOf("business", "econom", "finance", "اقتصاد", "bourse", "market"),
    "Education" to listOf("educat", "science", "culture", "learn", "تعليم", "ثقاف", "علوم"),
    "Lifestyle" to listOf("lifestyle", "travel", "cook", "food", "outdoor", "auto", "weather", "fashion", "طبخ", "سفر"),
    "Shopping" to listOf("shop", "تسوق")
)

private fun genresOf(channel: Channel): Set<String> {
    val found = channel.group.split(';').mapNotNull { part ->
        val text = part.lowercase()
        Genres.firstOrNull { (_, words) -> words.any { it in text } }?.first
    }.toSet()
    return found.ifEmpty { setOf("Other") }
}
