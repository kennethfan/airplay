package com.airplay.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ──────────────────────────────────────────────────────────────
// 深空蓝青配色 —— 保留影院深色感，但用克制的蓝/青替代高饱和红色
// ──────────────────────────────────────────────────────────────

/** 深色主题：近黑带蓝的底色 + 蓝色主色 + 青色状态强调 */
private val AirPlayDarkColors = darkColorScheme(
    primary = Color(0xFF4E9BFF),
    onPrimary = Color(0xFF04121F),
    primaryContainer = Color(0xFF1B3A5C),
    onPrimaryContainer = Color(0xFFD6E7FF),
    secondary = Color(0xFF38D9C0),
    onSecondary = Color(0xFF00201B),
    secondaryContainer = Color(0xFF12352F),
    onSecondaryContainer = Color(0xFFB8F5EA),
    tertiary = Color(0xFF9C8CFF),
    onTertiary = Color(0xFF120A33),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE8EDF5),
    surface = Color(0xFF141A24),
    onSurface = Color(0xFFE8EDF5),
    surfaceVariant = Color(0xFF1C2432),
    onSurfaceVariant = Color(0xFF93A3B8),
    surfaceContainerLowest = Color(0xFF070A0F),
    surfaceContainerLow = Color(0xFF10151D),
    surfaceContainer = Color(0xFF141A24),
    surfaceContainerHigh = Color(0xFF19212C),
    surfaceContainerHighest = Color(0xFF1F2835),
    outline = Color(0xFF2A3546),
    outlineVariant = Color(0xFF1E2734),
    // 透明 tint：避免 Material3 在所有带 elevation 的卡片上叠加彩色蒙层
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE8EDF5),
    inverseOnSurface = Color(0xFF0B0E14),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD9D9),
    scrim = Color(0xFF000000),
)

/** 浅色主题：冷白底 + 更深的蓝，保证白天可读性 */
private val AirPlayLightColors = lightColorScheme(
    primary = Color(0xFF1D6FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E7FF),
    onPrimaryContainer = Color(0xFF06213F),
    secondary = Color(0xFF0E9E8D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F4ED),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF6750D8),
    onTertiary = Color.White,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF0F1520),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1520),
    surfaceVariant = Color(0xFFEEF2F8),
    onSurfaceVariant = Color(0xFF5A6B82),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F6FB),
    surfaceContainer = Color(0xFFEDF2F9),
    surfaceContainerHigh = Color(0xFFE6EDF6),
    surfaceContainerHighest = Color(0xFFDFE8F3),
    outline = Color(0xFFC2CEDE),
    outlineVariant = Color(0xFFDCE4EE),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF141A24),
    inverseOnSurface = Color(0xFFE8EDF5),
    error = Color(0xFFD13438),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        // darkTheme 现在真正生效：系统切浅色时使用浅色配色
        colorScheme = if (darkTheme) AirPlayDarkColors else AirPlayLightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
