package com.streamtv.data

object M3uParser {
    private fun displayTitle(line: String): String {
        var quote: Char? = null
        line.forEachIndexed { index, char ->
            if ((char == '"' || char == 39.toChar()) && (quote == null || quote == char)) {
                quote = if (quote == null) char else null
            } else if (char == ',' && quote == null) {
                return line.substring(index + 1).trim()
            }
        }
        return ""
    }
    private val attribute = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")
    fun parse(text: String): List<Channel> {
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim().removeSuffix("\r") }.iterator()
        val result = mutableListOf<Channel>()
        var info: String? = null
        while (lines.hasNext()) {
            val line = lines.next()
            when {
                line.startsWith("#EXTINF", true) -> info = line
                line.isNotEmpty() && !line.startsWith("#") && info != null -> {
                    val raw = info!!
                    val attrs = attribute.findAll(raw).associate { m ->
                        m.groupValues[1].lowercase() to (m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } })
                    }
                    val title = displayTitle(raw).ifEmpty { attrs["tvg-name"] ?: "Channel ${result.size + 1}" }
                    val url = line
                    val id = attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: "$title|$url"
                    if (result.none { it.id == id && it.url == url }) {
                        result += Channel(id, title, url, attrs["tvg-logo"], attrs["group-title"] ?: "Other",
                            attrs["tvg-country"] ?: "", attrs["tvg-language"] ?: "")
                    }
                    info = null
                }
            }
        }
        return result
    }
}
