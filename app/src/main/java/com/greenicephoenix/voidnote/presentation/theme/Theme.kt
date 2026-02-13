package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark Color Scheme - Lifted blacks for better contrast
 *
 * This is the DEFAULT dark theme (not pure OLED)
 * Background: #121212 (Material Design standard)
 * Provides better depth perception and reduces eye strain
 */
private val DarkColorScheme = darkColorScheme(
    // Primary colors
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = VoidDarkGray,
    onPrimaryContainer = VoidWhite,

    // Secondary colors
    secondary = VoidLightGray,
    onSecondary = VoidWhite,
    secondaryContainer = VoidGray,
    onSecondaryContainer = VoidWhite,

    // Background colors - LIFTED from pure black
    background = VoidBlack,  // ✅ #121212 - Visible difference from Extra Dark!
    onBackground = VoidWhite,

    // Surface colors - Cards, bottom sheets, etc.
    surface = VoidDarkGray,  // ✅ #1E1E1E - Clear elevation
    onSurface = VoidWhite,

    surfaceVariant = VoidGray,  // ✅ #2A2A2A - Secondary surfaces
    onSurfaceVariant = VoidTextSecondary,

    // Outline colors - Borders and dividers
    outline = VoidLightGray,  // ✅ #3A3A3A - More visible borders
    outlineVariant = VoidBorderGray,

    // Error colors
    error = VoidAccent,
    onError = VoidWhite
)

/**
 * Light Color Scheme - Clean, minimal light theme
 *
 * Background: #FAFAFA (off-white to reduce eye strain)
 * Follows Material Design 3 guidelines
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
    onBackground = VoidLightText,

    surface = VoidLightCard,
    onSurface = VoidLightText,

    surfaceVariant = Color(0xFFF4DDDB),
    onSurfaceVariant = Color(0xFF534341),

    outline = VoidLightBorder,
    outlineVariant = Color(0xFFD8C2BF),

    error = Color(0xFFBA1A1A),
    onError = VoidWhite
)

/**
 * Extra Dark Color Scheme - Pure OLED black
 *
 * Background: #000000 (true black for AMOLED screens)
 * Maximum battery saving and aggressive dark mode
 * Best for night use and OLED devices
 */
private val ExtraDarkColorScheme = darkColorScheme(
    // Primary colors
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = VoidExtraDarkSurface,
    onPrimaryContainer = VoidWhite,

    // Secondary colors
    secondary = VoidLightGray,
    onSecondary = VoidWhite,
    secondaryContainer = VoidExtraDarkSurface,
    onSecondaryContainer = VoidWhite,

    // Background - PURE OLED BLACK
    background = VoidExtraBlack,  // ✅ #000000 - True black for OLED!
    onBackground = VoidWhite,

    // Surface colors - Minimal lift for depth
    surface = VoidExtraDarkSurface,  // ✅ #0A0A0A - Barely visible elevation
    onSurface = VoidWhite,

    surfaceVariant = VoidExtraDarkSecondary,  // ✅ #151515 - Subtle layering
    onSurfaceVariant = VoidTextSecondary,

    // Outline colors - Very subtle
    outline = VoidExtraDarkBorder,  // ✅ #1F1F1F - Minimal borders
    outlineVariant = VoidExtraDarkSecondary,

    // Error colors
    error = VoidAccent,
    onError = VoidWhite
)

/**
 * Main Theme Composable
 *
 * Provides three distinct visual themes:
 * 1. Light Mode - Off-white background, best for daytime
 * 2. Dark Mode - Lifted blacks (#121212), balanced dark theme
 * 3. Extra Dark - Pure OLED (#000000), maximum battery saving
 *
 * @param darkTheme Whether to use dark theme (default: system preference)
 * @param extraDark Whether to use pure OLED black theme
 * @param content The content to theme
 */
@Composable
fun VoidNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    extraDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        extraDark -> {
            // Pure OLED black - Maximum battery saving
            ExtraDarkColorScheme
        }
        darkTheme -> {
            // Lifted blacks - Better contrast and readability
            DarkColorScheme
        }
        else -> {
            // Light theme - Off-white background
            LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoidTypography,
        content = content
    )
}