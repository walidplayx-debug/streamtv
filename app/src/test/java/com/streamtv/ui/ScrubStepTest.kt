package com.streamtv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubStepTest {
    private val film = 2 * 60 * 60 * 1000L

    @Test fun eachPressMovesTenSeconds() {
        assertEquals(10_000L, scrubStep(held = false, durationMillis = film))
    }

    @Test fun aHeldKeyMovesAtOneSteadyStepThatNeverGrows() {
        assertEquals(30_000L, scrubStep(held = true, durationMillis = film))
    }

    @Test fun shortTitleKeepsSmallSteps() {
        assertEquals(16_000L, scrubStep(held = true, durationMillis = 4 * 60 * 1000L))
        assertEquals(10_000L, scrubStep(held = true, durationMillis = 60_000L))
    }

    @Test fun unknownLengthIsNotCapped() {
        assertEquals(30_000L, scrubStep(held = true, durationMillis = 0L))
    }
}
