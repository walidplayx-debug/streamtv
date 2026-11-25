package com.streamtv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.streamtv.R

/** The app's colours, in one place. Screens read them through [ActivePalette]. */
class StvPalette(
    /** The deepest tone: page edges, scrims, dark text on light buttons. */
    val ink: Color,
    /** Main text and light marks. */
    val text: Color,
    /** Secondary text. */
    val muted: Color,
    /** Check marks, "Active" labels and other small highlights. */
    val accent: Color,
    /** Solid dark glass behind bars and fields. */
    val glass: Color,
    /** The focused control. */
    val focus: List<Color>,
    /** The short pulse when OK is pressed. */
    val press: List<Color>,
    /** The page backdrop, top to bottom. */
    val backdrop: List<Color>,
    /** See-through glass panels, top to bottom. */
    val glassFill: List<Color>,
    /** Channel and title tiles: focused, then resting. */
    val tileFocused: List<Color>,
    val tile: List<Color>,
    /** The focused row in the live player's channel list. */
    val listFocus: List<Color>,
    /** Top of the welcome page's gradient. */
    val welcomeTop: Color,
    /** Solid glass behind pop-ups and the players' bars and lists. */
    val panel: Color = Color(0xF01C1C1E),
    /** Faces of channels without a logo, picked by the channel's name, top to bottom. */
    val faces: List<List<Color>> = listOf(
        listOf(Color(0xFF30363B), Color(0xFF202428), Color(0xFF141719)),
        listOf(Color(0xFF3A3430), Color(0xFF29231F), Color(0xFF171513)),
        listOf(Color(0xFF34343A), Color(0xFF25252B), Color(0xFF151519)),
        listOf(Color(0xFF303A39), Color(0xFF222B2A), Color(0xFF141A19))
    ),
    /** The light pill behind the focused row in pop-ups and players, and the text on it. */
    val lit: Color = Color(0xF2F2F2F7),
    val onLit: Color = Color(0xFF1C1C1E),
    /** The big channel number in the player and the typed number. */
    val number: Color = Color(0xFFE8452F),
    /** Error messages. */
    val error: Color = Color(0xFFFF8B7B),
    /** Secondary text over video. */
    val overlayMuted: Color = Color(0xFFB5B5BA),
    /** The filled part of the film and episode time bar. */
    val seek: Color = Color(0xE6FFFFFF),
    /** The pale tile behind dark logos: resting, then focused (top to bottom). */
    val lightTile: List<Color> = listOf(Color(0xFFC4CAD4), Color(0xFF8E96A5)),
    val lightTileFocused: List<Color> = listOf(Color(0xFFD3D8E0), Color(0xFFA2AAB8)),
    /** The STV wordmark drawn for this theme. */
    val wordmark: Int = R.drawable.stv_wordmark
) {
    val focusBrush: Brush get() = Brush.verticalGradient(focus)
    val pressBrush: Brush get() = Brush.verticalGradient(press)
    val backdropBrush: Brush get() = Brush.verticalGradient(backdrop)
    val glassFillBrush: Brush get() = Brush.verticalGradient(glassFill)
}

object Palettes {
    /** The app's one look. */
    val Default = StvPalette(
        ink = Color(0xFF000000), text = Color(0xFFFFFFFF), muted = Color(0xFF99999E), accent = Color(0xFFD5DADF), glass = Color(0xFF29292C),
        focus = listOf(Color(0xFF8290A6), Color(0xFF647187)),
        press = listOf(Color(0xFFC6D1E3), Color(0xFF9FADC6)),
        backdrop = listOf(Color(0xFF1A1D24), Color(0xFF111318), Color(0xFF0B0C0F)),
        glassFill = listOf(Color(0xB3262931), Color(0xB3131519)),
        tileFocused = listOf(Color(0xFF465064), Color(0xFF2B3240)),
        tile = listOf(Color(0xFF252A33), Color(0xFF171B22)),
        listFocus = listOf(Color(0xFF7C889D), Color(0xFF5F6B80)),
        welcomeTop = Color(0xFF16161A)
    )

}

/** The colours in use; read by every screen. */
object ActivePalette {
    var current: StvPalette by mutableStateOf(Palettes.Default)
}
