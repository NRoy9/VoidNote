package com.greenicephoenix.voidnote.domain.model

import androidx.compose.ui.graphics.Color

/**
 * NoteColor — The set of accent colors a user can assign to a note.
 *
 * DESIGN PHILOSOPHY (Nothing aesthetic):
 * Colors are muted, low-saturation tints — not bright Material colors.
 * In dark mode they appear as subtle card tints.
 * In light mode they are lightly tinted backgrounds.
 *
 * STORAGE:
 * Stored as a nullable String (the enum name) in NoteEntity.
 * null = no color (default card appearance).
 *
 * The enum name is used for DB storage, NOT the ordinal.
 * This means it's safe to reorder, rename, or add variants —
 * as long as existing names are never removed or renamed.
 *
 * COLOR PAIRS (darkCardColor / lightCardColor):
 * Each NoteColor exposes the card background color for both themes.
 * The dot shown in the color picker uses `pickerColor` — a more saturated
 * version of the tint so it's easy to distinguish in the picker UI.
 */
enum class NoteColor(
    val label: String,                    // Human-readable name shown in picker tooltip
    val darkCardColor: Color,             // Card background in dark/extra-dark mode
    val lightCardColor: Color,            // Card background in light mode
    val pickerColor: Color                // Dot color shown in the color picker
) {
    // ─── Red ─────────────────────────────────────────────────────────────────
    RED(
        label          = "Red",
        darkCardColor  = Color(0xFF2E1A1A),   // Deep dark red tint
        lightCardColor = Color(0xFFFFEBEB),   // Soft rose
        pickerColor    = Color(0xFFE53935)    // Vivid red for picker dot
    ),

    // ─── Orange ──────────────────────────────────────────────────────────────
    ORANGE(
        label          = "Orange",
        darkCardColor  = Color(0xFF2E2018),   // Deep dark amber tint
        lightCardColor = Color(0xFFFFF3E0),   // Soft cream
        pickerColor    = Color(0xFFF4511E)    // Vivid deep orange for picker
    ),

    // ─── Yellow ──────────────────────────────────────────────────────────────
    YELLOW(
        label          = "Yellow",
        darkCardColor  = Color(0xFF2C2A18),   // Deep dark gold tint
        lightCardColor = Color(0xFFFFFDE7),   // Soft butter
        pickerColor    = Color(0xFFFDD835)    // Vivid yellow for picker
    ),

    // ─── Green ───────────────────────────────────────────────────────────────
    GREEN(
        label          = "Green",
        darkCardColor  = Color(0xFF182E1C),   // Deep dark forest tint
        lightCardColor = Color(0xFFE8F5E9),   // Soft mint
        pickerColor    = Color(0xFF43A047)    // Vivid green for picker
    ),

    // ─── Blue ────────────────────────────────────────────────────────────────
    BLUE(
        label          = "Blue",
        darkCardColor  = Color(0xFF18202E),   // Deep dark navy tint
        lightCardColor = Color(0xFFE3F2FD),   // Soft sky
        pickerColor    = Color(0xFF1E88E5)    // Vivid blue for picker
    ),

    // ─── Purple ──────────────────────────────────────────────────────────────
    PURPLE(
        label          = "Purple",
        darkCardColor  = Color(0xFF22182E),   // Deep dark violet tint
        lightCardColor = Color(0xFFF3E5F5),   // Soft lavender
        pickerColor    = Color(0xFF8E24AA)    // Vivid purple for picker
    );

    companion object {
        /**
         * Safely convert a String (from the database) back to a NoteColor.
         * Returns null if the string is null, blank, or not a recognised name.
         *
         * This means if a future enum variant is removed, existing DB rows
         * with that name gracefully fall back to "no color" rather than crashing.
         */
        fun fromString(value: String?): NoteColor? {
            if (value.isNullOrBlank()) return null
            return entries.find { it.name == value }
        }
    }
}