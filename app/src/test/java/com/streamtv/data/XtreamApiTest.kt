package com.streamtv.data

import org.junit.Assert.*
import org.junit.Test

class XtreamApiTest {
    @Test fun recognizesXtreamGetPhpUrl() {
        val login = XtreamLogin.from("http://provider.example/get.php?username=walid&password=secret&type=m3u_plus")
        assertNotNull(login)
        assertEquals("http://provider.example", login!!.base)
        assertEquals("walid", login.username)
        assertEquals("secret", login.password)
    }

    @Test fun preservesProviderPathPrefixAndPort() {
        val login = XtreamLogin.from("https://provider.example:8443/iptv/get.php?username=user&password=pass")
        assertNotNull(login)
        assertEquals("https://provider.example:8443/iptv", login!!.base)
    }

    @Test fun rejectsNonXtreamPlaylistUrls() {
        assertNull(XtreamLogin.from("https://provider.example/playlist.m3u"))
        assertNull(XtreamLogin.from("https://provider.example/get.php?username=user"))
        assertNull(XtreamLogin.from("https://provider.example/get.php?password=pass"))
    }

    @Test fun rejectsBlankCredentials() {
        assertNull(XtreamLogin.from("https://provider.example/get.php?username=%20&password=pass"))
        assertNull(XtreamLogin.from("https://provider.example/get.php?username=user&password="))
    }
}
