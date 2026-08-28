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
    primary = Color(0xFF26D8E8),
    onPrimary = Color(0xFF04191D),
    primaryContainer = Color(0xFF163A4A),
    onPrimaryContainer = Color(0xFFD7FAFF),
    secondary = Color(0xFF9B6DFF),
    onSecondary = Color(0xFF170A35),
    tertiary = Color(0xFF3DDEA0),
    onTertiary = Color(0xFF041A12),
    background = Color(0xFF081226),
    onBackground = Color(0xFFF7F8FF),
    surface = Color(0xFF111B36),
    onSurface = Color(0xFFF7F8FF),
    surfaceVariant = Color(0xFF1B2545),
    onSurfaceVariant = Color(0xFFB9C4E2),
    outline = Color(0xFF526080),
    error = Color(0xFFFF6477),
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
