package com.greenicephoenix.voidnote.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a formatting type that can be applied to text.
 *
 * IMPORTANT:
 * - This is part of DOMAIN layer.
 * - It must NOT depend on Compose or UI classes.
 * - It is serializable for database storage.
 */
@Serializable
enum class FormatType {
    BOLD,
    ITALIC,
    UNDERLINE,
    HEADING_SMALL,
    HEADING_NORMAL,
    HEADING_LARGE
}

/**
 * Represents a formatted text range inside a note.
 *
 * start: inclusive index
 * end: exclusive index
 * type: formatting applied
 *
 * Example:
 * FormatRange(0, 4, BOLD)
 */
@Serializable
data class FormatRange(
    val start: Int,
    val end: Int,
    val type: FormatType
)
