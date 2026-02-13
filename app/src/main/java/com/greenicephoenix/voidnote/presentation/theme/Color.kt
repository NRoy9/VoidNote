package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Nothing-Inspired Color Palette for Void Note
 *
 * Design Philosophy:
 * - THREE distinct themes: Light, Dark (lifted blacks), Extra Dark (pure OLED)
 * - High contrast for readability
 * - OLED-friendly pure blacks for battery efficiency (Extra Dark only)
 * - Minimal use of accent colors (Nothing's signature red)
 */

// ============================================
// DARK MODE COLORS (Default Theme - LIFTED)
// ============================================

/** Dark background - Lifted from pure black for better contrast */
val VoidBlack = Color(0xFF121212)  // ✅ CHANGED: Was 0xFF000000, now lifted for visibility

/** Dark gray - Card backgrounds and elevated surfaces */
val VoidDarkGray = Color(0xFF1E1E1E)  // ✅ CHANGED: Was 0xFF0A0A0A, now lighter

/** Medium dark gray - Secondary backgrounds */
val VoidGray = Color(0xFF2A2A2A)  // ✅ CHANGED: Was 0xFF1A1A1A, now lighter

/** Light gray - Borders and dividers */
val VoidLightGray = Color(0xFF3A3A3A)  // ✅ CHANGED: More visible borders

/** Very light gray - Subtle borders */
val VoidBorderGray = Color(0xFF4A4A4A)  // ✅ CHANGED: More contrast

/** Pure white - Primary text on dark backgrounds */
val VoidWhite = Color(0xFFFFFFFF)

/** Light gray text - Secondary text and hints */
val VoidTextSecondary = Color(0xFFB0B0B0)

/** Dim gray text - Tertiary text and timestamps */
val VoidTextTertiary = Color(0xFF808080)  // ✅ CHANGED: Slightly lighter for readability

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
// EXTRA DARK MODE COLORS (Pure OLED Black)
// ============================================

/** Pure OLED black - Maximum battery saving on AMOLED screens */
val VoidExtraBlack = Color(0xFF000000)  // ✅ True black for OLED

/** Extra dark surface - Barely lifted from pure black */
val VoidExtraDarkSurface = Color(0xFF0A0A0A)  // ✅ Minimal lift for card visibility

/** Extra dark secondary - Subtle elevation */
val VoidExtraDarkSecondary = Color(0xFF151515)  // ✅ Subtle contrast for layering

/** Extra dark borders - Very subtle dividers */
val VoidExtraDarkBorder = Color(0xFF1F1F1F)  // ✅ Minimal borders, maximum darkness

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