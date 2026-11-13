package com.streamtv.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val StreamColors = darkColorScheme(primary = Color(0xFFFF7A32), background = Color(0xFF07070B),
    surface = Color(0xFF17151A), onBackground = Color(0xFFF6F2EE), onSurface = Color(0xFFF6F2EE))
@Composable fun StreamTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = StreamColors, content = content) }
