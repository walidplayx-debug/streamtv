package com.streamtv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.streamtv.R

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
    val swatch: List<Color>,
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
    /** Secondary text over video. */
    val overlayMuted: Color = Color(0xFFB5B5BA),
    /** The filled part of the film and episode time bar. */
    val seek: Color = Color(0xE6FFFFFF),
    /** The pale tile behind dark logos: resting, then focused (top to bottom). */
    val lightTile: List<Color> = listOf(Color(0xFFC4CAD4), Color(0xFF8E96A5)),
    val lightTileFocused: List<Color> = listOf(Color(0xFFD3D8E0), Color(0xFFA2AAB8)),
    /** The STV wordmark drawn for this theme. */
    val wordmark: Int = R.drawable.stv_wordmark,
    /** A soft band of light that keeps sweeping across the focused tile, like light on glass. */
    val sheen: Boolean = false
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
     * Apple's own look for a TV: true black pages with the dark system greys stepping up for each
     * layer (1C1C1E, 2C2C2E, 3A3A3C), white labels with grey secondary text, and one colour only,
     * the system blue, for what has focus and what is chosen. Pop-ups use the light platter with
     * dark text, as on Apple TV. Everything else stays neutral, so the pictures bring the colour.
     */
    val Cupertino = StvPalette(
        id = "cupertino", name = "Cupertino",
        ink = Color(0xFF000000), text = Color(0xFFFFFFFF), muted = Color(0xFF98989D), accent = Color(0xFF0A84FF), glass = Color(0xFF1C1C1E),
        focus = listOf(Color(0xFF0A84FF), Color(0xFF0071E3)),
        press = listOf(Color(0xFF64B5FF), Color(0xFF409CFF)),
        backdrop = listOf(Color(0xFF1C1C1E), Color(0xFF0B0B0C), Color(0xFF000000)),
        glassFill = listOf(Color(0xB32C2C2E), Color(0xB31C1C1E)),
        tileFocused = listOf(Color(0xFF48484A), Color(0xFF3A3A3C)),
        tile = listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E)),
        listFocus = listOf(Color(0xFF0A84FF), Color(0xFF0071E3)),
        welcomeTop = Color(0xFF1C1C1E),
        swatch = listOf(Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFF3A3A3C), Color(0xFF0A84FF), Color(0xFFF2F2F7)),
        panel = Color(0xF01C1C1E),
        faces = listOf(
            listOf(Color(0xFF3A3A3C), Color(0xFF2C2C2E), Color(0xFF1C1C1E)),
            listOf(Color(0xFF2A3A55), Color(0xFF1D2A40), Color(0xFF121B2B)),
            listOf(Color(0xFF48484A), Color(0xFF353537), Color(0xFF232325)),
            listOf(Color(0xFF33325A), Color(0xFF242342), Color(0xFF17162B))
        ),
        lit = Color(0xF2F2F2F7), onLit = Color(0xFF1C1C1E),
        number = Color(0xFF0A84FF),
        overlayMuted = Color(0xFFAEAEB2),
        seek = Color(0xFFFFFFFF),
        lightTile = listOf(Color(0xFFE5E5EA), Color(0xFFC7C7CC)),
        lightTileFocused = listOf(Color(0xFFF2F2F7), Color(0xFFD1D1D6))
    )

    /**
     * Apple's newer look, "liquid glass": the controls are clear glass over a deep blue-violet
     * page, so the page shows through them; the focused one is brighter glass with a band of
     * light sweeping across it. One cool accent (cyan) for choices, white for everything you read.
     * White text keeps at least 4.6:1 on every glass, focused or not.
     */
    val Glass = StvPalette(
        id = "glass", name = "Glass",
        ink = Color(0xFF05060D), text = Color(0xFFFFFFFF), muted = Color(0xFFA6ACC8), accent = Color(0xFF64D2FF), glass = Color(0xFF2B3158),
        focus = listOf(Color(0x5CFFFFFF), Color(0x38FFFFFF)),
        press = listOf(Color(0x8CFFFFFF), Color(0x5CFFFFFF)),
        backdrop = listOf(Color(0xFF22305C), Color(0xFF161A3A), Color(0xFF08080F)),
        glassFill = listOf(Color(0x29FFFFFF), Color(0x12FFFFFF)),
        tileFocused = listOf(Color(0x4DFFFFFF), Color(0x29FFFFFF)),
        tile = listOf(Color(0x24FFFFFF), Color(0x12FFFFFF)),
        listFocus = listOf(Color(0x5CFFFFFF), Color(0x38FFFFFF)),
        welcomeTop = Color(0xFF2A3A70),
        swatch = listOf(Color(0xFF08080F), Color(0xFF22305C), Color(0xFF8A93B8), Color(0xFF64D2FF), Color(0xFFFFFFFF)),
        panel = Color(0xD91A1F3D),
        faces = listOf(
            listOf(Color(0xFF2E3F7A), Color(0xFF22305C), Color(0xFF161E3D)),
            listOf(Color(0xFF3E3070), Color(0xFF2C2252), Color(0xFF1B1534)),
            listOf(Color(0xFF1E5068), Color(0xFF163B4E), Color(0xFF0E2533)),
            listOf(Color(0xFF3A4466), Color(0xFF2A3150), Color(0xFF1A1F33))
        ),
        lit = Color(0xEBFFFFFF), onLit = Color(0xFF0E1130),
        number = Color(0xFF64D2FF),
        overlayMuted = Color(0xFFC3C8DC),
        seek = Color(0xFFFFFFFF),
        lightTile = listOf(Color(0xFFEEF2FF), Color(0xFFC9D2EC)),
        lightTileFocused = listOf(Color(0xFFFFFFFF), Color(0xFFDCE3F7)),
        wordmark = R.drawable.stv_wordmark_glass,
        sheen = true
    )

    val all = listOf(Default, Cupertino, Glass)

    fun of(id: String): StvPalette = all.firstOrNull { it.id == id } ?: Default
}

/** The theme in use. Set by the view model from the saved choice; read by every screen. */
object ActivePalette {
    var current: StvPalette by mutableStateOf(Palettes.Default)
}
