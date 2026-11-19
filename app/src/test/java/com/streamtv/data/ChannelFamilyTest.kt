package com.streamtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelFamilyTest {
    private fun big(name: String) = channelFamily(name)?.big

    @Test fun numbersAreRead() {
        assertEquals("3", big("AR - BEIN SPORTS 3 HD"))
        assertEquals("1", big("beIN Sports 1 4K"))
        assertEquals("2", big("SSC 2 FHD"))
        assertEquals("1", big("AD Sports 1"))
        assertEquals("4", big("Alkass Four 4 HD"))
    }

    @Test fun channelsWithoutNumberShowAWord() {
        assertEquals("NEWS", big("AR - BEIN SPORTS NEWS 4K"))
        assertEquals("GLOBAL", big("beIN Sports Global FHD"))
        assertEquals("", big("AR - BEIN SPORTS 4K"))
    }

    @Test fun familiesAndOthers() {
        assertEquals("bein", channelFamily("BEIN SPORTS 1")?.key)
        assertEquals("ssc", channelFamily("SSC SPORT 1")?.key)
        assertEquals("SPORTS", channelFamily("BEIN SPORTS 1")?.label)
        assertNull(channelFamily("Sky Sports Main Event"))
        assertNull(channelFamily("Al Jazeera"))
    }
}
