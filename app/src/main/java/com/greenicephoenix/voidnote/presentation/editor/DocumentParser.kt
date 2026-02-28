package com.greenicephoenix.voidnote.presentation.editor

import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockType

/**
 * DocumentParser — Converts between logical content and raw stored content.
 *
 * ─── THE TWO CONTENT FORMATS ──────────────────────────────────────────────
 *
 * LOGICAL CONTENT (what the user sees and types):
 *   "My grocery list"
 *
 * RAW CONTENT (what is stored in the database):
 *   "My grocery list\n⟦block:TODO:abc-123⟧\n⟦block:TODO:def-456⟧"
 *
 * The app ALWAYS works with logical content internally.
 * The raw content is only built when saving to the database,
 * and only parsed when loading from the database.
 *
 * WHY THIS SEPARATION?
 * FormatRange objects index character positions in the logical content.
 * If we mixed markers into the logical content, every FormatRange position
 * after a marker would be wrong. By keeping them separate, FormatRanges
 * are always correct and never need adjustment.
 *
 * ─── MARKER FORMAT ────────────────────────────────────────────────────────
 *
 * Every block is represented in raw content by a marker token:
 *   ⟦block:TYPE:UUID⟧
 *
 * Examples:
 *   ⟦block:TODO:abc-123-def-456⟧
 *   ⟦block:IMAGE:abc-123-def-456⟧
 *
 * The ⟦ and ⟧ characters (U+27E6, U+27E7) are chosen because they are
 * extremely unlikely to appear in normal user text. This prevents false
 * positives when parsing.
 *
 * ─── CURRENT STORAGE LAYOUT (Phase 1 — blocks at bottom) ─────────────────
 *
 * Raw content structure:
 *   [logical text][marker1][marker2]...
 *
 * All markers come AFTER the text. This means:
 * - FormatRanges never overlap with marker positions
 * - Simple to implement and reason about
 * - No change to existing text editing logic
 *
 * Phase 2 (future) will support markers inline within the text.
 * DocumentParser already handles this via the parse() function.
 */
object DocumentParser {

    // ─── MARKER CONSTANTS ─────────────────────────────────────────────────

    /** Start of a marker token */
    private const val MARKER_PREFIX = "⟦block:"

    /** End of a marker token */
    private const val MARKER_SUFFIX = "⟧"

    /**
     * Regex to find and parse marker tokens in raw content.
     *
     * Captures:
     *   Group 1: block type string (e.g. "TODO")
     *   Group 2: block UUID (e.g. "abc-123-def-456")
     *
     * Pattern: ⟦block:TYPE:UUID⟧
     * Where TYPE = uppercase letters, UUID = hex chars and dashes
     */
    private val MARKER_REGEX = Regex("""⟦block:([A-Z]+):([0-9a-f\-]+)⟧""")

    // ─── PUBLIC API ───────────────────────────────────────────────────────

    /**
     * Create a marker token string for a block.
     *
     * Called when inserting a new block — the returned string
     * is appended to the raw content.
     *
     * Example: createMarker(InlineBlockType.TODO, "abc-123") → "⟦block:TODO:abc-123⟧"
     */
    fun createMarker(type: InlineBlockType, blockId: String): String {
        return "$MARKER_PREFIX${type.name}:$blockId$MARKER_SUFFIX"
    }

    /**
     * Extract the logical content (user-visible text) from raw content.
     *
     * Strips all marker tokens and surrounding whitespace from the end.
     *
     * Examples:
     *   "Hello world"                              → "Hello world"
     *   "Hello world\n⟦block:TODO:abc⟧"           → "Hello world"
     *   "Hello\n⟦block:TODO:a⟧\n⟦block:TODO:b⟧"  → "Hello"
     *
     * @param rawContent The raw string as stored in the database.
     * @return The logical content string with no markers.
     */
    fun extractLogicalContent(rawContent: String): String {
        val firstMarkerIdx = rawContent.indexOf(MARKER_PREFIX)
        return if (firstMarkerIdx == -1) {
            // No markers — the whole string is logical content
            rawContent
        } else {
            // Everything before the first marker (trimming trailing whitespace/newlines)
            rawContent.substring(0, firstMarkerIdx).trimEnd()
        }
    }

    /**
     * Build the raw content string for database storage.
     *
     * Combines logical content + marker tokens for all blocks.
     * Blocks are sorted by creation time (oldest first) to maintain
     * stable ordering even if blocks are added at different times.
     *
     * Examples:
     *   ("Hello", emptyList)                    → "Hello"
     *   ("Hello", [TODO block abc])             → "Hello\n⟦block:TODO:abc⟧"
     *   ("Hello", [TODO block a, TODO block b]) → "Hello\n⟦block:TODO:a⟧\n⟦block:TODO:b⟧"
     *
     * @param logicalContent The user-visible text (no markers).
     * @param blocks         All InlineBlock objects belonging to this note.
     * @return Raw content string ready for database storage.
     */
    fun buildRawContent(logicalContent: String, blocks: List<InlineBlock>): String {
        if (blocks.isEmpty()) return logicalContent

        // Sort by creation time — oldest first, so order is stable
        val sortedBlocks = blocks.sortedBy { it.createdAt }

        // Append each block's marker, each on its own line
        val markers = sortedBlocks.joinToString("") { block ->
            "\n" + createMarker(block.type, block.id)
        }

        return logicalContent + markers
    }

    /**
     * Parse raw content into a list of DocumentNode objects.
     *
     * This is used for FUTURE inline rendering (Phase 2), where blocks
     * can be positioned anywhere within the text. Currently, all blocks
     * appear after all text, so the result is always:
     *   [Text node, Block node, Block node, ...]
     *
     * @param rawContent The raw string as stored in the database.
     * @return An ordered list of Text and Block nodes.
     */
    fun parse(rawContent: String): List<DocumentNode> {
        val nodes = mutableListOf<DocumentNode>()
        var lastEnd = 0

        // Find all marker matches in order
        for (match in MARKER_REGEX.findAll(rawContent)) {
            // Text before this marker (if any)
            if (match.range.first > lastEnd) {
                val text = rawContent.substring(lastEnd, match.range.first)
                nodes.add(
                    DocumentNode.Text(
                        text = text,
                        rawStart = lastEnd,
                        rawEnd = match.range.first
                    )
                )
            }

            // The block node
            val typeString = match.groupValues[1]  // e.g. "TODO"
            val blockId = match.groupValues[2]      // e.g. "abc-123"

            val blockType = try {
                InlineBlockType.valueOf(typeString)
            } catch (e: IllegalArgumentException) {
                // Unknown block type — skip this marker
                lastEnd = match.range.last + 1
                continue
            }

            nodes.add(
                DocumentNode.Block(
                    blockId = blockId,
                    blockType = blockType,
                    rawStart = match.range.first,
                    rawEnd = match.range.last + 1
                )
            )

            lastEnd = match.range.last + 1
        }

        // Any remaining text after the last marker
        if (lastEnd < rawContent.length) {
            val text = rawContent.substring(lastEnd)
            nodes.add(
                DocumentNode.Text(
                    text = text,
                    rawStart = lastEnd,
                    rawEnd = rawContent.length
                )
            )
        }

        // If no nodes were created (empty content), return a single empty text node
        if (nodes.isEmpty()) {
            nodes.add(DocumentNode.Text(text = "", rawStart = 0, rawEnd = 0))
        }

        return nodes
    }
}