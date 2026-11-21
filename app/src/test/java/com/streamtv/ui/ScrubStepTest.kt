package com.streamtv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubStepTest {
    private val film = 2 * 60 * 60 * 1000L

    @Test fun firstPressesMoveTenSeconds() {
        assertEquals(10_000L, scrubStep(1, 0L, film))
        assertEquals(10_000L, scrubStep(3, 900L, film))
    }

    @Test fun stepGrowsWhileTheRunGoesOn() {
        assertEquals(20_000L, scrubStep(4, 1_000L, film))
        assertEquals(60_000L, scrubStep(20, 4_000L, film))
        assertEquals(120_000L, scrubStep(40, 6_000L, film))
        assertEquals(300_000L, scrubStep(80, 9_000L, film))
    }

    @Test fun shortTitleKeepsSmallSteps() {
        val episode = 4 * 60 * 1000L
        assertEquals(12_000L, scrubStep(80, 9_000L, episode))
        assertEquals(10_000L, scrubStep(80, 9_000L, 60_000L))
    }

    @Test fun unknownLengthIsNotCapped() {
        assertEquals(300_000L, scrubStep(80, 9_000L, 0L))
    }
}
