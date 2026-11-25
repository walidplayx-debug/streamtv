package com.streamtv.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.streamtv.R
private val StreamColors = darkColorScheme(primary = Color(0xFFFF7A32), background = Color(0xFF07070B),
    surface = Color(0xFF17151A), onBackground = Color(0xFFF6F2EE), onSurface = Color(0xFFF6F2EE))
/**
 * IBM Plex Sans Arabic for Arabic letters. The files hold Arabic only, so Latin letters and digits
 * fall back to the system font and look as before.
 */
val StvFont = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal), Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold), Font(R.font.plex_arabic_bold, FontWeight.Bold)
)
private val StreamType = Typography().let { it.copy(bodyLarge = it.bodyLarge.copy(fontFamily = StvFont)) }
@Composable fun StreamTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = StreamColors, typography = StreamType, content = content) }
