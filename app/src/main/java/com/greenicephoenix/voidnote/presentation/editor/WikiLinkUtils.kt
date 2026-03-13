package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * WikiLinkUtils — utilities for [[wiki link]] syntax detection and rendering.
 *
 * SPRINT 11 · Task 01
 *
 * ─── SYNTAX ──────────────────────────────────────────────────────────────────
 *
 * [[Note Title]]
 *
 * The text between [[ and ]] is the EXACT title of another note.
 * Matching is case-insensitive and whitespace-trimmed at resolution time.
 *
 * Examples:
 *   [[Meeting Notes]]          → links to a note titled "Meeting Notes"
 *   [[Daily Standup 2026]]     → links to a note titled "Daily Standup 2026"
 *   [[Project Ideas]]          → created if no note with that title exists
 *
 * ─── HOW LINKS ARE DISPLAYED ─────────────────────────────────────────────────
 *
 * EDIT MODE:
 *   The [[...]] text stays as plain text in the editor (Compose's BasicTextField
 *   does not support tappable inline spans inside editable text without a full
 *   VisualTransformation implementation, which is very complex).
 *   Instead, a WikiLinksRow chip strip is shown below the note content.
 *   Each chip shows the link target title. Tapping a chip resolves and navigates.
 *
 * PREVIEW MODE:
 *   buildWikiAnnotatedString() renders [[...]] as coloured, tappable spans.
 *   The ClickableText wrapper in NotePreviewPanel handles the tap.
 *
 * ─── RESOLUTION LOGIC (in NoteEditorViewModel) ───────────────────────────────
 *
 * 1. Load all non-trashed notes (already decrypted by NoteRepository).
 * 2. Find first note whose title matches linkTitle (case-insensitive, trimmed).
 * 3. If found  → navigate to that note's editor.
 * 4. If absent → create a new note titled linkTitle, navigate to it.
 *
 * WHY CAN'T WE USE SQL LIKE?
 * Note titles are AES-256-GCM ciphertext in the DB.
 * A SQL LIKE query compares against ciphertext — it can never match plaintext.
 * In-memory search over the decrypted domain model list is the only option.
 */

// ─── Regex ────────────────────────────────────────────────────────────────────

/**
 * Matches [[anything]] where "anything" is 1–100 chars, no nested brackets.
 *
 * Group 1 captures the inner title text without the [[ ]] delimiters.
 *
 * Examples:
 *   "See [[Meeting Notes]] for details" → match, group(1) = "Meeting Notes"
 *   "[[Todo]]"                           → match, group(1) = "Todo"
 *   "[[]]"                               → no match (empty title)
 *   "[[a[[b]]"                           → no match (nested bracket)
 */
val WIKI_LINK_REGEX = Regex("""\[\[([^\[\]]{1,100})\]\]""")

// ─── Extraction ───────────────────────────────────────────────────────────────

/**
 * Extract all unique wiki link titles from [text], preserving order of first
 * appearance. Titles are trimmed but NOT lowercased — the original casing is
 * preserved for display (resolution is case-insensitive at lookup time).
 *
 * Returns an empty list if no [[...]] patterns are found.
 *
 * @param text  Raw note content (may contain block markers — those don't
 *              contain [[ ]] syntax, so they're harmless)
 */
fun extractWikiLinks(text: String): List<String> {
    return WIKI_LINK_REGEX
        .findAll(text)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .distinct()                 // deduplicate — same title linked multiple times = one chip
        .toList()
}

// ─── Preview-mode rendering ───────────────────────────────────────────────────

/**
 * Build an [AnnotatedString] that renders [[wiki links]] as styled, tappable
 * spans. Used exclusively in Preview Mode (NotePreviewPanel).
 *
 * HOW TAPPING WORKS:
 * Each wiki link span is annotated with tag = "WIKI_LINK" and item = linkTitle.
 * In the composable, a ClickableText with onClick receives the character offset,
 * calls getStringAnnotations("WIKI_LINK", offset, offset), and navigates.
 *
 * @param text       Raw note content (with [[...]] syntax)
 * @param linkColor  The theme's primary color — used to tint link spans
 */
fun buildWikiAnnotatedString(
    text: String,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {

    val linkStyle = SpanStyle(
        color      = linkColor,
        fontWeight = FontWeight.Medium
    )

    var lastEnd = 0

    WIKI_LINK_REGEX.findAll(text).forEach { match ->
        val matchStart = match.range.first
        val matchEnd   = match.range.last + 1
        val linkTitle  = match.groupValues[1].trim()

        // Append plain text before this match
        if (matchStart > lastEnd) {
            append(text.substring(lastEnd, matchStart))
        }

        // Append the link span — annotated for tap detection
        pushStringAnnotation(tag = "WIKI_LINK", annotation = linkTitle)
        withStyle(linkStyle) {
            // Show just the inner title (without [[ ]]) for cleaner reading
            append(linkTitle)
        }
        pop()

        lastEnd = matchEnd
    }

    // Append any remaining plain text after the last match
    if (lastEnd < text.length) {
        append(text.substring(lastEnd))
    }
}