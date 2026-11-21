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
    val lightTileFocused: List<Color> = listOf(Color(0xFFD3D8E0), Color(0xFFA2AAB8))
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
        ink = Color(0xFF14150E), text = Color(0xFFF4EFE6), muted = Color(0xFFB8AF9E), accent = Color(0xFFE8B978), glass = Color(0xFF3A3527),
        focus = listOf(Color(0xFFA57744), Color(0xFF7E5629)),
        press = listOf(Color(0xFFE3C08A), Color(0xFFC79B5E)),
        // 60 %: the olive of the background behind the bird, clearly green-brown, not black.
        backdrop = listOf(Color(0xFF3D4229), Color(0xFF2C2C1D), Color(0xFF1C1812)),
        glassFill = listOf(Color(0xB34A4632), Color(0xB32A281C)),
        // 30 %: the warm brown of the back and wings.
        tileFocused = listOf(Color(0xFF8C6A42), Color(0xFF634A2E)),
        tile = listOf(Color(0xFF5A4632), Color(0xFF3E3022)),
        // 10 %: the wing's ochre, only on what has focus.
        listFocus = listOf(Color(0xFF9C7040), Color(0xFF7A5530)),
        welcomeTop = Color(0xFF4A5233),
        swatch = listOf(Color(0xFF2B2625), Color(0xFF98A96C), Color(0xFF9BA1B0), Color(0xFFD2A15F), Color(0xFFF4EFE6)),
        panel = Color(0xF02E2A1F),
        // Brown back, olive ground, blue-grey head, rust buds.
        faces = listOf(
            listOf(Color(0xFF5A4632), Color(0xFF3E3022), Color(0xFF2A2018)),
            listOf(Color(0xFF4A5233), Color(0xFF353B24), Color(0xFF22261A)),
            listOf(Color(0xFF4E5560), Color(0xFF373C45), Color(0xFF24272E)),
            listOf(Color(0xFF5E3A2E), Color(0xFF43281F), Color(0xFF2B1A14))
        ),
        // The pale throat for the light pill, with the dark umber of the wing stripes on it.
        lit = Color(0xF2F4EFE6), onLit = Color(0xFF2B2118),
        number = Color(0xFFE8B978),
        overlayMuted = Color(0xFFC2B8A6),
        seek = Color(0xFFE8B978),
        lightTile = listOf(Color(0xFFE6DDCC), Color(0xFFC4B59C)),
        lightTileFocused = listOf(Color(0xFFF4EDE0), Color(0xFFD6C8AE))
    )

    /**
     * Made for watching TV in a dim room. Deep midnight indigo instead of pure black: softer on the
     * eyes over a long evening and lets pictures and logos stand out. One warm coral, like a cinema
     * marquee, marks only what has focus, so it is easy to find from the sofa. Cool page, warm
     * focus: opposite sides of the colour wheel, so focus never blends into the page.
     */
    val Midnight = StvPalette(
        id = "midnight", name = "Midnight",
        ink = Color(0xFF07060F), text = Color(0xFFF5F3FF), muted = Color(0xFFA9A5C7), accent = Color(0xFFFF8A6B), glass = Color(0xFF262342),
        focus = listOf(Color(0xFFE0563A), Color(0xFFB8402A)),
        press = listOf(Color(0xFFFFB199), Color(0xFFFF8A6B)),
        backdrop = listOf(Color(0xFF1B1838), Color(0xFF120F26), Color(0xFF0A0816)),
        glassFill = listOf(Color(0xB32C2950), Color(0xB315132A)),
        tileFocused = listOf(Color(0xFF4A4580), Color(0xFF312D5C)),
        tile = listOf(Color(0xFF2E2B52), Color(0xFF1F1C3A)),
        listFocus = listOf(Color(0xFFD24E34), Color(0xFFA93A26)),
        welcomeTop = Color(0xFF2A2456),
        swatch = listOf(Color(0xFF0A0816), Color(0xFF2E2B52), Color(0xFF4A4580), Color(0xFFE0563A), Color(0xFFFFB199)),
        panel = Color(0xF01A1830),
        faces = listOf(
            listOf(Color(0xFF3A3570), Color(0xFF27234C), Color(0xFF17142E)),
            listOf(Color(0xFF4A2D5C), Color(0xFF33203F), Color(0xFF1E1326)),
            listOf(Color(0xFF1F4552), Color(0xFF163139), Color(0xFF0D1D22)),
            listOf(Color(0xFF353A55), Color(0xFF25283C), Color(0xFF161825))
        ),
        lit = Color(0xF2FFF4EE), onLit = Color(0xFF15132A),
        number = Color(0xFFFF8A6B),
        overlayMuted = Color(0xFFBDB9D6),
        seek = Color(0xFFFF6B4A),
        lightTile = listOf(Color(0xFFD9D6EA), Color(0xFFABA6CB)),
        lightTileFocused = listOf(Color(0xFFE8E6F5), Color(0xFFBDB8DA))
    )

    val all = listOf(Default, Midnight, Sparrow)

    fun of(id: String): StvPalette = all.firstOrNull { it.id == id } ?: Default
}

/** The theme in use. Set by the view model from the saved choice; read by every screen. */
object ActivePalette {
    var current: StvPalette by mutableStateOf(Palettes.Default)
}
