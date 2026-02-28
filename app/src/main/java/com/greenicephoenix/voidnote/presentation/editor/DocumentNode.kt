package com.greenicephoenix.voidnote.presentation.editor

import com.greenicephoenix.voidnote.domain.model.InlineBlockType

/**
 * Represents a single node in a parsed note document.
 *
 * WHAT IS A DocumentNode?
 * When we parse a note's raw content string, we get back a list of nodes.
 * Each node is either a chunk of plain text, or a reference to a block
 * (like a TODO list or an image).
 *
 * WHY A SEALED CLASS?
 * A sealed class is a closed hierarchy — only the subclasses defined here
 * can exist. This means when you write a `when` expression on a DocumentNode,
 * the compiler forces you to handle BOTH Text and Block cases.
 * No runtime surprises, no forgotten cases.
 *
 * CURRENT ARCHITECTURE (Phase 1 — blocks at bottom):
 * Right now, all blocks are appended AFTER the logical text.
 * So a note with one TODO block produces:
 *   [ Text("My grocery list") ]
 *   [ Block("uuid-abc", TODO) ]
 *
 * FUTURE ARCHITECTURE (Phase 2 — inline at cursor):
 * A block can appear between text segments:
 *   [ Text("Before the list\n") ]
 *   [ Block("uuid-abc", TODO) ]
 *   [ Text("\nAfter the list") ]
 *
 * The DocumentNode sealed class supports BOTH — no changes needed later.
 */
sealed class DocumentNode {

    /**
     * A plain text segment.
     *
     * @param text       The display text. Does NOT contain any marker tokens.
     * @param rawStart   Start position of this text in the raw content string (inclusive).
     *                   Used for: FormatRange position mapping in Phase 2.
     * @param rawEnd     End position of this text in the raw content string (exclusive).
     */
    data class Text(
        val text: String,
        val rawStart: Int,
        val rawEnd: Int
    ) : DocumentNode()

    /**
     * A reference to an inline block (TODO, image, audio, etc.)
     *
     * The actual block data is NOT stored here — only the ID and type.
     * The ViewModel holds the full InlineBlock objects in a Map<String, InlineBlock>.
     * The UI looks up `blockId` in that map to get the data for rendering.
     *
     * @param blockId    The UUID embedded in the marker token. Used to look up
     *                   the full InlineBlock from the ViewModel's blocks map.
     * @param blockType  The type (TODO, IMAGE, etc.) — used to choose which
     *                   composable to render.
     * @param rawStart   Start of the marker token in the raw content string.
     * @param rawEnd     End of the marker token in the raw content string (exclusive).
     */
    data class Block(
        val blockId: String,
        val blockType: InlineBlockType,
        val rawStart: Int,
        val rawEnd: Int
    ) : DocumentNode()
}