package com.streamtv.data

data class Channel(
    val id: String, val name: String, val url: String, val logo: String? = null,
    val group: String = "Other", val country: String = "", val language: String = ""
) {
    /** Unique per playlist entry: several streams may share one tvg-id. */
    val key: String get() = "$id|$url"
}


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
