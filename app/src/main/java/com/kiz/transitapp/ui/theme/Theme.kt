package com.kiz.transitapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

// Define Material 3 color schemes using your color palette
private val LightColorScheme = lightColorScheme(
    primary = CyanAccent,
    onPrimary = DarkOnPrimary,
    primaryContainer = PastelBlue,
    onPrimaryContainer = DarkCharcoal,
    secondary = NiceBlue,
    onSecondary = White,
    secondaryContainer = MinBlue,
    onSecondaryContainer = DarkCharcoal,
    tertiary = PastelGreen,
    onTertiary = DarkCharcoal,
    error = ErrorRed,
    onError = White,
    background = CreamyWhiteBackground,
    onBackground = DarkCharcoal,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = OffWhite,
    onSurfaceVariant = Gray2,
    outline = Gray3
)

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = DarkOnPrimary,
    primaryContainer = DeepBlue,
    onPrimaryContainer = PastelBlue,
    secondary = MaxBlue,
    onSecondary = DarkCharcoal,
    secondaryContainer = DeepNavy,
    onSecondaryContainer = MaxBlue,
    tertiary = PastelGreen,
    onTertiary = DarkCharcoal,
    error = PastelRed,
    onError = White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkCharcoal,
    onSurfaceVariant = GreyText,
    outline = Gray2
)

@Composable
fun NewTransitAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
