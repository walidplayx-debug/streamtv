package com.streamtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test fun parsesQuotedAttributesAndChannelUrl() {
        val input = """#EXTM3U
#EXTINF:-1 tvg-id="one" tvg-name="News HD" tvg-logo="https://x/logo.png" group-title="News",News HD
https://x/live.m3u8
"""
        val parsed = M3uParser.parse(input)
        assertEquals(1, parsed.size)
        assertEquals("News HD", parsed.first().name)
        assertEquals("News", parsed.first().group)
        assertEquals("https://x/live.m3u8", parsed.first().url)
    }
    @Test fun ignoresCommentsAndMalformedEntries() {
        val parsed = M3uParser.parse("#EXTM3U\n#EXTVLCOPT:http-user-agent=x\n#EXTINF:-1,Only title\n")
        assertTrue(parsed.isEmpty())
    }
    @Test fun stripsBomAndDeduplicates() {
        val item = "#EXTINF:-1 tvg-id='a',A\nhttps://x/a\n"
        assertEquals(1, M3uParser.parse("\uFEFF#EXTM3U\n$item$item").size)
    }
}
