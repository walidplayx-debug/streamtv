package com.streamtv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubStepTest {
    private val film = 2 * 60 * 60 * 1000L

    @Test fun firstPressesMoveTenSeconds() {
        assertEquals(10_000L, scrubStep(1, film))
        assertEquals(10_000L, scrubStep(2, film))
    }

    @Test fun stepGrowsWhileTheRunGoesOn() {
        assertEquals(30_000L, scrubStep(3, film))
        assertEquals(60_000L, scrubStep(6, film))
        assertEquals(120_000L, scrubStep(12, film))
        assertEquals(300_000L, scrubStep(30, film))
    }

    @Test fun shortTitleKeepsSmallSteps() {
        assertEquals(16_000L, scrubStep(30, 4 * 60 * 1000L))
        assertEquals(10_000L, scrubStep(30, 60_000L))
    }

    @Test fun unknownLengthIsNotCapped() {
        assertEquals(300_000L, scrubStep(30, 0L))
    }
}
