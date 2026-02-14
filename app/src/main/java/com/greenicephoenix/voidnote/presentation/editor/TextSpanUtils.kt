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
 * Simple formatting storage
 */
data class FormattingInfo(
    val text: String,
    val formats: List<FormatRange> = emptyList()
)

/**
 * Apply formatting to AnnotatedString for display
 */
fun applyFormatting(text: String, formats: List<FormatRange>): AnnotatedString {
    val builder = AnnotatedString.Builder(text)

    formats.forEach { range ->
        if (range.start < text.length && range.end <= text.length && range.start < range.end) {
            val style = when (range.type) {
                FormatType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                FormatType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                FormatType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                FormatType.HEADING_SMALL -> SpanStyle(
                    fontSize = 16.sp  // ✅ Small
                )
                FormatType.HEADING_NORMAL -> SpanStyle(
                    fontSize = 20.sp  // ✅ Normal (default)
                )
                FormatType.HEADING_LARGE -> SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp  // ✅ Large
                )
            }

            builder.addStyle(style, range.start, range.end)
        }
    }

    return builder.toAnnotatedString()
}

/**
 * Add format to a range
 */
fun addFormat(
    formats: List<FormatRange>,
    start: Int,
    end: Int,
    type: FormatType
): List<FormatRange> {
    // Remove existing format of same type in this range
    val filtered = formats.filter { existing ->
        !(existing.type == type && existing.start < end && existing.end > start)
    }

    return filtered + FormatRange(start, end, type)
}

/**
 * Remove format from a range
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
 * Check if range has format
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
 * Clear all formatting
 */
fun clearAllFormatting(formats: List<FormatRange>): List<FormatRange> {
    return emptyList()
}