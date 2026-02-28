package com.greenicephoenix.voidnote.domain.model

/**
 * Enumerates every type of inline block that can be embedded in a note.
 *
 * WHY AN ENUM?
 * We need a single source of truth for block type names.
 * The string stored in the database (e.g. "TODO") must map to exactly
 * one type. An enum makes that mapping compile-time safe — if you
 * rename a type, the compiler will catch every broken reference.
 *
 * CURRENT TYPES:
 * - TODO: An interactive checklist with multiple items.
 *         This is a "structured" block — its content is editable inline.
 *
 * FUTURE TYPES (do not implement yet, just reserved):
 * - IMAGE: A full-width embedded image.
 * - AUDIO: A voice recording block.
 * - DRAWING: A freehand canvas block.
 *
 * HOW IT'S STORED IN DATABASE:
 * We store the enum's name() as a plain String, e.g. "TODO".
 * This avoids fragile integer ordinal mapping.
 *
 * HOW IT'S USED IN MARKER TOKENS:
 * The content string uses: ⟦block:TODO:uuid⟧
 * The "TODO" part is always InlineBlockType.TODO.name
 */
enum class InlineBlockType {

    TODO,

    IMAGE,   // Reserved — not implemented yet

    AUDIO,   // Reserved — not implemented yet

    DRAWING  // Reserved — not implemented yet
}