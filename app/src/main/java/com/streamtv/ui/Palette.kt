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
        ink = Color(0xFF000000), muted = Color(0xFF99999E), accent = Color(0xFFD5DADF), glass = Color(0xFF29292C),
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

    /** Deep navy to bright cyan: #03045E #0077B6 #00B4D8 #90E0EF #CAF0F8. */
    val Ocean = StvPalette(
        id = "ocean", name = "Ocean",
        ink = Color(0xFF01031F), muted = Color(0xFF8FB4CC), accent = Color(0xFF90E0EF), glass = Color(0xFF10265A),
        focus = listOf(Color(0xFF0A8CCB), Color(0xFF0068A3)),
        press = listOf(Color(0xFFCAF0F8), Color(0xFF90E0EF)),
        backdrop = listOf(Color(0xFF0A1F4D), Color(0xFF05123A), Color(0xFF020826)),
        glassFill = listOf(Color(0xB3132B62), Color(0xB3061436)),
        tileFocused = listOf(Color(0xFF0B5592), Color(0xFF07346E)),
        tile = listOf(Color(0xFF0E2553), Color(0xFF081739)),
        listFocus = listOf(Color(0xFF0A8CCB), Color(0xFF0068A3)),
        welcomeTop = Color(0xFF0A1A46),
        swatch = listOf(Color(0xFF03045E), Color(0xFF0077B6), Color(0xFF00B4D8), Color(0xFF90E0EF), Color(0xFFCAF0F8))
    )

    /** Cool slate and frost: #1B3043 #4C738D #A6C4CD #D5E6EE #F6F8FA. */
    val Winter = StvPalette(
        id = "winter", name = "Winter",
        ink = Color(0xFF0A131B), muted = Color(0xFF9DB4C2), accent = Color(0xFFD5E6EE), glass = Color(0xFF233A4E),
        focus = listOf(Color(0xFF5F88A3), Color(0xFF4C738D)),
        press = listOf(Color(0xFFF6F8FA), Color(0xFFD5E6EE)),
        backdrop = listOf(Color(0xFF1B3043), Color(0xFF13222F), Color(0xFF0A131B)),
        glassFill = listOf(Color(0xB32A4359), Color(0xB3132230)),
        tileFocused = listOf(Color(0xFF4C738D), Color(0xFF2E4B62)),
        tile = listOf(Color(0xFF213A4F), Color(0xFF152738)),
        listFocus = listOf(Color(0xFF5F88A3), Color(0xFF4C738D)),
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
