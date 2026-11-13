package com.streamtv.data

import org.junit.Assert.*
import org.junit.Test

class M3uParserTest {
    private fun one(info: String, url: String = "https://example.test/live.m3u8") =
        M3uParser.parse("#EXTM3U\n$info\n$url\n")

    @Test fun parsesBasicChannel() { assertEquals("News", one("#EXTINF:-1,News").single().name) }
    @Test fun parsesTvgId() { assertEquals("news.id", one("#EXTINF:-1 tvg-id=\"news.id\",News").single().id) }
    @Test fun parsesTvgNameFallback() { assertEquals("Fallback", one("#EXTINF:-1 tvg-name=\"Fallback\",").single().name) }
    @Test fun parsesLogo() { assertEquals("https://x/logo.png", one("#EXTINF:-1 tvg-logo=\"https://x/logo.png\",A").single().logo) }
    @Test fun parsesGroupTitle() { assertEquals("Sports", one("#EXTINF:-1 group-title=\"Sports\",A").single().group) }
    @Test fun parsesCountry() { assertEquals("BE", one("#EXTINF:-1 tvg-country=\"BE\",A").single().country) }
    @Test fun parsesLanguage() { assertEquals("French", one("#EXTINF:-1 tvg-language=\"French\",A").single().language) }
    @Test fun parsesSingleQuotedAttributes() { assertEquals("x", one("#EXTINF:-1 tvg-id='x',A").single().id) }
    @Test fun parsesUnquotedAttributes() { assertEquals("x", one("#EXTINF:-1 tvg-id=x,A").single().id) }
    @Test fun parsesAttributeWithSpaces() { assertEquals("World News", one("#EXTINF:-1 group-title=\"World News\",A").single().group) }
    @Test fun parsesChannelUrl() { assertEquals("https://example.test/live.m3u8", one("#EXTINF:-1,A").single().url) }
    @Test fun defaultsMissingGroup() { assertEquals("Other", one("#EXTINF:-1,A").single().group) }
    @Test fun parsesMultipleChannels() {
        val result = M3uParser.parse("#EXTM3U\n#EXTINF:-1,A\nhttps://x/a\n#EXTINF:-1,B\nhttps://x/b\n")
        assertEquals(listOf("A", "B"), result.map { it.name })
    }
    @Test fun stripsUtf8Bom() { assertEquals(1, M3uParser.parse("\uFEFF#EXTM3U\n#EXTINF:-1,A\nhttps://x/a").size) }
    @Test fun handlesCrLf() { assertEquals("A", M3uParser.parse("#EXTM3U\r\n#EXTINF:-1,A\r\nhttps://x/a\r\n").single().name) }
    @Test fun ignoresVlcOptionsBetweenInfoAndUrl() {
        assertEquals(1, M3uParser.parse("#EXTM3U\n#EXTINF:-1,A\n#EXTVLCOPT:http-user-agent=x\nhttps://x/a").size)
    }
    @Test fun ignoresCommentOnlyInput() { assertTrue(M3uParser.parse("#EXTM3U\n#EXTVLCOPT:x").isEmpty()) }
    @Test fun ignoresUrlWithoutExtinf() { assertTrue(M3uParser.parse("https://x/a").isEmpty()) }
    @Test fun ignoresExtinfWithoutUrl() { assertTrue(M3uParser.parse("#EXTINF:-1,A").isEmpty()) }
    @Test fun ignoresEmptyInput() { assertTrue(M3uParser.parse("").isEmpty()) }
    @Test fun ignoresBlankLines() { assertEquals(1, M3uParser.parse("\n#EXTINF:-1,A\n\nhttps://x/a\n").size) }
    @Test fun removesExactDuplicateIdAndUrl() {
        val item = "#EXTINF:-1 tvg-id='a',A\nhttps://x/a\n"
        assertEquals(1, M3uParser.parse("#EXTM3U\n$item$item").size)
    }
    @Test fun keepsSameIdWithDifferentStreamUrl() {
        val input = "#EXTM3U\n#EXTINF:-1 tvg-id='a',A\nhttps://x/a\n#EXTINF:-1 tvg-id='a',A HD\nhttps://x/b"
        assertEquals(2, M3uParser.parse(input).size)
    }
    @Test fun supportsUppercaseExtinf() { assertEquals("A", one("#extinf:-1,A").single().name) }
    @Test fun keepsTitleWithComma() { assertEquals("Final, News", one("#EXTINF:-1,Final, News").single().name) }
    @Test fun handlesMissingTvgId() { assertTrue(one("#EXTINF:-1,A").single().id.contains("A")) }
    @Test fun parsesQuotedAndUnquotedAttributesTogether() {
        val item = one("#EXTINF:-1 tvg-id=abc group-title=\"Local News\",A").single()
        assertEquals("abc", item.id)
        assertEquals("Local News", item.group)
    }
}
