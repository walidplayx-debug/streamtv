package com.streamtv.ui

import androidx.core.graphics.get
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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
     * The tile shows a label under the face (the words that tell this channel from its siblings),
     * so the face has room for one line only: the shared name, without the category above it.
     */
    compact: Boolean = false,
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
                CategoryFace(name, group, compact)
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
                    if (smartPlate) {
                        val dark = LogoTone.known[logo] ?: ((success.result.drawable as? BitmapDrawable)?.bitmap
                            ?.let { LogoTone.needsPlate(it) } ?: false).also { LogoTone.known[logo] = it }
                        onNeedsLight(dark)
                    }
                    loaded = true
                },
                onError = { failed = true; onNeedsLight?.invoke(false) }
            )
        }
    }
}

/**
 * The face of a channel without a logo: its name only, cleaned of tags, split over two balanced
 * lines when that reads better, at the largest size the tile holds. [group] and [compact] are kept
 * for the callers; the face no longer shows the category.
 */
@Composable
private fun CategoryFace(name: String, @Suppress("UNUSED_PARAMETER") group: String, @Suppress("UNUSED_PARAMETER") compact: Boolean = false) {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF30353F), Color(0xFF22262E)))).padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        FittedName(cleanChannelName(name))
    }
}

/** The name at the largest size that fits, on the layout (one line or two) that allows the larger size. */
@Composable
private fun FittedName(name: String) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val fit = remember(name, maxW, maxH) {
            val candidates = listOfNotNull(name, smartSplit(name))
            var best = candidates.first() to MinNameSize
            for (text in candidates) {
                // Largest size, in half points, whose layout stays inside the face.
                var low = MinNameSize; var high = MaxNameSize
                while (high - low > .5f) {
                    val mid = (low + high) / 2f
                    val size = measurer.measure(text, nameStyle(mid), softWrap = false).size
                    if (size.width <= maxW && size.height <= maxH) low = mid else high = mid
                }
                // Two lines only when they allow a clearly larger size: "BBC One" stays on one line.
                if (low > best.second * (if (text == name) 1f else 1.1f)) best = text to low
            }
            best
        }
        Text(
            fit.first, style = nameStyle(fit.second), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2, softWrap = false, overflow = TextOverflow.Ellipsis
        )
    }
}

private const val MinNameSize = 11f
private const val MaxNameSize = 34f

private fun nameStyle(size: Float) = TextStyle(
    fontSize = size.sp, lineHeight = (size * 1.12f).sp, fontWeight = FontWeight.Bold,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/** Tags, quality marks and anything in brackets left out: "Adult Swim West [Not 24/7;Geo-blocked]" -> "Adult Swim West". */
fun cleanChannelName(name: String): String {
    val noBrackets = name.replace(Regex("""[\(\[\{][^)\]}]*[\)\]\}]"""), " ")
    val quality = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "h264", "720p", "1080p", "2160p", "480p", "360p")
    val words = noBrackets.split(Regex("\\s+")).filter { it.isNotBlank() && it.lowercase().trim('|', '-', ':') !in quality }
    val cleaned = words.joinToString(" ").trim(' ', '|', '-', ':', '•')
    return cleaned.ifBlank { name.trim() }
}

/**
 * The name over two lines of about the same length, or null when it is one word. Numbers and
 * very short words stay with the word before them ("MBC 3", "Mubasher 24"); a word is never cut.
 */
fun smartSplit(name: String): String? {
    val words = name.split(' ').filter { it.isNotBlank() }
    // Glue short pieces to their neighbour so they never sit alone on a line.
    val chunks = ArrayList<String>()
    for (word in words) {
        val short = word.length <= 2 || word.all { it.isDigit() || it == '+' }
        if (short && chunks.isNotEmpty()) chunks[chunks.size - 1] = chunks.last() + " " + word else chunks += word
    }
    if (chunks.size > 1 && (chunks[0].length <= 2)) { chunks[1] = chunks[0] + " " + chunks[1]; chunks.removeAt(0) }
    if (chunks.size < 2) return null
    // The most even split; among splits about as even (one letter apart), the one with the longer
    // first line, so the name's lead words stay together: "Adult Swim / West", not "Adult / Swim West".
    val splits = (1 until chunks.size).map { at ->
        val first = chunks.subList(0, at).joinToString(" ").length
        val second = chunks.subList(at, chunks.size).joinToString(" ").length
        Triple(at, first, maxOf(first, second))
    }
    val even = splits.minOf { it.third }
    val bestAt = splits.filter { it.third <= even + 1 }.maxBy { it.second }.first
    return chunks.subList(0, bestAt).joinToString(" ") + "\n" + chunks.subList(bestAt, chunks.size).joinToString(" ")
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
            val c = bitmap[x * (bitmap.width - 1) / (steps - 1), y * (bitmap.height - 1) / (steps - 1)]
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

/**
 * What a group of sibling channels share, once the words of [mark] (the part that tells this one
 * apart) are taken out: "ITV1 North West (1080p)" with mark "North West" -> "ITV1". The name
 * itself when nothing is left.
 */
fun sharedName(name: String, mark: String?): String {
    if (mark.isNullOrBlank()) return name
    val noise = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
    val quality = setOf("hd", "fhd", "uhd", "sd", "4k", "hevc", "h265", "720p", "1080p", "2160p", "480p", "360p")
    val own = mark.lowercase().split(Regex("\\s+")).toSet()
    val left = noise.replace(name, " ").split(Regex("\\s+"))
        .filter { it.isNotBlank() && it.lowercase() !in own && it.lowercase() !in quality }
        .joinToString(" ")
    return left.ifBlank { name }
}

/**
 * The label on a tile: the distinguishing words under the logo, dark on a silver tile and light on
 * a dark one, so it always reads.
 */
@Composable
fun TileLabel(text: String, onLight: Boolean, modifier: Modifier = Modifier) {
    // No extra font padding: the line sits centred and its descenders (y, g, p) are never cut.
    Box(modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text, color = if (onLight) Color(0xFF2A3045) else Color(0xFFD6D8DE), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeight = 15.sp)
        )
    }
}

/** A stream's quality as a short tag: "4K", "HD" or "SD" (empty when the name says nothing). */
fun qualityTag(name: String): String = when (val q = channelQuality(name)) {
    "" -> ""
    "4K", "2160P" -> "4K"
    "SD", "480P", "360P", "576P", "240P" -> "SD"
    else -> "HD"
}

/**
 * Among channels that share one logo, those whose quality differs (ZDF HD next to ZDF 4K): channel
 * key -> its quality tag. Others get none.
 */
fun qualityBadges(channels: List<com.streamtv.data.Channel>): Map<String, String> {
    val out = HashMap<String, String>()
    for (group in channels.filter { !it.logo.isNullOrBlank() }.groupBy { it.logo!! }.values) {
        if (group.size < 2) continue
        val tags = group.associate { it.key to qualityTag(it.name) }
        if (tags.values.toSet().size > 1) tags.forEach { (key, tag) -> if (tag.isNotEmpty()) out[key] = tag }
    }
    return out
}

/** The quality badge at the foot of a tile: a dark pill, red for 4K. */
@Composable
fun QualityBadge(tag: String, modifier: Modifier = Modifier) {
    Text(
        tag,
        modifier.clip(RoundedCornerShape(6.dp))
            .background(if (tag == "4K") Color(0xFFE8452F) else Color.Black.copy(alpha = .62f))
            .border(1.dp, if (tag == "4K") Color(0xFFE8452F) else Color.White.copy(alpha = .30f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1
    )
}
