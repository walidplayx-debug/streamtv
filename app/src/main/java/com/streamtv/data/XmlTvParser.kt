package com.streamtv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmlTvParser {
    private val formats = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")
    // Made once per guide: making two date formats for each of a guide's 100 000 programmes was
    // most of the time it took to read.
    private fun parseTime(value: String, readers: List<SimpleDateFormat>): Long {
        for (f in readers) try {
            return f.parse(value.trim())?.time ?: 0L
        } catch (_: Exception) { }
        return 0L
    }

    /**
     * [wanted]: keeps only programmes of these channels (by the guide's channel id). An Xtream
     * account's guide covers every channel of the server; kept whole it filled a TV's memory.
     */
    fun parse(input: InputStream, gzipped: Boolean = false, wanted: ((String) -> Boolean)? = null): List<EpgProgram> {
        val readers = formats.map { SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
        val source = if (gzipped) GZIPInputStream(input) else input
        val parser = Xml.newPullParser()
        parser.setInput(source, null)
        val result = mutableListOf<EpgProgram>()
        var channel = ""
        var start = 0L
        var end = 0L
        var title = ""
        var desc = ""
        var tag = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    // A programme may give its title (and description) in several languages, one
                    // element each; keep the first instead of running them together.
                    if ((tag == "title" && title.isNotEmpty()) || (tag == "desc" && desc.isNotEmpty())) tag = ""
                    if (tag == "programme") {
                        channel = parser.getAttributeValue(null, "channel") ?: ""
                        // A programme of another channel is passed over without reading its times.
                        if (wanted != null && channel.isNotBlank() && !wanted(channel)) channel = ""
                        if (channel.isNotBlank()) {
                            start = parseTime(parser.getAttributeValue(null, "start") ?: "", readers)
                            end = parseTime(parser.getAttributeValue(null, "stop") ?: "", readers)
                        }
                        title = ""; desc = ""
                    }
                }
                XmlPullParser.TEXT -> if (channel.isNotBlank()) { if (tag == "title") title += parser.text else if (tag == "desc") desc += parser.text }
                XmlPullParser.END_TAG -> {
                    // Text after a closing tag (line breaks, indentation) belongs to no element.
                    tag = ""
                    if (parser.name == "programme" && channel.isNotBlank() && title.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        if (end > now - 2 * 60 * 60 * 1000L && start < now + 24 * 60 * 60 * 1000L) {
                            result += EpgProgram(channel, title.trim(), desc.trim().take(400), start, end)
                        }
                    }
                }
            }
            event = parser.next()
        }
        source.close()
        return result
    }
}
