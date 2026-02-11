package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing System - 8dp Grid System
 *
 * Everything aligns to 8dp increments for visual consistency
 * This is a standard design practice used by Material Design and Nothing
 *
 * Usage: Use these constants instead of hardcoded values
 * Example: padding(Spacing.medium) instead of padding(16.dp)
 */
object Spacing {

    /** 4dp - Extra small spacing (tight gaps, icon padding) */
    val extraSmall = 4.dp

    /** 8dp - Small spacing (between related items) */
    val small = 8.dp

    /** 12dp - Small-medium spacing */
    val smallMedium = 12.dp

    /** 16dp - Medium spacing (standard padding) */
    val medium = 16.dp

    /** 20dp - Medium-large spacing */
    val mediumLarge = 20.dp

    /** 24dp - Large spacing (section separation) */
    val large = 24.dp

    /** 32dp - Extra large spacing (major sections) */
    val extraLarge = 32.dp

    /** 40dp - Extra extra large spacing */
    val extraExtraLarge = 40.dp

    /** 48dp - Huge spacing (screen edges, major separators) */
    val huge = 48.dp
}

/**
 * Elevation values for Material Design 3
 * Used for card shadows and elevated surfaces
 */
object Elevation {

    /** No elevation - Flat surface */
    val none = 0.dp

    /** Small elevation - Subtle lift */
    val small = 2.dp

    /** Medium elevation - Standard cards */
    val medium = 4.dp

    /** Large elevation - Dialogs, menus */
    val large = 8.dp

    /** Extra large elevation - Floating action button */
    val extraLarge = 12.dp
}