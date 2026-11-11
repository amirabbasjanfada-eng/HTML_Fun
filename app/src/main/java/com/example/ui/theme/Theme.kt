package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ThemeMode

// Classic minimal: warm paper white / ink black, hairline borders, one accent (the ink itself).
private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3A3A37),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFAFAF7),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEEEEE9),
    onSurfaceVariant = Color(0xFF6A6A64),
    surfaceTint = Color(0xFF111111),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F1),
    surfaceContainer = Color(0xFFF0F0EC),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEAEAE5),
    outline = Color(0xFFC8C8C1),
    outlineVariant = Color(0xFFE3E3DC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEDEDE8),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFC9C9C3),
    onSecondary = Color(0xFF111111),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFEDEDE8),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFEDEDE8),
    surfaceVariant = Color(0xFF1B1B1A),
    onSurfaceVariant = Color(0xFF9B9B95),
    surfaceTint = Color(0xFFEDEDE8),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF141413),
    surfaceContainer = Color(0xFF181817),
    surfaceContainerHigh = Color(0xFF1F1F1E),
    surfaceContainerHighest = Color(0xFF262625),
    outline = Color(0xFF3F3F3B),
    outlineVariant = Color(0xFF262624),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.4.sp
    )
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

/** Resolves the user's choice (System / Light / Dark) into a concrete dark-or-light decision. */
@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun WebPaperTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
