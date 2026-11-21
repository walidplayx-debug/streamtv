package com.streamtv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The app's colours for one theme. A theme changes colours only: every screen keeps its layout,
 * shapes and sizes. Screens read these through [ActivePalette], so a new choice in Settings
 * repaints everything at once.
 */
class StvPalette(
    /** Key saved in the settings. */
    val id: String,
    /** Name shown in Settings (translated with [t]). */
    val name: String,
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
    /** The palette's own colours, shown as dots beside its name in Settings. */
    val swatch: List<Color>
) {
    val focusBrush: Brush get() = Brush.verticalGradient(focus)
    val pressBrush: Brush get() = Brush.verticalGradient(press)
    val backdropBrush: Brush get() = Brush.verticalGradient(backdrop)
    val glassFillBrush: Brush get() = Brush.verticalGradient(glassFill)
}

object Palettes {
    /** The original look; stays the default. */
    val Default = StvPalette(
        id = "default", name = "Default",
        ink = Color(0xFF000000), text = Color(0xFFFFFFFF), muted = Color(0xFF99999E), accent = Color(0xFFD5DADF), glass = Color(0xFF29292C),
        focus = listOf(Color(0xFF8290A6), Color(0xFF647187)),
        press = listOf(Color(0xFFC6D1E3), Color(0xFF9FADC6)),
        backdrop = listOf(Color(0xFF1A1D24), Color(0xFF111318), Color(0xFF0B0C0F)),
        glassFill = listOf(Color(0xB3262931), Color(0xB3131519)),
        tileFocused = listOf(Color(0xFF465064), Color(0xFF2B3240)),
        tile = listOf(Color(0xFF252A33), Color(0xFF171B22)),
        listFocus = listOf(Color(0xFF7C889D), Color(0xFF5F6B80)),
        welcomeTop = Color(0xFF16161A),
        swatch = listOf(Color(0xFF0B0C0F), Color(0xFF252A33), Color(0xFF647187), Color(0xFF8290A6), Color(0xFFD5DADF))
    )

    /*
     * The two card themes share their colours out by role rather than tinting everything:
     * the darkest card colour, deepened toward a near-neutral black, is the page (most of the
     * screen); the middle colours are the surfaces and the focus (some of it); the light colours
     * are only the small highlights, the secondary text and the press (a little of it).
     */

    /** Card: #03045E #0077B6 #00B4D8 #90E0EF #CAF0F8. */
    val Ocean = StvPalette(
        id = "ocean", name = "Ocean",
        // Page: #03045E fading into a soft black, so the blue is a glow at the top, not a wash.
        ink = Color(0xFF05060D), text = Color(0xFFF3FBFD), muted = Color(0xFF8FB0BE), accent = Color(0xFF90E0EF),
        glass = Color(0xFF161C30),
        // Focus: #0077B6 lifted toward #00B4D8.
        focus = listOf(Color(0xFF0A9BD0), Color(0xFF0077B6)),
        // Press: the two light card colours.
        press = listOf(Color(0xFFCAF0F8), Color(0xFF90E0EF)),
        backdrop = listOf(Color(0xFF0C1442), Color(0xFF080B1F), Color(0xFF05060D)),
        glassFill = listOf(Color(0xB3172040), Color(0xB30A0E1E)),
        // Tiles: neutral slate at rest; the card's navy and blue when focused.
        tileFocused = listOf(Color(0xFF0077B6), Color(0xFF03045E)),
        tile = listOf(Color(0xFF1A2036), Color(0xFF10142A)),
        listFocus = listOf(Color(0xFF0A9BD0), Color(0xFF0077B6)),
        welcomeTop = Color(0xFF0C1442),
        swatch = listOf(Color(0xFF03045E), Color(0xFF0077B6), Color(0xFF00B4D8), Color(0xFF90E0EF), Color(0xFFCAF0F8))
    )

    /** Card: #1B3043 #4C738D #A6C4CD #D5E6EE #F6F8FA. */
    val Winter = StvPalette(
        id = "winter", name = "Winter",
        // Page: Night Teal into a cool charcoal. Text: Snow. Secondary text: Arctic. Marks: Mist Blue.
        ink = Color(0xFF0B1117), text = Color(0xFFF6F8FA), muted = Color(0xFFA6C4CD), accent = Color(0xFFD5E6EE),
        glass = Color(0xFF1E2C3A),
        // Focus: Steel Blue.
        focus = listOf(Color(0xFF5C849E), Color(0xFF4C738D)),
        // Press: Snow into Mist Blue.
        press = listOf(Color(0xFFF6F8FA), Color(0xFFD5E6EE)),
        backdrop = listOf(Color(0xFF1B3043), Color(0xFF121D28), Color(0xFF0B1117)),
        glassFill = listOf(Color(0xB3243749), Color(0xB3101922)),
        // Tiles: charcoal at rest; Steel Blue into Night Teal when focused.
        tileFocused = listOf(Color(0xFF4C738D), Color(0xFF1B3043)),
        tile = listOf(Color(0xFF202B36), Color(0xFF151D26)),
        listFocus = listOf(Color(0xFF5C849E), Color(0xFF4C738D)),
        welcomeTop = Color(0xFF1B3043),
        swatch = listOf(Color(0xFF1B3043), Color(0xFF4C738D), Color(0xFFA6C4CD), Color(0xFFD5E6EE), Color(0xFFF6F8FA))
    )

    val all = listOf(Default, Ocean, Winter)

    fun of(id: String): StvPalette = all.firstOrNull { it.id == id } ?: Default
}

/** The theme in use. Set by the view model from the saved choice; read by every screen. */
object ActivePalette {
    var current: StvPalette by mutableStateOf(Palettes.Default)
}
