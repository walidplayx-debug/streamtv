package com.streamtv.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

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
        return ExoPlayer.Builder(app)
            .setBandwidthMeter(bandwidth)
            .setTrackSelector(DefaultTrackSelector(app, selection))
            .build()
    }
}
