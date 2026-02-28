package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.domain.model.FormatType

/**
 * Simple formatting info container
 */
data class FormattingInfo(
    val text: String,
    val formats: List<FormatRange> = emptyList()
)

/**
 * Convert a plain text string + list of FormatRanges into an AnnotatedString
 * that Compose's BasicTextField can render with inline styles.
 *
 * Each FormatRange maps to a SpanStyle applied over [start, end).
 * Ranges that fall outside the text length are safely skipped.
 *
 * STRIKETHROUGH implementation:
 * TextDecoration values can be combined using + operator in Compose.
 * UNDERLINE and STRIKETHROUGH both use TextDecoration, but they can
 * coexist because we apply each format as a separate SpanStyle.
 * Compose merges overlapping SpanStyles automatically.
 */
fun applyFormatting(text: String, formats: List<FormatRange>): AnnotatedString {
    val builder = AnnotatedString.Builder(text)

    formats.forEach { range ->
        // Guard: skip ranges that are out of bounds (can happen during editing)
        if (range.start < text.length && range.end <= text.length && range.start < range.end) {
            val style = when (range.type) {
                FormatType.BOLD         -> SpanStyle(fontWeight = FontWeight.Bold)
                FormatType.ITALIC       -> SpanStyle(fontStyle = FontStyle.Italic)
                FormatType.UNDERLINE    -> SpanStyle(textDecoration = TextDecoration.Underline)
                FormatType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                FormatType.HEADING_SMALL  -> SpanStyle(fontSize = 14.sp)
                FormatType.HEADING_NORMAL -> SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                FormatType.HEADING_LARGE  -> SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            builder.addStyle(style, range.start, range.end)
        }
    }

    return builder.toAnnotatedString()
}

/**
 * Add a format to a list of format ranges.
 *
 * First removes any existing range of the same type that overlaps the
 * new range (prevents duplicate/conflicting formats), then appends the new one.
 */
fun addFormat(
    formats: List<FormatRange>,
    start: Int,
    end: Int,
    type: FormatType
): List<FormatRange> {
    val filtered = formats.filter { existing ->
        !(existing.type == type && existing.start < end && existing.end > start)
    }
    return filtered + FormatRange(start, end, type)
}

/**
 * Remove all formats of a given type that overlap [start, end).
 */
fun removeFormat(
    formats: List<FormatRange>,
    start: Int,
    end: Int,
    type: FormatType
): List<FormatRange> {
    return formats.filter { existing ->
        !(existing.type == type && existing.start < end && existing.end > start)
    }
}

/**
 * Returns true if the entire [start, end) range is covered by at least
 * one FormatRange of the given type.
 */
fun hasFormat(
    formats: List<FormatRange>,
    start: Int,
    end: Int,
    type: FormatType
): Boolean {
    return formats.any { existing ->
        existing.type == type && existing.start <= start && existing.end >= end
    }
}

/**
 * Remove all formatting from a note.
 */
fun clearAllFormatting(formats: List<FormatRange>): List<FormatRange> = emptyList()