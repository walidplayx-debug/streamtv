package com.streamtv

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache

class StreamTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("channel_logos"))
                        .maxSizeBytes(64L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
        }
    }
}
