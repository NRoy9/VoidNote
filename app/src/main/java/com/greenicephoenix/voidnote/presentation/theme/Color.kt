package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Nothing-Inspired Color Palette for Void Note
 *
 * Design Philosophy:
 * - Monochromatic scheme (blacks, whites, grays)
 * - High contrast for readability
 * - OLED-friendly pure blacks for battery efficiency
 * - Minimal use of accent colors (Nothing's signature red)
 */

// ============================================
// DARK MODE COLORS (Default Theme)
// ============================================

/** Pure black - Primary background for OLED displays */
val VoidBlack = Color(0xFF000000)

/** Dark gray - Card backgrounds and elevated surfaces */
val VoidDarkGray = Color(0xFF0A0A0A)

/** Medium dark gray - Secondary backgrounds */
val VoidGray = Color(0xFF1A1A1A)

/** Light gray - Borders and dividers */
val VoidLightGray = Color(0xFF2A2A2A)

/** Very light gray - Subtle borders */
val VoidBorderGray = Color(0xFF333333)

/** Pure white - Primary text on dark backgrounds */
val VoidWhite = Color(0xFFFFFFFF)

/** Light gray text - Secondary text and hints */
val VoidTextSecondary = Color(0xFFB0B0B0)

/** Dim gray text - Tertiary text and timestamps */
val VoidTextTertiary = Color(0xFF707070)

/** Nothing Red - Accent color (used sparingly) */
val VoidAccent = Color(0xFFFF3B30)

/** Accent variant - Slightly darker red for pressed states */
val VoidAccentDark = Color(0xFFCC2E26)

/** Success green - For positive actions */
val VoidSuccess = Color(0xFF34C759)

/** Warning amber - For caution states */
val VoidWarning = Color(0xFFFFCC00)

/** Error red - For destructive actions */
val VoidError = Color(0xFFFF3B30)

// ============================================
// EXTRA DARK MODE COLORS (Pure OLED)
// ============================================

/** Extra dark - Slightly lifted from pure black for contrast */
val VoidExtraDarkGray = Color(0xFF050505)

// ============================================
// LIGHT MODE COLORS
// ============================================

/** Light background - Off-white for reduced eye strain */
val VoidLightBg = Color(0xFFFAFAFA)

/** Pure white cards - Elevated surfaces */
val VoidLightCard = Color(0xFFFFFFFF)

/** Light border - Subtle dividers */
val VoidLightBorder = Color(0xFFE0E0E0)

/** Dark text on light background */
val VoidLightText = Color(0xFF000000)

/** Secondary text on light background */
val VoidLightTextSecondary = Color(0xFF505050)

/** Tertiary text on light background */
val VoidLightTextTertiary = Color(0xFF909090)

// ============================================
// SPECIAL UI COLORS
// ============================================

/** Dot matrix pattern overlay (semi-transparent white) */
val DotMatrixOverlay = Color(0x0AFFFFFF)

/** Glassmorphism blur background */
val GlassMorphBg = Color(0x1AFFFFFF)

/** Tag background colors (subtle, pastel tones) */
val TagBlue = Color(0xFF1E3A5F)
val TagGreen = Color(0xFF1E5F3A)
val TagPurple = Color(0xFF3A1E5F)
val TagOrange = Color(0xFF5F3A1E)
val TagPink = Color(0xFF5F1E3A)