package com.streamtv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistGuideTest {
    private val a = "http://tv.example:8080/get.php?username=anna&password=1&type=m3u_plus"
    private val b = "http://tv.example:8080/get.php?username=ben&password=2&type=m3u_plus"
    private val guideOfA = "http://tv.example:8080/xmltv.php?username=anna&password=1"

    @Test fun theOldAccountsGuideGoesWithIt() = assertTrue(cameWithPlaylist(guideOfA, a))
    @Test fun aGuideOfAnotherAccountStays() = assertFalse(cameWithPlaylist(guideOfA, b))
    @Test fun aGuideTheViewerTypedStays() = assertFalse(cameWithPlaylist("http://epg.example/guide.xml", a))
    @Test fun noGuideNothingToDrop() = assertFalse(cameWithPlaylist("", a))
}
