package com.greenicephoenix.voidnote.domain.model

/**
 * Domain model representing an inline block embedded in a note.
 *
 * WHAT IS AN INLINE BLOCK?
 * When a user inserts a checklist (or image, audio, etc.) into a note,
 * we create an InlineBlock. The note's content string gets a marker token:
 *
 *   "Some text before\n⟦block:TODO:abc-123⟧\nSome text after"
 *
 * The UUID "abc-123" links back to this InlineBlock.
 * The actual checklist items live in this object's `payload` field.
 *
 * THIS IS THE DOMAIN LAYER MODEL.
 * It is clean — no Room annotations, no JSON strings, no Android dependencies.
 * The data layer (InlineBlockEntity + InlineBlockMapper) handles
 * the translation between this clean model and the database.
 *
 * ARCHITECTURE POSITION:
 * Domain Model ← InlineBlockMapper → InlineBlockEntity (DB)
 *
 * @param id        UUID that matches the marker in the note content.
 *                  Format: standard UUID string, e.g. "abc-123-def-456"
 * @param noteId    The note this block belongs to. Foreign key (logical).
 * @param type      What kind of block this is (TODO, IMAGE, etc.)
 * @param payload   The actual data for this block, already deserialized.
 * @param createdAt Unix timestamp in milliseconds when this block was created.
 */
data class InlineBlock(
    val id: String,
    val noteId: String,
    val type: InlineBlockType,
    val payload: InlineBlockPayload,
    val createdAt: Long
)