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
     * Each theme is built the same way: a calm dark page with a glow of its colour at the top,
     * dark tinted tiles, one medium shade for focus, and a bright shade only for small highlights.
     */

    /** Deep indigo with a soft violet light. */
    val Midnight = StvPalette(
        id = "midnight", name = "Midnight",
        ink = Color(0xFF07060F), text = Color(0xFFF5F3FF), muted = Color(0xFFA39FC0), accent = Color(0xFFB7A6FF),
        glass = Color(0xFF1D1A33),
        focus = listOf(Color(0xFF7B68D9), Color(0xFF5B49B8)),
        press = listOf(Color(0xFFE4DDFF), Color(0xFFC3B5FF)),
        backdrop = listOf(Color(0xFF1A1633), Color(0xFF100E21), Color(0xFF08070F)),
        glassFill = listOf(Color(0xB3231F3F), Color(0xB30F0D1D)),
        tileFocused = listOf(Color(0xFF4E42A0), Color(0xFF2D2763)),
        tile = listOf(Color(0xFF201D36), Color(0xFF15132A)),
        listFocus = listOf(Color(0xFF7B68D9), Color(0xFF5B49B8)),
        welcomeTop = Color(0xFF1A1633),
        swatch = listOf(Color(0xFF100E21), Color(0xFF2D2763), Color(0xFF5B49B8), Color(0xFF7B68D9), Color(0xFFB7A6FF))
    )

    /** Night sea blue with a clear sky-blue light. */
    val Ocean = StvPalette(
        id = "ocean", name = "Ocean",
        ink = Color(0xFF04080D), text = Color(0xFFF2F9FC), muted = Color(0xFF94B0C0), accent = Color(0xFF7DD3F0),
        glass = Color(0xFF142230),
        focus = listOf(Color(0xFF2E8FC9), Color(0xFF1F70A6)),
        press = listOf(Color(0xFFD6F2FB), Color(0xFFA9E3F5)),
        backdrop = listOf(Color(0xFF0E2236), Color(0xFF091522), Color(0xFF04080D)),
        glassFill = listOf(Color(0xB3152A3E), Color(0xB3091420)),
        tileFocused = listOf(Color(0xFF1E5E8C), Color(0xFF113652)),
        tile = listOf(Color(0xFF152230), Color(0xFF0E1722)),
        listFocus = listOf(Color(0xFF2E8FC9), Color(0xFF1F70A6)),
        welcomeTop = Color(0xFF0E2236),
        swatch = listOf(Color(0xFF091522), Color(0xFF113652), Color(0xFF1F70A6), Color(0xFF2E8FC9), Color(0xFF7DD3F0))
    )

    /** Forest green with a fresh mint light. */
    val Emerald = StvPalette(
        id = "emerald", name = "Emerald",
        ink = Color(0xFF040A08), text = Color(0xFFF1FBF6), muted = Color(0xFF92B5A6), accent = Color(0xFF6EE7B7),
        glass = Color(0xFF15261F),
        focus = listOf(Color(0xFF1F9D74), Color(0xFF137A59)),
        press = listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0)),
        backdrop = listOf(Color(0xFF0F2A21), Color(0xFF0A1A15), Color(0xFF050C0A)),
        glassFill = listOf(Color(0xB3173328), Color(0xB30A1712)),
        tileFocused = listOf(Color(0xFF1A6B52), Color(0xFF0F3D30)),
        tile = listOf(Color(0xFF16241F), Color(0xFF0E1814)),
        listFocus = listOf(Color(0xFF1F9D74), Color(0xFF137A59)),
        welcomeTop = Color(0xFF0F2A21),
        swatch = listOf(Color(0xFF0A1A15), Color(0xFF0F3D30), Color(0xFF137A59), Color(0xFF1F9D74), Color(0xFF6EE7B7))
    )

    /** Warm charcoal with a coral and amber light. */
    val Sunset = StvPalette(
        id = "sunset", name = "Sunset",
        ink = Color(0xFF0D0806), text = Color(0xFFFFF7F0), muted = Color(0xFFC2A898), accent = Color(0xFFFFB86B),
        glass = Color(0xFF2A1E19),
        focus = listOf(Color(0xFFE07A4F), Color(0xFFC25A33)),
        press = listOf(Color(0xFFFFE3C7), Color(0xFFFFC994)),
        backdrop = listOf(Color(0xFF2A1812), Color(0xFF1A100C), Color(0xFF0D0806)),
        glassFill = listOf(Color(0xB3352219), Color(0xB317100C)),
        tileFocused = listOf(Color(0xFF8F4126), Color(0xFF4F2416)),
        tile = listOf(Color(0xFF2A1F1A), Color(0xFF1C1511)),
        listFocus = listOf(Color(0xFFE07A4F), Color(0xFFC25A33)),
        welcomeTop = Color(0xFF2A1812),
        swatch = listOf(Color(0xFF1A100C), Color(0xFF4F2416), Color(0xFFC25A33), Color(0xFFE07A4F), Color(0xFFFFB86B))
    )

    /** Dark plum with a soft pink light. */
    val Rose = StvPalette(
        id = "rose", name = "Rose",
        ink = Color(0xFF0D0609), text = Color(0xFFFFF4F7), muted = Color(0xFFC0A0AC), accent = Color(0xFFFF9EBB),
        glass = Color(0xFF2A1A22),
        focus = listOf(Color(0xFFD45C86), Color(0xFFB03F69)),
        press = listOf(Color(0xFFFFE0EA), Color(0xFFFFC2D4)),
        backdrop = listOf(Color(0xFF2A1220), Color(0xFF1A0C14), Color(0xFF0D0609)),
        glassFill = listOf(Color(0xB3361C2B), Color(0xB3180C12)),
        tileFocused = listOf(Color(0xFF83304F), Color(0xFF4A1B2E)),
        tile = listOf(Color(0xFF2A1C23), Color(0xFF1C1318)),
        listFocus = listOf(Color(0xFFD45C86), Color(0xFFB03F69)),
        welcomeTop = Color(0xFF2A1220),
        swatch = listOf(Color(0xFF1A0C14), Color(0xFF4A1B2E), Color(0xFFB03F69), Color(0xFFD45C86), Color(0xFFFF9EBB))
    )

    /** Black and gold, quiet and rich. */
    val Gold = StvPalette(
        id = "gold", name = "Gold",
        ink = Color(0xFF050505), text = Color(0xFFFAF7F0), muted = Color(0xFFA8A296), accent = Color(0xFFE8C872),
        glass = Color(0xFF221F1A),
        focus = listOf(Color(0xFFA8843A), Color(0xFF85662A)),
        press = listOf(Color(0xFFF5E6BE), Color(0xFFE8C872)),
        backdrop = listOf(Color(0xFF1C1A16), Color(0xFF110F0D), Color(0xFF070706)),
        glassFill = listOf(Color(0xB3272420), Color(0xB3121110)),
        tileFocused = listOf(Color(0xFF5E4C2A), Color(0xFF332A19)),
        tile = listOf(Color(0xFF201E1B), Color(0xFF151412)),
        listFocus = listOf(Color(0xFFA8843A), Color(0xFF85662A)),
        welcomeTop = Color(0xFF1C1A16),
        swatch = listOf(Color(0xFF110F0D), Color(0xFF332A19), Color(0xFF85662A), Color(0xFFA8843A), Color(0xFFE8C872))
    )

    val all = listOf(Default, Midnight, Ocean, Emerald, Sunset, Rose, Gold)

    fun of(id: String): StvPalette = all.firstOrNull { it.id == id } ?: Default
}

/** The theme in use. Set by the view model from the saved choice; read by every screen. */
object ActivePalette {
    var current: StvPalette by mutableStateOf(Palettes.Default)
}
