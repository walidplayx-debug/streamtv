package com.streamtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelKindTest {
    private fun kind(url: String, id: String = "", name: String = "Channel") = Channel(id, name, url).kind()

    @Test fun httpFlvIsLive() =
        assertEquals(ChannelKind.Live, kind("https://stream-cdn-iad.vaughnsoft.net/play/live_a_neflix01.flv", "AneflixLive.us@HD"))

    @Test fun liveHlsUnderMoviesFolderIsLive() =
        assertEquals(ChannelKind.Live, kind("http://api.toonamiaftermath.com:3000/movies/playlist.m3u8", "ToonamiAftermathMovies.us"))

    @Test fun videoFileOfARegisteredChannelIsLive() =
        assertEquals(ChannelKind.Live, kind("https://nd106.republicaservers.com:4433/c7827.mp4", "Canal12Web.ar@SD"))

    @Test fun xtreamMovieIsMovie() =
        assertEquals(ChannelKind.Movie, kind("http://host/movie/user/pass/123.mkv", "xtream-movie-123"))

    @Test fun plainVideoFileIsMovie() =
        assertEquals(ChannelKind.Movie, kind("https://files.example/films/night.mp4", "", "Night"))

    @Test fun episodeIsSeries() {
        assertEquals(ChannelKind.Series, kind("http://host/series/user/pass/55.mp4"))
        assertEquals(ChannelKind.Series, kind("https://files.example/show.mp4", "", "Show S01E02"))
    }

    @Test fun ordinaryLiveStreamIsLive() =
        assertEquals(ChannelKind.Live, kind("http://host/live/user/pass/9.ts", "bbcone.uk"))
}
