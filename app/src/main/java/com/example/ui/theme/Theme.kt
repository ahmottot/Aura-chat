package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = TextPrimary,
    secondary = NeonCyan,
    onSecondary = MidnightBg,
    tertiary = TechPurple,
    onTertiary = TextPrimary,
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    error = TechRed,
    onError = TextPrimary,
    surfaceVariant = DividerColor,
    onSurfaceVariant = TextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightCardSurface,
    secondary = LightSecondary,
    onSecondary = LightCardSurface,
    tertiary = TechPurple,
    onTertiary = LightCardSurface,
    background = LightBg,
    onBackground = LightText,
    surface = LightCardSurface,
    onSurface = LightText,
    error = TechRed,
    onError = LightCardSurface,
    surfaceVariant = LightBg,
    onSurfaceVariant = LightTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to preserve our custom identity colors
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
