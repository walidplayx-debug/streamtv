package com.streamtv.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.net.URLDecoder

/**
 * Builds the players. Every channel change makes a new player; on their own, each one started
 * from a low bandwidth guess (and quick zapping, with its cut-off downloads, pushed the guess down
 * further), so a channel could come back in a lower quality, soft and washed out, and stay there
 * for a while. Here every player shares one bandwidth meter that starts from a generous guess, and
 * the quality is allowed to climb back after two seconds instead of ten.
 */
@OptIn(UnstableApi::class)
object Players {
    private var meter: DefaultBandwidthMeter? = null

    fun create(context: Context): ExoPlayer {
        val app = context.applicationContext
        val bandwidth = meter ?: DefaultBandwidthMeter.Builder(app)
            .setInitialBitrateEstimate(20_000_000L)
            .build().also { meter = it }
        val selection = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 2_000,
            /* maxDurationForQualityDecreaseMs = */ 25_000,
            /* minDurationToRetainAfterDiscardMs = */ 25_000,
            /* bandwidthFraction = */ 0.85f
        )
        // Start showing after 1.5 s of picture (3 s after a stall) instead of the generic 2.5 s / 5 s:
        // channels open sooner and sit closer to live, while up to 50 s is still gathered behind.
        val buffers = DefaultLoadControl.Builder()
            .setBufferDurationsMs(/* min = */ 15_000, /* max = */ 50_000, /* forPlayback = */ 1_500, /* afterRebuffer = */ 3_000)
            .build()
        // The device's own decoders first; FFmpeg (Dolby AC3 / E-AC3, DTS, TrueHD...) only for a sound
        // the device cannot play, which otherwise left the picture silent.
        val renderers = DefaultRenderersFactory(app)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // Audio focus: the sound steps aside while the remote's voice search or the assistant speaks.
        val audio = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
        return ExoPlayer.Builder(app, renderers)
            .setAudioAttributes(audio, /* handleAudioFocus = */ true)
            .setBandwidthMeter(bandwidth)
            .setTrackSelector(DefaultTrackSelector(app, selection))
            .setLoadControl(buffers)
            .build()
    }

    /** What players say they are when the playlist names nothing: accepted by most IPTV servers. */
    private const val DefaultAgent = "VLC/3.0.20 LibVLC/3.0.20"

    /**
     * The stream for an address. A playlist can ask for its own player name and referrer, written
     * after a "|" (Kodi style: "url|User-Agent=...&Referer=..."); they are sent with every request.
     * Redirects between http and https are followed (common with IPTV servers), and a live stream
     * that falls behind (after a stall) speeds up slightly, unnoticed, until it is back at live.
     */
    fun stream(context: Context, address: String): MediaSource {
        val url = address.substringBefore('|')
        val headers = address.substringAfter('|', "").split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', "").trim()
            if (key.isEmpty() || value.isEmpty()) null else key to runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }.toMap(LinkedHashMap())
        val agent = headers.keys.firstOrNull { it.equals("User-Agent", true) }?.let { headers.remove(it) } ?: DefaultAgent
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(agent)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        val item = MediaItem.Builder().setUri(url)
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(.97f).setMaxPlaybackSpeed(1.04f).build())
            .build()
        return DefaultMediaSourceFactory(DefaultDataSource.Factory(context.applicationContext, http)).createMediaSource(item)
    }
}
