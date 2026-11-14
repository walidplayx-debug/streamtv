package com.streamtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

fun channelInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val raw = if (words.size > 1) words.take(2).mapNotNull { it.firstOrNull() }.joinToString("") else name.trim().take(2)
    return raw.uppercase().ifBlank { "TV" }
}

/**
 * Channel logo with an initials placeholder. The placeholder is shown while the logo is
 * loading, when there is no logo, and when the logo fails to load; it is removed once the
 * logo is on screen so it never shows through transparent artwork.
 */
@Composable
fun ChannelLogo(
    name: String,
    logo: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    corner: Dp = 10.dp,
    placeholder: Brush = Brush.linearGradient(listOf(Color(0xFF3A3A3D), Color(0xFF242426))),
    logoPadding: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Fit
) {
    var loaded by remember(logo) { mutableStateOf(false) }
    var failed by remember(logo) { mutableStateOf(false) }
    val hasLogo = !logo.isNullOrBlank() && !failed
    Box(modifier.clip(RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        if (!hasLogo || !loaded) {
            Box(Modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
                Text(channelInitials(name), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        if (hasLogo) {
            AsyncImage(
                model = logo,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().padding(logoPadding),
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = { failed = true }
            )
        }
    }
}
