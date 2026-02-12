package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark Color Scheme - Primary theme
 */
private val DarkColorScheme = darkColorScheme(
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = VoidDarkGray,
    onPrimaryContainer = VoidWhite,

    secondary = VoidLightGray,
    onSecondary = VoidWhite,
    secondaryContainer = VoidGray,
    onSecondaryContainer = VoidWhite,

    background = VoidBlack,
    onBackground = VoidWhite,

    surface = VoidDarkGray,
    onSurface = VoidWhite,

    surfaceVariant = VoidGray,
    onSurfaceVariant = VoidTextSecondary,

    outline = VoidLightGray,
    outlineVariant = VoidGray,

    error = VoidAccent,
    onError = VoidWhite
)

/**
 * Light Color Scheme
 */
private val LightColorScheme = lightColorScheme(
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),

    secondary = Color(0xFF775652),
    onSecondary = VoidWhite,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1512),

    background = VoidLightBg,
    onBackground = Color(0xFF1A1C1E),

    surface = VoidLightCard,
    onSurface = Color(0xFF1A1C1E),

    surfaceVariant = Color(0xFFF4DDDB),
    onSurfaceVariant = Color(0xFF534341),

    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF),

    error = Color(0xFFBA1A1A),
    onError = VoidWhite
)

/**
 * Extra Dark Color Scheme - Pure OLED black
 */
private val ExtraDarkColorScheme = darkColorScheme(
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = Color(0xFF0A0A0A),
    onPrimaryContainer = VoidWhite,

    secondary = VoidLightGray,
    onSecondary = VoidWhite,
    secondaryContainer = Color(0xFF0A0A0A),
    onSecondaryContainer = VoidWhite,

    background = Color(0xFF000000), // Pure OLED black
    onBackground = VoidWhite,

    surface = Color(0xFF0A0A0A),
    onSurface = VoidWhite,

    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = VoidTextSecondary,

    outline = Color(0xFF2C2C2C),
    outlineVariant = Color(0xFF1E1E1E),

    error = VoidAccent,
    onError = VoidWhite
)

/**
 * Main Theme Composable
 */
@Composable
fun VoidNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    extraDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        extraDark -> ExtraDarkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoidTypography,
        content = content
    )
}