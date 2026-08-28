package com.vmesspro.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vmesspro.android.R

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DD6F5),
    onPrimary = Color(0xFF001419),
    primaryContainer = Color(0xFF0A3440),
    onPrimaryContainer = Color(0xFFD6F7FF),
    secondary = Color(0xFFB8C8D9),
    onSecondary = Color(0xFF172A38),
    tertiary = Color(0xFF53DDAA),
    background = Color(0xFF071019),
    onBackground = Color(0xFFF5F8FA),
    surface = Color(0xFF0C1620),
    onSurface = Color(0xFFF5F8FA),
    surfaceVariant = Color(0xFF132330),
    onSurfaceVariant = Color(0xFFBAC8D2),
    outline = Color(0xFF3B5668),
    error = Color(0xFFFF6F7D),
)

val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.vazir() = copy(fontFamily = VazirmatnFontFamily)

private val baseTypography = Typography()
private val AppTypography = Typography(
    displayLarge = baseTypography.displayLarge.vazir(),
    displayMedium = baseTypography.displayMedium.vazir(),
    displaySmall = baseTypography.displaySmall.vazir(),
    headlineLarge = baseTypography.headlineLarge.vazir(),
    headlineMedium = baseTypography.headlineMedium.vazir(),
    headlineSmall = baseTypography.headlineSmall.vazir(),
    titleLarge = baseTypography.titleLarge.vazir(),
    titleMedium = baseTypography.titleMedium.vazir(),
    titleSmall = baseTypography.titleSmall.vazir(),
    bodyLarge = baseTypography.bodyLarge.vazir(),
    bodyMedium = baseTypography.bodyMedium.vazir(),
    bodySmall = baseTypography.bodySmall.vazir(),
    labelLarge = baseTypography.labelLarge.vazir(),
    labelMedium = baseTypography.labelMedium.vazir(),
    labelSmall = baseTypography.labelSmall.vazir(),
)

@Composable
fun VMessProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
