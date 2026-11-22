package com.streamtv.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
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
    /**
     * Subtitles as Apple shows them: white letters on a small dark box just around the words. The
     * viewer can choose "transparent background" instead: no box, a light dark shadow. The choice
     * is kept for both players. Whatever the file asks for (colours, sizes) is ignored.
     */
    fun styleSubtitles(view: androidx.media3.ui.PlayerView) {
        val boxed = subtitleBox(view.context)
        view.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setStyle(
                if (boxed) androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE, 0xA6000000.toInt(), android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.TRANSPARENT, null
                ) else androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, 0xB3000000.toInt(), null
                )
            )
            setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * .9f)
        }
    }

    /** True: the dark box behind subtitles (the default); false: no box, a shadow. */
    fun subtitleBox(context: Context): Boolean =
        context.getSharedPreferences("stv_player", Context.MODE_PRIVATE).getBoolean("subtitle_box", true)

    fun setSubtitleBox(context: Context, boxed: Boolean) {
        context.getSharedPreferences("stv_player", Context.MODE_PRIVATE).edit().putBoolean("subtitle_box", boxed).apply()
    }

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
        // Up to 50 s, but never more than 48 MB (24 MB on a low-memory TV). Left to itself the player
        // may hold about 130 MB of video in the app's memory, most of what a TV allows an app.
        val lowRam = (app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
        val buffers = DefaultLoadControl.Builder()
            .setBufferDurationsMs(/* min = */ 15_000, /* max = */ 50_000, /* forPlayback = */ 1_500, /* afterRebuffer = */ 3_000)
            .setTargetBufferBytes((if (lowRam) 24 else 48) * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        // The device's own decoders first; FFmpeg (Dolby AC3 / E-AC3, DTS, TrueHD...) only for a sound
        // the device cannot play, which otherwise left the picture silent.
        val renderers = DefaultRenderersFactory(app)
            .setEnableDecoderFallback(true)
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

    /**
     * The sound picked by itself, as the big apps do: the app's language first, then the TV's own,
     * and the main sound before a commentary or an audio description. Among those, the player
     * already takes the one with the most channels it can play (5.1 before stereo), decoded by the
     * device or, for Dolby and DTS it cannot play, by FFmpeg.
     */
    fun preferSound(player: ExoPlayer, appLanguage: String) {
        val device = java.util.Locale.getDefault().language
        val languages = listOf(appLanguage, device).filter { it.isNotBlank() }.distinct()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*languages.toTypedArray())
            .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
            .build()
    }

    /** A player for movies and episodes: a seek lands on the nearest keyframe, which over the
     *  network is far quicker than decoding up to the exact frame. */
    fun createVod(context: Context): ExoPlayer = create(context).apply { setSeekParameters(SeekParameters.CLOSEST_SYNC) }

    /**
     * A small silent player for the picture above the bar while choosing a place: pictures only
     * (no sound, no subtitles), low resolution, a short buffer, and seeks to the nearest keyframe.
     */
    fun createPreview(context: Context): ExoPlayer {
        val app = context.applicationContext
        val selector = DefaultTrackSelector(app).apply {
            setParameters(
                buildUponParameters().setMaxVideoSizeSd()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
        }
        val buffers = DefaultLoadControl.Builder()
            .setBufferDurationsMs(/* min = */ 1_000, /* max = */ 2_000, /* forPlayback = */ 250, /* afterRebuffer = */ 500)
            .build()
        return ExoPlayer.Builder(app)
            .setTrackSelector(selector)
            .setLoadControl(buffers)
            .build()
            .apply { setSeekParameters(SeekParameters.CLOSEST_SYNC); volume = 0f; playWhenReady = false }
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
