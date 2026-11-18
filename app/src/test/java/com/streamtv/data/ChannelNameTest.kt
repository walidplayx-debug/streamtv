package com.streamtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelNameTest {
    @Test fun separatorsAreRecognised() {
        assertTrue(isSeparatorName("##### BEIN SPORTS SA #####"))
        assertTrue(isSeparatorName("===== SPORT ====="))
        assertTrue(isSeparatorName("★★★ MOVIES ★★★"))
        assertTrue(isSeparatorName("#####"))
        assertFalse(isSeparatorName("AR - BEIN SPORTS 1 HD"))
        assertFalse(isSeparatorName("Channel #1"))
    }

    @Test fun sharedPrefixIsFoundForAnyTag() {
        val names = listOf("AR - BEIN SPORTS 1 HD", "AR - BEIN SPORTS 2 4K", "AR - BEIN SPORTS NEWS", "BEIN XTRA")
        assertEquals("AR", sharedListPrefix(names))
        assertEquals("FR", sharedListPrefix(listOf("FR | TF1", "FR | M6", "FR | France 2")))
        assertEquals("", sharedListPrefix(listOf("BBC One", "ITV1", "Channel 4")))
    }

    @Test fun shortNamesDropTagAndQuality() {
        assertEquals("BEIN SPORTS 1", shortChannelName("AR - BEIN SPORTS 1 FHD", "AR"))
        assertEquals("BEIN SPORTS NEWS", shortChannelName("AR - BEIN SPORTS NEWS 4K", "AR"))
        assertEquals("BEIN SPORTS 2", shortChannelName("AR - BEIN SPORTS 2 HD", "AR"))
        assertEquals("BBC One", shortChannelName("BBC One (1080p)", ""))
        // Another list's tag is kept.
        assertEquals("FR | TF1", shortChannelName("FR | TF1", "AR"))
    }
}
