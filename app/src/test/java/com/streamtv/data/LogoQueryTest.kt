package com.streamtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LogoQueryTest {
    @Test fun saysItIsATvChannelAndWhere() =
        assertEquals("5Star TV channel logo UK", LogoSearch.logoQuery("5Star", country = "", group = "UK | Entertainment"))

    @Test fun dropsQualityAndCountryPrefixFromTheName() =
        assertEquals("MBC 1 TV channel logo Arabic", LogoSearch.logoQuery("AR: MBC 1 FHD (1080p)", country = "", group = "News"))

    @Test fun playlistCountryComesFirst() =
        assertEquals("TF1 TV channel logo France", LogoSearch.logoQuery("TF1 HD", country = "FR", group = "UK | Mixed"))

    @Test fun arabicNameMeansArabic() =
        assertEquals("الجزيرة TV channel logo Arabic", LogoSearch.logoQuery("الجزيرة", country = "", group = "News"))

    @Test fun unknownPlaceIsLeftOut() =
        assertEquals("5Star TV channel logo", LogoSearch.logoQuery("5Star", country = "", group = "Entertainment"))
}
