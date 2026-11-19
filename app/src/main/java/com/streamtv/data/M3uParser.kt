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
    fun parse(text: String): List<Channel> = parse(text.lineSequence())

    /**
     * Reads the playlist line by line, so a very large one (tens of megabytes) never has to sit
     * in memory as a single string. The reader is closed when done.
     */
    fun parse(reader: java.io.Reader): List<Channel> = reader.buffered().useLines { parse(it) }

    private fun parse(source: Sequence<String>): List<Channel> {
        // A byte-order mark may start the first line; it is not whitespace, so trim() keeps it.
        val lines = source.map { it.removePrefix("\uFEFF").trim().removeSuffix("\r") }.iterator()
        val result = mutableListOf<Channel>()
        // Seen (id, url) pairs. A set keeps big playlists (tens of thousands of entries) fast.
        val seen = HashSet<String>()
        var info: String? = null
        // Player name and referrer a playlist asks for (#EXTVLCOPT lines, or attributes on #EXTINF).
        val options = LinkedHashMap<String, String>()
        while (lines.hasNext()) {
            val line = lines.next()
            when {
                line.startsWith("#EXTINF", true) -> { info = line; options.clear() }
                line.startsWith("#EXTVLCOPT", true) -> {
                    val option = line.substringAfter(':')
                    val value = option.substringAfter('=', "").trim()
                    when (option.substringBefore('=').trim().lowercase()) {
                        "http-user-agent" -> options["User-Agent"] = value
                        "http-referrer", "http-referer" -> options["Referer"] = value
                        "http-origin" -> options["Origin"] = value
                    }
                }
                line.isNotEmpty() && !line.startsWith("#") && info != null -> {
                    val raw = info
                    val attrs = attribute.findAll(raw).associate { m ->
                        m.groupValues[1].lowercase() to (m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } })
                    }
                    val title = displayTitle(raw).ifEmpty { attrs["tvg-name"] ?: "Channel ${result.size + 1}" }
                    attrs["user-agent"]?.takeIf { it.isNotBlank() }?.let { options.putIfAbsent("User-Agent", it) }
                    (attrs["http-referrer"] ?: attrs["referrer"])?.takeIf { it.isNotBlank() }?.let { options.putIfAbsent("Referer", it) }
                    // Kept on the address after a "|", where the player reads them (and where some
                    // playlists already write them).
                    val url = if (options.isEmpty() || '|' in line) line
                        else line + "|" + options.entries.joinToString("&") { (k, v) -> k + "=" + java.net.URLEncoder.encode(v, "UTF-8") }
                    val id = attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: "$title|$url"
                    if (seen.add(id + "\u0000" + url)) {
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
