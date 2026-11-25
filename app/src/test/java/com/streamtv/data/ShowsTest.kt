package com.streamtv.data

import org.junit.Assert.*
import org.junit.Test

class ShowsTest {
    private fun ep(name: String, n: Int) = Channel("e$n", name, "http://h/series/u/p/$n.mkv")

    @Test fun readsShowName() {
        assertEquals("Dark", Shows.showName("Dark S01E02 - Lies"))
        assertEquals("The Office (US)", Shows.showName("The Office (US) - S02 E05"))
        assertNull(Shows.showName("Inception"))
    }

    @Test fun gathersEpisodesUnderOneTitle() {
        val list = listOf(ep("Dark S01E01", 1), ep("Dark S01E02", 2), ep("Dark S02E01", 3), ep("Lost S01E01", 4))
        val shows = Shows.of(list)
        assertEquals(listOf("Dark", "Lost"), shows.map { it.name })
        assertTrue(shows.all { it.isShow() && it.kind() == ChannelKind.Series })
        val seasons = Shows.seasonsFromPlaylist(shows.first(), list)
        assertEquals(listOf(1, 2), seasons.map { it.number })
        assertEquals(listOf(1, 2), seasons.first().episodes.map { it.number })
    }

    @Test fun keepsApiShowsAndNamesEpisodes() {
        val show = Shows.xtream("42", "Caminandes", null, "Animation")
        assertEquals("42", Shows.xtreamId(show))
        assertEquals(listOf(show), Shows.of(listOf(show, Channel("n", "News", "http://h/live/u/p/1.ts"))))
        val episode = Episode("7", 1, 3, "Llamigos", "http://h/series/u/p/7.mp4")
        val playable = Shows.playable(show, episode)
        assertEquals("Caminandes S01E03 - Llamigos", playable.name)
        assertEquals(ChannelKind.Series, playable.kind())
    }

    @Test fun episodeNumbersStayLatinOnAnArabicTv() {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale("ar", "EG"))
        try {
            val episode = Episode("7", 1, 3, "Llamigos", "http://h/series/u/p/7.mp4")
            assertEquals("Caminandes S01E03 - Llamigos", Shows.playable(Shows.xtream("42", "Caminandes", null, "Animation"), episode).name)
        } finally {
            java.util.Locale.setDefault(before)
        }
    }
}
