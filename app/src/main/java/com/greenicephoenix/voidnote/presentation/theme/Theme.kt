package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark Color Scheme — GIP Ecosystem Void Deep
 *
 * Background: Void Deep #050508 (GIP spec)
 * Primary accent: Ice Blue #00D4FF (VoidNote Cryo element)
 * Error: Red #FF3B30 (retained for destructive actions only)
 */
private val DarkColorScheme = darkColorScheme(
    // Primary — Ice Blue accent
    primary            = VoidIceBlue,
    onPrimary          = Color(0xFF001F26),          // Dark text on ice blue button
    primaryContainer   = VoidIceBlueDarkContainer,   // #003A47 — tinted dark container
    onPrimaryContainer = VoidIceBlue,                // Ice blue text on dark container

    // Secondary — neutral surface tones
    secondary             = VoidTextSecondary,
    onSecondary           = VoidWhite,
    secondaryContainer    = VoidSurfaceVariant,
    onSecondaryContainer  = VoidWhite,

    // Backgrounds — GIP Void Deep spec
    background   = VoidDeep,     // #050508
    onBackground = VoidWhite,

    // Surfaces — cards, bottom sheets, dialogs
    surface            = VoidSurface,         // #0F0F18
    onSurface          = VoidWhite,
    surfaceVariant     = VoidSurfaceVariant,  // #1A1A28
    onSurfaceVariant   = VoidTextSecondary,

    // Borders
    outline        = VoidBorderDark,     // #2A2A3A
    outlineVariant = VoidBorderVariant,  // #3A3A4A

    // Error — red stays here only
    error   = VoidError,  // #FF3B30
    onError = VoidWhite
)

/**
 * Light Color Scheme — clean light background, Ice Blue accent
 *
 * Background: #FAFAFA (off-white)
 * Primary accent: Ice Blue #00D4FF
 * Error: Red #FF3B30
 */
private val LightColorScheme = lightColorScheme(
    // Primary — Ice Blue accent
    primary            = VoidIceBlueDark,          // Slightly darker for contrast on white
    onPrimary          = VoidWhite,
    primaryContainer   = VoidIceBlueContainer,     // #CCF4FF — light ice tint
    onPrimaryContainer = VoidIceBlueOnContainer,   // #001F26 — dark readable text

    // Secondary — warm neutral
    secondary             = Color(0xFF4A6070),
    onSecondary           = VoidWhite,
    secondaryContainer    = Color(0xFFCCE8F4),
    onSecondaryContainer  = Color(0xFF001F2C),

    // Backgrounds — light
    background   = VoidLightBg,    // #FAFAFA
    onBackground = VoidLightText,  // #050508

    // Surfaces
    surface            = VoidLightCard,
    onSurface          = VoidLightText,
    surfaceVariant     = Color(0xFFDCF0F8),    // Subtle ice tint for variant surfaces
    onSurfaceVariant   = Color(0xFF405060),

    // Borders
    outline        = VoidLightBorder,     // #E0E0E0
    outlineVariant = Color(0xFFB8D8E8),   // Subtle ice-tinted border

    // Error — red
    error   = VoidError,
    onError = VoidWhite
)

/**
 * Extra Dark Color Scheme — Pure OLED black
 *
 * Background: #000000 (true black for AMOLED)
 * Primary accent: Ice Blue #00D4FF
 * Error: Red #FF3B30
 */
private val ExtraDarkColorScheme = darkColorScheme(
    // Primary — Ice Blue accent
    primary            = VoidIceBlue,
    onPrimary          = Color(0xFF001F26),
    primaryContainer   = VoidExtraDarkSurface,   // Near-black container
    onPrimaryContainer = VoidIceBlue,

    // Secondary
    secondary             = VoidTextSecondary,
    onSecondary           = VoidWhite,
    secondaryContainer    = VoidExtraDarkSurface,
    onSecondaryContainer  = VoidWhite,

    // Backgrounds — OLED pure black
    background   = VoidExtraBlack,  // #000000
    onBackground = VoidWhite,

    // Surfaces — minimal lift for depth
    surface            = VoidExtraDarkSurface,    // #0A0A0A
    onSurface          = VoidWhite,
    surfaceVariant     = VoidExtraDarkSecondary,  // #151515
    onSurfaceVariant   = VoidTextSecondary,

    // Borders — barely visible
    outline        = VoidExtraDarkBorder,     // #1F1F1F
    outlineVariant = VoidExtraDarkSecondary,

    // Error — red
    error   = VoidError,
    onError = VoidWhite
)

/**
 * Main Theme Composable
 *
 * Three themes:
 * 1. Light     — off-white (#FAFAFA) background, Ice Blue accent
 * 2. Dark      — Void Deep (#050508) background, Ice Blue accent
 * 3. Extra Dark — OLED black (#000000) background, Ice Blue accent
 *
 * @param darkTheme  Whether to use dark theme (default: system preference)
 * @param extraDark  Whether to use pure OLED black theme
 * @param content    The content to theme
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
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = VoidTypography,
        content     = content
    )
}