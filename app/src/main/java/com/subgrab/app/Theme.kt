package com.subgrab.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0A),
    onPrimary = Color.White,
    secondary = Color(0xFF1A4F94),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE3FA),
    onSecondaryContainer = Color(0xFF123A6F),
    tertiary = Color(0xFF1B6B2B),
    tertiaryContainer = Color(0xFFCDEFD0),
    onTertiaryContainer = Color(0xFF0B4A18),
    error = Color(0xFF9B1C1C),
    errorContainer = Color(0xFFFBE4E4),
    onErrorContainer = Color(0xFF6E1111),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF4F4F2),
    onSurfaceVariant = Color(0xFF6B6B66),
    outline = Color(0xFF8A8A85),
    outlineVariant = Color(0xFFE3E3DF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F0),
    onPrimary = Color(0xFF0A0A0A),
    secondary = Color(0xFF8DB8F0),
    onSecondary = Color(0xFF0B2A52),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFCFE3FA),
    tertiary = Color(0xFF8FD99B),
    tertiaryContainer = Color(0xFF1C4A27),
    onTertiaryContainer = Color(0xFFCDEFD0),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFBE4E4),
    background = Color(0xFF121212),
    onBackground = Color(0xFFECECEA),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFECECEA),
    surfaceVariant = Color(0xFF1E1F22),
    onSurfaceVariant = Color(0xFFA8A8A2),
    outline = Color(0xFF8A8A85),
    outlineVariant = Color(0xFF34353A)
)

/** Màu cảnh báo (hổ phách) không có sẵn trong bảng màu Material 3. */
object SubGrabExtras {
    val warningContainer: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4A3A12) else Color(0xFFFBE3B0)
    val onWarningContainer: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF5D98B) else Color(0xFF5C3B00)
    val highlight: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6B5A1E) else Color(0xFFFBE0A6)
}

@Composable
fun SubGrabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) { content() }
}
