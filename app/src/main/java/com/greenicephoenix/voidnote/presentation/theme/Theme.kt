package com.greenicephoenix.voidnote.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark Color Scheme - Nothing-Inspired Design
 *
 * This is our primary theme - dark mode first approach
 * Pure blacks for OLED battery efficiency
 */
private val DarkColorScheme = darkColorScheme(
    // Primary colors - Main brand color (Nothing Red accent)
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = VoidAccentDark,
    onPrimaryContainer = VoidWhite,

    // Secondary colors - Less prominent actions
    secondary = VoidLightGray,
    onSecondary = VoidWhite,
    secondaryContainer = VoidGray,
    onSecondaryContainer = VoidTextSecondary,

    // Tertiary colors - Alternative accent
    tertiary = VoidTextSecondary,
    onTertiary = VoidBlack,
    tertiaryContainer = VoidLightGray,
    onTertiaryContainer = VoidWhite,

    // Background colors
    background = VoidBlack,
    onBackground = VoidWhite,

    // Surface colors (cards, dialogs, etc.)
    surface = VoidDarkGray,
    onSurface = VoidWhite,
    surfaceVariant = VoidGray,
    onSurfaceVariant = VoidTextSecondary,

    // Outline colors (borders, dividers)
    outline = VoidLightGray,
    outlineVariant = VoidBorderGray,

    // Error colors
    error = VoidError,
    onError = VoidWhite,
    errorContainer = VoidAccentDark,
    onErrorContainer = VoidWhite,

    // Inverse colors (for snackbars, etc.)
    inverseSurface = VoidWhite,
    inverseOnSurface = VoidBlack,
    inversePrimary = VoidAccent,

    // Scrim (overlay for dialogs)
    scrim = VoidBlack.copy(alpha = 0.7f)
)

/**
 * Light Color Scheme - For users who prefer light mode
 *
 * Inverted color scheme while maintaining Nothing aesthetic
 */
private val LightColorScheme = lightColorScheme(
    // Primary colors
    primary = VoidAccent,
    onPrimary = VoidWhite,
    primaryContainer = VoidAccent.copy(alpha = 0.1f),
    onPrimaryContainer = VoidAccent,

    // Secondary colors
    secondary = VoidLightTextSecondary,
    onSecondary = VoidWhite,
    secondaryContainer = VoidLightBorder,
    onSecondaryContainer = VoidLightText,

    // Tertiary colors
    tertiary = VoidLightTextTertiary,
    onTertiary = VoidWhite,
    tertiaryContainer = VoidLightBorder,
    onTertiaryContainer = VoidLightText,

    // Background colors
    background = VoidLightBg,
    onBackground = VoidLightText,

    // Surface colors
    surface = VoidLightCard,
    onSurface = VoidLightText,
    surfaceVariant = VoidLightBg,
    onSurfaceVariant = VoidLightTextSecondary,

    // Outline colors
    outline = VoidLightBorder,
    outlineVariant = VoidLightBorder.copy(alpha = 0.5f),

    // Error colors
    error = VoidError,
    onError = VoidWhite,
    errorContainer = VoidError.copy(alpha = 0.1f),
    onErrorContainer = VoidError,

    // Inverse colors
    inverseSurface = VoidLightText,
    inverseOnSurface = VoidLightBg,
    inversePrimary = VoidAccent,

    // Scrim
    scrim = VoidBlack.copy(alpha = 0.5f)
)

/**
 * VoidNoteTheme - Main theme composable
 *
 * This wraps your entire app and provides consistent theming
 *
 * @param darkTheme Whether to use dark theme (defaults to system setting)
 * @param content The composable content to theme
 */
@Composable
fun VoidNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Select color scheme based on theme
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    // Get the current view to modify system UI
    val view = LocalView.current

    // Update system bars (status bar and navigation bar) to match theme
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Set status bar color to match background
            window.statusBarColor = colorScheme.background.toArgb()

            // Set navigation bar color to match background
            window.navigationBarColor = colorScheme.background.toArgb()

            // Update system bar icon colors (dark icons on light theme, light icons on dark theme)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Apply Material Theme with our custom color scheme and typography
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoidTypography,
        content = content
    )
}