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
    private fun parseTime(value: String): Long {
        for (pattern in formats) try {
            val f = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return f.parse(value.trim())?.time ?: 0L
        } catch (_: Exception) { }
        return 0L
    }
    fun parse(input: InputStream, gzipped: Boolean = false): List<EpgProgram> {
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
                    if (tag == "programme") {
                        channel = parser.getAttributeValue(null, "channel") ?: ""
                        start = parseTime(parser.getAttributeValue(null, "start") ?: "")
                        end = parseTime(parser.getAttributeValue(null, "stop") ?: "")
                        title = ""; desc = ""
                    }
                }
                XmlPullParser.TEXT -> if (tag == "title") title += parser.text else if (tag == "desc") desc += parser.text
                XmlPullParser.END_TAG -> if (parser.name == "programme" && channel.isNotBlank() && title.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    if (end > now - 2 * 60 * 60 * 1000L && start < now + 24 * 60 * 60 * 1000L) {
                        result += EpgProgram(channel, title.trim(), desc.trim(), start, end)
                    }
                }
            }
            event = parser.next()
        }
        source.close()
        return result
    }
}
