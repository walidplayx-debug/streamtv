package com.streamtv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {
    @Test fun withoutAPinTheDefaultOpens() = assertTrue(pinMatches("0000", ""))
    @Test fun withoutAPinOthersDoNot() = assertFalse(pinMatches("1234", ""))
    @Test fun aChosenPinReplacesTheDefault() {
        assertTrue(pinMatches("1234", "1234"))
        assertFalse(pinMatches("0000", "1234"))
    }
}
