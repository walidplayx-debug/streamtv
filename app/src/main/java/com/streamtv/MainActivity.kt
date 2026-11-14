package com.streamtv
import android.os.Bundle
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.streamtv.ui.MainScreen
import com.streamtv.ui.MainViewModel
import com.streamtv.ui.StreamTheme
class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // TV keyboards float over the window; text entry has its own top-aligned screen instead.
        window.setSoftInputMode(SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            StreamTheme {
                // The interface is English-only, so keep one left-to-right layout on every TV language.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val state by model.state.collectAsState()
                    MainScreen(state, model)
                }
            }
        }
    }
}
