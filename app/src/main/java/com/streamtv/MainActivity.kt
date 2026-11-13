package com.streamtv
import android.os.Bundle
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.streamtv.ui.MainScreen
import com.streamtv.ui.MainViewModel
import com.streamtv.ui.StreamTheme
class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE)
        setContent { StreamTheme { val state by model.state.collectAsState(); MainScreen(state, model) } }
    }
}
