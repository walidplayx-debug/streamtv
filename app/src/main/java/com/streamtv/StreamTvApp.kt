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
                // Wikimedia refuses pictures asked for without a name: logos found by "Search logo".
                .okHttpClient {
                    okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                        val request = chain.request()
                        val host = request.url.host
                        if (host.endsWith("wikimedia.org") || host.endsWith("wikipedia.org")) {
                            chain.proceed(request.newBuilder().header("User-Agent", com.streamtv.data.LogoSearch.UserAgent).build())
                        } else chain.proceed(request)
                    }.build()
                }
                .build()
        )
    }
}
