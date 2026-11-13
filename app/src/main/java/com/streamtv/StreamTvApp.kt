package com.streamtv

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache

class StreamTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("channel_logos"))
                        .maxSizeBytes(64L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
        )
    }
}
