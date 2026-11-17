package com.streamtv.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay

/**
 * Small one-line tips for actions nobody can see (holding OK, the arrows while watching). Each tip
 * shows the first three times it applies, once per app start, then never again.
 */
object Hints {
    private const val Times = 3
    var current by mutableStateOf<String?>(null)
        private set
    var tick by mutableIntStateOf(0)
        private set
    private val shownThisRun = HashSet<String>()

    fun offer(context: Context, key: String, text: String) {
        if (key in shownThisRun) return
        val prefs = context.getSharedPreferences("hints", Context.MODE_PRIVATE)
        val seen = prefs.getInt(key, 0)
        if (seen >= Times) return
        shownThisRun += key
        prefs.edit().putInt(key, seen + 1).apply()
        current = text
        tick++
    }

    fun clear() { current = null }
}

/** Where the tips appear: a soft glass pill at the foot of the screen, gone after a few seconds. */
@Composable
fun HintOverlay() {
    val text = Hints.current
    // The last words stay while the pill fades out.
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    LaunchedEffect(Hints.tick) {
        if (Hints.current != null) { delay(5_000); Hints.clear() }
    }
    Box(Modifier.fillMaxSize().padding(bottom = 30.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = text != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Text(
                t(shown),
                Modifier.clip(CircleShape).background(Color(0xE6202329))
                    .border(1.dp, Color.White.copy(alpha = .14f), CircleShape)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                color = Color.White.copy(alpha = .9f), fontSize = 15.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}
