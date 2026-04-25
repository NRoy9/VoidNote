package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * GreenIcePhoenix Ecosystem Color Palette for Void Note
 *
 * Design Philosophy:
 * - THREE distinct themes: Light, Dark (Void Deep), Extra Dark (pure OLED)
 * - VoidNote element: Ice / Cryo — primary accent is #00D4FF
 * - Deep space blacks for dark/extra-dark backgrounds (GIP ecosystem spec)
 * - Red (#FF3B30) retained strictly as error color — NOT as accent
 * - High contrast for readability across all themes
 */

// ============================================
// ICE / CRYO ACCENT (VoidNote Primary)
// ============================================

/** Ice Blue — VoidNote primary accent. Piercing cyan representing crystalline security. */
val VoidIceBlue = Color(0xFF00D4FF)

/** Ice Blue dimmed — for pressed/ripple states on the accent */
val VoidIceBlueDark = Color(0xFF00A8CC)

/** Ice Blue surface container — used for primaryContainer in light mode */
val VoidIceBlueContainer = Color(0xFFCCF4FF)

/** Ice Blue on-container text — readable dark tone on the light ice container */
val VoidIceBlueOnContainer = Color(0xFF001F26)

/** Ice Blue dark container — used for primaryContainer in dark/extra-dark mode */
val VoidIceBlueDarkContainer = Color(0xFF003A47)

// ============================================
// VOID BACKGROUNDS (GIP Ecosystem Spec)
// ============================================

/** Void Deep — absolute base background for dark mode. From GIP spec. */
val VoidDeep = Color(0xFF050508)

/** Void Surface — elevated surfaces, cards, dialogs in dark mode. From GIP spec. */
val VoidSurface = Color(0xFF0F0F18)

/** Void Surface Variant — secondary surfaces and input backgrounds in dark mode */
val VoidSurfaceVariant = Color(0xFF1A1A28)

/** Void Border — borders and dividers in dark mode */
val VoidBorderDark = Color(0xFF2A2A3A)

/** Void Border Variant — subtle secondary borders */
val VoidBorderVariant = Color(0xFF3A3A4A)

// ============================================
// EXTRA DARK MODE (Pure OLED Black)
// ============================================

/** Pure OLED black — true black for maximum AMOLED battery saving */
val VoidExtraBlack = Color(0xFF000000)

/** Extra dark surface — minimal lift for card visibility on OLED */
val VoidExtraDarkSurface = Color(0xFF0A0A0A)

/** Extra dark secondary — subtle elevation layer */
val VoidExtraDarkSecondary = Color(0xFF151515)

/** Extra dark borders — barely visible dividers on OLED */
val VoidExtraDarkBorder = Color(0xFF1F1F1F)

// ============================================
// TEXT COLORS
// ============================================

/** Pure white — primary text on all dark backgrounds */
val VoidWhite = Color(0xFFFFFFFF)

/** Secondary text — hints, metadata, timestamps on dark backgrounds */
val VoidTextSecondary = Color(0xFFB0B0B0)

/** Tertiary text — very subtle labels on dark backgrounds */
val VoidTextTertiary = Color(0xFF808080)

// ============================================
// LIGHT MODE COLORS
// ============================================

/** Light background — off-white for reduced eye strain */
val VoidLightBg = Color(0xFFFAFAFA)

/** Pure white cards — elevated surfaces in light mode */
val VoidLightCard = Color(0xFFFFFFFF)

/** Light border — subtle dividers in light mode */
val VoidLightBorder = Color(0xFFE0E0E0)

/** Dark text on light background */
val VoidLightText = Color(0xFF050508)

/** Secondary text on light background */
val VoidLightTextSecondary = Color(0xFF505060)

/** Tertiary text on light background */
val VoidLightTextTertiary = Color(0xFF909090)

// ============================================
// SEMANTIC COLORS
// ============================================

/** Error red — destructive actions and errors ONLY. Not used as accent. */
val VoidError = Color(0xFFFF3B30)

/** Error red dark variant — pressed state for error elements */
val VoidErrorDark = Color(0xFFCC2E26)

/** Success green — positive confirmations */
val VoidSuccess = Color(0xFF34C759)

/** Warning amber — caution states */
val VoidWarning = Color(0xFFFFCC00)

// ============================================
// LEGACY ALIAS (keep existing references compiling)
// ============================================

/**
 * VoidAccent — kept for any existing references in non-theme files.
 * Points to VoidError (red) since red is now error-only.
 * Prefer VoidIceBlue for any new accent usage.
 * TODO: replace call sites with VoidError directly and remove this alias.
 */
val VoidAccent = VoidError

// ============================================
// SPECIAL UI COLORS
// ============================================

/** Dot matrix pattern overlay (semi-transparent white) */
val DotMatrixOverlay = Color(0x0AFFFFFF)

/** Glassmorphism blur background — Void Glass from GIP spec */
val GlassMorphBg = Color(0x330F0F18)

/** Tag background colors — ice-tinted dark tones for ecosystem consistency */
val TagBlue    = Color(0xFF0D2A40)
val TagGreen   = Color(0xFF1E5F3A)
val TagPurple  = Color(0xFF2A1A40)
val TagOrange  = Color(0xFF5F3A1E)
val TagPink    = Color(0xFF5F1E3A)