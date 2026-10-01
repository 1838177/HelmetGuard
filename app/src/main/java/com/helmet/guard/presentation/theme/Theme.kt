package com.helmet.guard.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = StatusSafe,
    onPrimary = DarkBackground,
    secondary = DarkSurfaceVariant,
    onSecondary = TextPrimaryDark,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder,
    error = StatusAlert,
    onError = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = StatusSafeLight,
    onPrimary = LightSurface,
    secondary = LightSurfaceVariant,
    onSecondary = TextPrimaryLight,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder,
    error = StatusAlert,
    onError = LightSurface
)

@Composable
fun HelmetGuardTheme(
    darkTheme: Boolean = true, // 默认深色工业仪表盘模式
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
