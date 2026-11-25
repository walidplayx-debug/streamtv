package com.streamtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeTest {
    private val episodes = listOf("e1", "e2", "e3", "e4")
    private fun resume(recent: List<String>, positions: Map<String, Pair<Long, Long>>) =
        Shows.resume(episodes, { it }, recent, positions)

    @Test fun startsAtTheFirstWhenNothingWasWatched() =
        assertEquals("e1" to 0L, resume(emptyList(), emptyMap()))

    @Test fun goesOnInsideAnUnfinishedEpisode() =
        assertEquals("e2" to 600_000L, resume(listOf("e2"), mapOf("e2" to (600_000L to 2_400_000L))))

    @Test fun offersTheEpisodeAfterAFinishedOne() =
        // e3 was watched to the end, so it has no saved place any more; an older unfinished one must not win.
        assertEquals("e4" to 0L, resume(listOf("e3", "e1"), mapOf("e1" to (600_000L to 2_400_000L))))

    @Test fun goesBackToTheFirstAfterTheLastEpisode() =
        assertEquals("e1" to 0L, resume(listOf("e4"), emptyMap()))

    @Test fun ignoresOtherTitles() =
        assertEquals("e2" to 0L, resume(listOf("film", "e1"), emptyMap()))
}
