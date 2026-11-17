package com.streamtv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
                val url = "https://api.github.com/repos/walidplayx-debug/streamtv/releases/tags/latest"
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

/**
 * A glossy red circle with a white "!" that says something new is waiting. The mark is drawn, not
 * typed, so it sits exactly in the middle whatever the font.
 */
@Composable
fun AlertBadge(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        // A soft shadow under it.
        drawCircle(Color.Black.copy(alpha = .35f), radius = r, center = Offset(c.x, c.y + r * .12f))
        // Red, lit from above: brighter at the top, deeper at the rim.
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFF5B4E), Color(0xFFE3241B), Color(0xFFB3120B)),
                center = Offset(c.x, c.y - r * .35f), radius = r * 1.35f
            ),
            radius = r, center = c
        )
        // A thin light rim and a gloss on the upper half.
        drawCircle(Color.White.copy(alpha = .55f), radius = r - r * .05f, center = c, style = Stroke(width = r * .09f))
        drawOval(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = .45f), Color.Transparent), startY = c.y - r * .9f, endY = c.y),
            topLeft = Offset(c.x - r * .62f, c.y - r * .9f), size = Size(r * 1.24f, r * .85f)
        )
        // The "!": a rounded bar and a dot, centred.
        val w = r * .26f
        drawRoundRect(
            Color.White, topLeft = Offset(c.x - w / 2f, c.y - r * .56f), size = Size(w, r * .70f),
            cornerRadius = CornerRadius(w / 2f)
        )
        drawCircle(Color.White, radius = w * .62f, center = Offset(c.x, c.y + r * .42f))
    }
}
