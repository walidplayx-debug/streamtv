package com.streamtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.graphics.drawable.BitmapDrawable
import coil.compose.AsyncImage
import coil.request.ImageRequest

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
    contentScale: ContentScale = ContentScale.Fit,
    /** When given, a channel without a logo shows its name under its category's colour instead of initials. */
    group: String? = null,
    /**
     * When given, the logo is judged once as it loads, and this is told whether it is dark or
     * see-through enough to need a light tile behind it (false while loading or without a logo).
     */
    onNeedsLight: ((Boolean) -> Unit)? = null
) {
    val smartPlate = onNeedsLight != null
    var loaded by remember(logo) { mutableStateOf(false) }
    var failed by remember(logo) { mutableStateOf(false) }
    val hasLogo = !logo.isNullOrBlank() && !failed
    val context = LocalContext.current
    // Pixels are read only when the plate is wanted, so other logos keep the faster hardware bitmaps.
    val model = remember(logo, smartPlate) {
        if (smartPlate && logo != null && !LogoTone.known.containsKey(logo)) ImageRequest.Builder(context).data(logo).allowHardware(false).build() else logo
    }
    Box(modifier.clip(RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        if (!hasLogo || !loaded) {
            if (group != null) {
                CategoryFace(name, group)
            } else {
                Box(Modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
                    Text(channelInitials(name), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        if (hasLogo) {
            AsyncImage(
                model = model,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().padding(logoPadding),
                contentScale = contentScale,
                onSuccess = { success ->
                    if (smartPlate && logo != null) {
                        val dark = LogoTone.known[logo] ?: ((success.result.drawable as? BitmapDrawable)?.bitmap
                            ?.let { LogoTone.needsPlate(it) } ?: false).also { LogoTone.known[logo] = it }
                        onNeedsLight?.invoke(dark)
                    }
                    loaded = true
                },
                onError = { failed = true; onNeedsLight?.invoke(false) }
            )
        }
    }
}

/** A colour for a playlist category, found from words in its name (in English, French or Arabic). */
private fun categoryColor(group: String): Color {
    val g = group.lowercase()
    fun has(vararg words: String) = words.any { it in g }
    return when {
        has("news", "info", "أخبار", "اخبار", "actualit") -> Color(0xFF3B82F6)
        has("sport", "رياض", "bein", "foot", "soccer") -> Color(0xFF22C55E)
        has("movie", "film", "cinema", "ciné", "أفلام", "افلام", "سينما") -> Color(0xFFF59E0B)
        has("kid", "child", "cartoon", "anim", "enfant", "أطفال", "اطفال", "كرتون") -> Color(0xFFEC4899)
        has("music", "musique", "أغاني", "اغاني", "موسيق") -> Color(0xFF06B6D4)
        has("docu", "nature", "science", "وثائق", "علوم") -> Color(0xFF14B8A6)
        has("relig", "islam", "quran", "coran", "دين", "قرآن", "قران") -> Color(0xFF84CC16)
        has("series", "série", "drama", "مسلسل", "دراما") -> Color(0xFFA855F7)
        else -> Color(0xFF94A3B8)
    }
}

/**
 * The face of a channel without a logo: a coloured stripe and the category on the start side,
 * the channel's full name under it, large enough to read from the sofa.
 */
@Composable
private fun CategoryFace(name: String, group: String) {
    val color = categoryColor(group)
    val label = group.split(';', '|', '/').firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    Row(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF30353F), Color(0xFF22262E))))
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            if (label.isNotEmpty()) {
                Text(label.uppercase(), color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, letterSpacing = .4.sp)
                Spacer(Modifier.height(2.dp))
            }
            Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
        }
    }
}

/**
 * Decides which logos need a light plate: those that are mostly dark, or whose drawn parts are
 * dark enough to vanish against the dark tile through their see-through gaps. Each logo is
 * judged once and remembered.
 */
object LogoTone {
    val known = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun needsPlate(bitmap: android.graphics.Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return false
        val steps = 28
        var drawn = 0
        var dark = 0
        var light = 0.0
        for (y in 0 until steps) for (x in 0 until steps) {
            val c = bitmap.getPixel(x * (bitmap.width - 1) / (steps - 1), y * (bitmap.height - 1) / (steps - 1))
            val a = (c ushr 24) and 0xFF
            if (a < 60) continue
            val l = (.299 * ((c shr 16) and 0xFF) + .587 * ((c shr 8) and 0xFF) + .114 * (c and 0xFF)) / 255.0
            drawn++
            light += l
            if (l < .22) dark++
        }
        if (drawn < steps) return false
        return light / drawn < .36 || dark.toFloat() / drawn > .35
    }
}

/**
 * For channels that share one logo (one channel in several languages or regions), the words that
 * tell each apart: what is left of its name once the words all of them share, and quality tags
 * like "(1080p)" or "HD", are taken out. "DW Arabic", "DW English" -> "Arabic", "English";
 * "24 Horas", "24 Horas Catalunya" -> (nothing), "Catalunya". Returns channel key -> label.
 */
fun distinguishingLabels(channels: List<com.streamtv.data.Channel>): Map<String, String> {
    val noise = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
    val quality = setOf("hd", "fhd", "uhd", "sd", "4k", "hevc", "h265", "720p", "1080p", "2160p", "480p", "360p", "tv")
    fun words(name: String) = noise.replace(name, " ").split(Regex("\\s+")).filter { it.isNotBlank() && it.lowercase() !in quality }
    val out = HashMap<String, String>()
    channels.filter { !it.logo.isNullOrBlank() }.groupBy { it.logo!! }.values.filter { it.size > 1 }.forEach { group ->
        val all = group.map { words(it.name) }
        val shared = all.map { w -> w.map { it.lowercase() }.toSet() }.reduce { x, y -> x intersect y }
        group.zip(all).forEach { (channel, w) ->
            val label = w.filter { it.lowercase() !in shared }.joinToString(" ")
            if (label.isNotBlank()) out[channel.key] = label
        }
    }
    return out
}

/** The label on a tile: a see-through dark band along its foot, with the distinguishing words. */
@Composable
fun TileLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = .55f)).padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
