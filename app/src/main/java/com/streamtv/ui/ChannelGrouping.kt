package com.streamtv.ui

import com.streamtv.data.Channel
import com.streamtv.data.detectedLanguage
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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

/** One entry of the side list: what is saved as the choice, what is shown, its channels and, for a country, its code (for the flag). */
class ChannelSection(val token: String, val title: String, val channels: List<Channel>, val flag: String? = null)

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
    GroupMode.Language -> {
        val shownIn = Locale.forLanguageTag(appLanguage)
        // The app's language first, then Arabic, then the rest by size.
        ranked(live, { code -> if (code == appLanguage) 0 else if (code == "ar") 1 else 2 }) { languagesOf(it) }.map { (code, list) ->
            ChannelSection("__lang:$code", if (code == OTHER) "Other" else languageName(code, shownIn), list)
        }
    }
    GroupMode.Country -> {
        val shownIn = Locale.forLanguageTag(appLanguage)
        // Countries speaking the app's language first, then the Arab countries, then the rest; by size within each.
        ranked(live, { code -> CountryLanguage[code].let { if (it == appLanguage) 0 else if (it == "ar") 1 else 2 } }) { countriesOf(it) }.map { (code, list) ->
            if (code == OTHER) ChannelSection("__country:$code", "Other", list)
            else ChannelSection("__country:$code", countryName(code, shownIn), list, flag = code)
        }
    }
    GroupMode.Genre -> {
        val byGenre = LinkedHashMap<String, MutableList<Channel>>()
        for (channel in live) for (genre in genresOf(channel)) byGenre.getOrPut(genre) { mutableListOf() } += channel
        (Genres.map { it.first } + "Other").mapNotNull { name -> byGenre[name]?.let { ChannelSection("__genre:$name", name, it) } }
    }
}

private const val OTHER = "zz"

private fun ranked(live: List<Channel>, rank: (String) -> Int, keys: (Channel) -> Set<String>): List<Pair<String, List<Channel>>> {
    val groups = HashMap<String, MutableList<Channel>>()
    for (channel in live) for (key in keys(channel).ifEmpty { setOf(OTHER) }) groups.getOrPut(key) { mutableListOf() } += channel
    return groups.entries.sortedWith(
        compareBy<Map.Entry<String, MutableList<Channel>>> { it.key == OTHER }.thenBy { rank(it.key) }.thenByDescending { it.value.size }
    ).map { it.key to it.value }
}

/**
 * A short code in front of a provider's category: "AR | MBC", "FR: Sport", "[UK] News". Only the
 * category is read: in channel names such prefixes are too often words ("TV: ...").
 */
private val prefixCode = Regex("""^\W*([A-Za-z]{2,3})\s*[|:\]]""")
private fun prefixOf(channel: Channel): String? = prefixCode.find(channel.group)?.groupValues?.get(1)?.lowercase()

/** Public playlists (iptv-org) end each channel id with its country: "BBCNews.uk@SD", "AlJazeera.qa". */
private val idCountry = Regex("""\.([a-z]{2})(?:@|$)""", RegexOption.IGNORE_CASE)

/** English names, native names and ISO codes (2 and 3 letters) of every language Android knows. */
private val languageCodes: Map<String, String> by lazy {
    val map = HashMap<String, String>()
    for (locale in Locale.getAvailableLocales()) {
        val code = locale.language.takeIf { it.length == 2 } ?: continue
        map[code] = code
        runCatching { map[locale.isO3Language] = code }
        map[locale.getDisplayLanguage(Locale.ENGLISH).lowercase()] = code
        map[locale.getDisplayLanguage(locale).lowercase()] = code
    }
    map["fre"] = "fr"; map["ger"] = "de"; map["dut"] = "nl"; map["per"] = "fa"; map["chi"] = "zh"; map["farsi"] = "fa"
    map
}

/**
 * Channels whose language is not their country's, from the iptv-org database (made by
 * tools/gen_channel_languages.py): "RTArabic.ru" -> "ara", "MBC1USA.us" -> "ara",
 * "France24.fr@Arabic" -> "ara". A feed marked "-" speaks its country's language after all.
 */
private val idLanguages: Map<String, String> by lazy {
    runCatching {
        object {}.javaClass.getResourceAsStream("/channel_languages.tsv")!!.bufferedReader().useLines { lines ->
            lines.mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        }
    }.getOrDefault(emptyMap())
}

/** The language a channel's id is filed under in that table, if any. */
private fun idLanguagesOf(channel: Channel): Set<String> {
    // Only a real channel id ("Name.cc" or "Name.cc@Feed"), not the stand-in made from the name and address.
    if (channel.id.isBlank() || '/' in channel.id || '|' in channel.id) return emptySet()
    val value = idLanguages[channel.id] ?: idLanguages[channel.id.substringBefore('@')] ?: return emptySet()
    if (value == "-") return emptySet()
    return value.split(';').mapNotNull { languageCodes[it] }.toSet()
}

/** A language said in the channel's own name: "RT Arabic", "Sky News Arabia", "Al Jazeera English". */
private val nameLanguageWords = mapOf(
    "arabic" to "ar", "arabia" to "ar", "arabi" to "ar", "عربي" to "ar", "العربية" to "ar",
    "english" to "en", "french" to "fr", "français" to "fr", "francais" to "fr",
    "spanish" to "es", "español" to "es", "espanol" to "es", "deutsch" to "de", "german" to "de",
    "russian" to "ru", "persian" to "fa", "farsi" to "fa", "turkish" to "tr", "türkçe" to "tr",
    "urdu" to "ur", "hindi" to "hi", "kurdish" to "ku", "portuguese" to "pt", "português" to "pt",
    "italian" to "it", "italiano" to "it"
)

private val nonLetters = Regex("""[^\p{L}]+""")

private fun nameLanguagesOf(channel: Channel): Set<String> =
    channel.name.lowercase().split(nonLetters).mapNotNull { nameLanguageWords[it] }.toSet()

private fun languagesOf(channel: Channel): Set<String> {
    val tagged = channel.language.split(';', ',', '/').mapNotNull { languageCodes[it.trim().lowercase()] }.toSet()
    if (tagged.isNotEmpty()) return tagged
    // The name says it ("RT Arabic"), or the channel is known to speak another language than its
    // country's ("RTArabic.ru", "MBC1USA.us"): either beats the guess from the country.
    nameLanguagesOf(channel).takeIf { it.isNotEmpty() }?.let { return it }
    idLanguagesOf(channel).takeIf { it.isNotEmpty() }?.let { return it }
    // Arabic letters in the name settle it.
    if (channel.name.any { it in '\u0600'..'\u06FF' }) return setOf("ar")
    when (val prefix = prefixOf(channel)) {
        null -> Unit
        "uk", "us", "gb" -> return setOf("en")
        else -> languageCodes[prefix]?.let { return setOf(it) }
    }
    // Otherwise the main language of the channel's country, where that is clear.
    countriesOf(channel).mapNotNull { CountryLanguage[it] }.toSet().takeIf { it.isNotEmpty() }?.let { return it }
    return when (channel.detectedLanguage()) {
        "ar" -> setOf("ar")
        "fr" -> setOf("fr")
        else -> emptySet()
    }
}

/** The main language of countries where one language clearly leads (bilingual ones are left out). */
private val CountryLanguage: Map<String, String> = buildMap {
    for (c in listOf("SA", "AE", "EG", "QA", "KW", "BH", "OM", "JO", "LB", "SY", "IQ", "PS", "YE", "LY", "TN", "DZ", "MA", "SD", "MR")) put(c, "ar")
    for (c in listOf("FR", "MC", "SN", "CI")) put(c, "fr")
    for (c in listOf("GB", "US", "AU", "NZ", "IE", "JM", "NG", "GH", "KE", "ZA", "PH")) put(c, "en")
    for (c in listOf("DE", "AT", "LI")) put(c, "de")
    for (c in listOf("ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "BO", "PY", "UY", "CR", "PA", "DO", "GT", "HN", "NI", "SV", "CU", "PR")) put(c, "es")
    for (c in listOf("PT", "BR", "AO", "MZ")) put(c, "pt")
    for (c in listOf("IT", "SM")) put(c, "it")
    put("NL", "nl"); put("TR", "tr"); put("RU", "ru"); put("CN", "zh"); put("TW", "zh"); put("HK", "zh")
    put("IR", "fa"); put("PK", "ur"); put("IN", "hi"); put("GR", "el"); put("CY", "el"); put("HR", "hr"); put("RO", "ro")
    put("PL", "pl"); put("UA", "uk"); put("CZ", "cs"); put("SK", "sk"); put("HU", "hu"); put("BG", "bg"); put("RS", "sr")
    put("SE", "sv"); put("NO", "no"); put("DK", "da"); put("FI", "fi"); put("JP", "ja"); put("KR", "ko"); put("VN", "vi")
    put("TH", "th"); put("ID", "id"); put("MY", "ms"); put("IL", "he"); put("AL", "sq"); put("XK", "sq"); put("AM", "hy")
    put("GE", "ka"); put("AZ", "az"); put("BD", "bn"); put("LK", "si"); put("NP", "ne"); put("MN", "mn"); put("SI", "sl")
    put("BA", "bs"); put("MK", "mk"); put("LT", "lt"); put("LV", "lv"); put("EE", "et"); put("IS", "is"); put("AF", "fa")
}

/** A language named in the app's language: "Spanish" in English, "الإسبانية" in Arabic, "Espagnol" in French. */
private fun languageName(code: String, shownIn: Locale): String =
    Locale(code).getDisplayLanguage(shownIn).ifBlank { code }.replaceFirstChar { it.titlecase(shownIn) }

private val countryCodes: Set<String> by lazy { Locale.getISOCountries().toSet() }

private fun countriesOf(channel: Channel): Set<String> {
    val tagged = channel.country.split(';', ',', '/').map { it.trim().uppercase() }
        .map { if (it == "UK") "GB" else it }.filter { it in countryCodes }.toSet()
    if (tagged.isNotEmpty()) return tagged
    // Only a real channel id ("Name.cc"), not the stand-in made from the name and address.
    if ('/' !in channel.id && '|' !in channel.id) idCountry.find(channel.id)?.groupValues?.get(1)?.uppercase()?.let { code ->
        val country = if (code == "UK") "GB" else code
        if (country in countryCodes) return setOf(country)
    }
    // "AR | " in IPTV lists means Arabic, not Argentina, so it says nothing about a country.
    val prefix = prefixOf(channel)?.uppercase()?.let { if (it == "UK") "GB" else it } ?: return emptySet()
    return if (prefix != "AR" && prefix in countryCodes) setOf(prefix) else emptySet()
}

/** A country named in the app's language: "France", "السعودية". */
private fun countryName(code: String, shownIn: Locale): String = Locale("", code).getDisplayCountry(shownIn).ifBlank { code }

/** The system's emoji flag, shown only when the flat flag picture cannot be loaded. */
fun emojiFlag(code: String): String = code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

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

/**
 * A country's flag, flat with softly rounded corners (the Twitter look) rather than the system's
 * wavy emoji: a small picture from flagcdn.com, fetched once and kept in the image cache. Without
 * a connection the emoji flag stands in.
 */
@androidx.compose.runtime.Composable
fun CountryFlag(code: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    var failed by androidx.compose.runtime.remember(code) { androidx.compose.runtime.mutableStateOf(false) }
    if (failed) {
        androidx.compose.material3.Text(emojiFlag(code), modifier, fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp))
    } else {
        coil.compose.AsyncImage(
            model = "https://flagcdn.com/w80/${code.lowercase()}.png",
            contentDescription = null,
            modifier = modifier.size(24.dp, 16.dp).clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            onError = { failed = true }
        )
    }
}
