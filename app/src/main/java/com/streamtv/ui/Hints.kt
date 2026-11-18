package com.streamtv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small one-line tips for actions nobody can see (holding OK, the arrows while watching). Each tip
 * shows the first three times it applies, once per app start, then never again; it stays until
 * OK is pressed.
 */
object Hints {
    private const val Times = 3
    var current by mutableStateOf<String?>(null)
        private set
    private val shownThisRun = HashSet<String>()
    /** True while the start cover is up: nothing to point at yet. */
    var paused = false

    /** Shows the tip if it is still due; true when it was shown. */
    fun offer(context: Context, key: String, text: String): Boolean {
        if (paused || current != null || key in shownThisRun) return false
        val prefs = context.getSharedPreferences("hints", Context.MODE_PRIVATE)
        val seen = prefs.getInt(key, 0)
        if (seen >= Times) return false
        shownThisRun += key
        prefs.edit().putInt(key, seen + 1).apply()
        current = text
        return true
    }

    fun clear() { current = null }
}

/**
 * Where the tips appear: a glass pill at the foot of the screen that stays until OK is pressed.
 * While it is up the remote does nothing else, so the tip is read before going on.
 */
@Composable
fun HintOverlay() {
    val text = Hints.current ?: return
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { Hints.clear() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Nothing dimmed: only the pill appears over the screen.
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect { window?.setDimAmount(0f) }
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        // The OK press that led here may still be held; only a press that starts here closes the tip.
        val armed = remember { booleanArrayOf(false) }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier.fillMaxSize().padding(bottom = 34.dp)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { event ->
                    val ok = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter || event.key == Key.ButtonSelect
                    when {
                        ok && event.type == KeyEventType.KeyDown -> { armed[0] = true; true }
                        ok && event.type == KeyEventType.KeyUp -> { if (armed[0]) Hints.clear(); true }
                        event.key == Key.Back && event.type == KeyEventType.KeyUp -> { Hints.clear(); true }
                        else -> true
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                Modifier.clip(CircleShape).background(Color(0xF0202329))
                    .border(1.dp, Color.White.copy(alpha = .16f), CircleShape)
                    .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t(text), color = Color.White.copy(alpha = .92f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(14.dp))
                Text(
                    "OK", Modifier.clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF8290A6), Color(0xFF647187))))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * "Go to number": the list on screen (so a number means its place there, as in the player's info
 * bar), and a number begun with the remote's digit keys on the channel grid.
 */
object NumberJump {
    var list: List<com.streamtv.data.Channel> = emptyList()
    var title: String = ""
    var started by mutableStateOf<String?>(null)
}
