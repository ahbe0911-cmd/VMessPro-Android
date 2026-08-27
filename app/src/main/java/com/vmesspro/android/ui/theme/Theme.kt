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
    background = Color(0xFF030712),
    onBackground = Color(0xFFF5F8FF),
    surface = Color(0xFF091322),
    onSurface = Color(0xFFF5F8FF),
    surfaceVariant = Color(0xFF101D31),
    onSurfaceVariant = Color(0xFFB8C7DD),
    outline = Color(0xFF315070),
    outlineVariant = Color(0xFF1A2E47),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF3A0008),
)

val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extra_bold, FontWeight.ExtraBold),
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
