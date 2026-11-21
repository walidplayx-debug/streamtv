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

    /**
     * Taken from a photo of a sparrow on a branch: dark umber and olive for the pages (most of the
     * screen), warm brown for the tiles, and the wing's ochre for focus (the small, bright part).
     * White text stays at least 3.4:1 on every focus colour, as in the default theme.
     */
    val Sparrow = StvPalette(
        id = "sparrow", name = "Sparrow",
        ink = Color(0xFF0E0C0A), text = Color(0xFFF4EFE6), muted = Color(0xFFA39C8E), accent = Color(0xFFE0B072), glass = Color(0xFF2A2622),
        focus = listOf(Color(0xFFA57744), Color(0xFF7E5629)),
        press = listOf(Color(0xFFE3C08A), Color(0xFFC79B5E)),
        backdrop = listOf(Color(0xFF1F2117), Color(0xFF16150F), Color(0xFF0D0C09)),
        glassFill = listOf(Color(0xB32E2C24), Color(0xB315140F)),
        tileFocused = listOf(Color(0xFF5A4A36), Color(0xFF3A3024)),
        tile = listOf(Color(0xFF2A2720), Color(0xFF1B1915)),
        listFocus = listOf(Color(0xFF9C7040), Color(0xFF7A5530)),
        welcomeTop = Color(0xFF1C1D14),
        swatch = listOf(Color(0xFF2B2625), Color(0xFF98A96C), Color(0xFF9BA1B0), Color(0xFFD2A15F), Color(0xFFF4EFE6))
    )

    val all = listOf(Default, Sparrow)

    fun of(id: String): StvPalette = all.firstOrNull { it.id == id } ?: Default
}

/** The theme in use. Set by the view model from the saved choice; read by every screen. */
object ActivePalette {
    var current: StvPalette by mutableStateOf(Palettes.Default)
}
