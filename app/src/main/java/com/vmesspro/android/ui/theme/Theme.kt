package com.vmesspro.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF35D4FF),
    secondary = Color(0xFF8B5CFF),
    tertiary = Color(0xFF48E6B0),
    background = Color(0xFF050914),
    surface = Color(0xFF0B1324),
    surfaceVariant = Color(0xFF111D33),
    onPrimary = Color(0xFF001018),
    onBackground = Color(0xFFF5F7FF),
    onSurface = Color(0xFFF5F7FF),
)

@Composable
fun VMessProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
