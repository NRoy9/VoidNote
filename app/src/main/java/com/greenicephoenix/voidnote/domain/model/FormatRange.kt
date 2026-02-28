package com.greenicephoenix.voidnote.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a formatting type applied to a text range in a note.
 *
 * DOMAIN LAYER — no Compose/UI dependencies.
 * Serializable so FormatRange objects can be stored as JSON in Room.
 *
 * WHY SERIALIZABLE ENUM IS SAFE TO EXTEND:
 * kotlinx.serialization encodes enum values by name (e.g. "BOLD", "STRIKETHROUGH").
 * Adding a new value like STRIKETHROUGH does NOT break existing notes — old notes
 * simply have no FormatRange with type=STRIKETHROUGH, which is correct.
 * No database migration required.
 */
@Serializable
enum class FormatType {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    HEADING_SMALL,
    HEADING_NORMAL,
    HEADING_LARGE
}

/**
 * A single formatted range inside a note's content.
 *
 * @param start Start index (inclusive) in the logical content string.
 * @param end   End index (exclusive) in the logical content string.
 * @param type  The format to apply across [start, end).
 *
 * Example: FormatRange(0, 4, BOLD) makes the first 4 characters bold.
 */
@Serializable
data class FormatRange(
    val start: Int,
    val end: Int,
    val type: FormatType
)