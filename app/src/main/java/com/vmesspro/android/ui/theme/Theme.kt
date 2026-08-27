package com.vmesspro.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF36D9FF),
    onPrimary = Color(0xFF00151D),
    primaryContainer = Color(0xFF073449),
    onPrimaryContainer = Color(0xFFD7F7FF),
    secondary = Color(0xFFA98BFF),
    onSecondary = Color(0xFF160049),
    secondaryContainer = Color(0xFF2A1F5A),
    onSecondaryContainer = Color(0xFFE9E0FF),
    tertiary = Color(0xFF4EE6B1),
    onTertiary = Color(0xFF002116),
    background = Color(0xFF050A14),
    onBackground = Color(0xFFF4F7FF),
    surface = Color(0xFF0A1221),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF111D31),
    onSurfaceVariant = Color(0xFFB6C4DC),
    outline = Color(0xFF2C405F),
    outlineVariant = Color(0xFF1D2A40),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF3A0008),
)

@Composable
fun VMessProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
