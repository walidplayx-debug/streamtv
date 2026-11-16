package com.streamtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamtv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Knows whether a newer version is waiting, so the Settings tab and About can wear a red mark.
 * It only reads the release's notes (a few hundred bytes); the app itself is downloaded later,
 * from About, as before.
 */
object UpdateNotice {
    var available by mutableStateOf(false)
        private set

    /** Looks once; any failure (no network, rate limit) leaves the mark as it was. */
    suspend fun refresh() {
        val remote = withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.github.com/repos/walidplayx-debug/streamtv/releases/tags/${BuildConfig.UPDATE_CHANNEL}"
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "STV-Android-App")
                }
                try {
                    if (connection.responseCode !in 200..299) null
                    else {
                        val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optString("body")
                        // The build writes "Version code: N" into the release notes.
                        Regex("""Version code:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toLongOrNull()
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        if (remote != null) available = remote > BuildConfig.VERSION_CODE.toLong()
    }
}

/** A small red circle with "!" that says something new is waiting. */
@Composable
fun AlertBadge(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Color(0xFFE5484D)), contentAlignment = Alignment.Center) {
        Text("!", color = Color.White, fontSize = (size.value * .68f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
