package com.greenicephoenix.voidnote.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the typed payload data stored inside an InlineBlock.
 *
 * WHY A SEALED CLASS?
 * Different block types store completely different data:
 * - A TODO block stores a list of TodoItem objects.
 * - An IMAGE block (future) stores a file path + dimensions.
 * - An AUDIO block (future) stores a file path + duration.
 *
 * A sealed class lets us model this as a closed type hierarchy.
 * When you write a `when` expression on InlineBlockPayload, the
 * compiler forces you to handle every subtype — no surprises.
 *
 * RELATIONSHIP TO DATABASE:
 * InlineBlockEntity stores the payload as raw JSON String.
 * InlineBlockMapper converts between this sealed class and that JSON.
 *
 * Example:
 *   InlineBlockPayload.Todo(items = listOf(...))
 *       → mapper serializes → JSON String → stored in DB
 *   JSON String from DB
 *       → mapper deserializes → InlineBlockPayload.Todo(...)
 *
 * WHY @Serializable on each subclass?
 * Each subclass needs its own serializer so kotlinx.serialization
 * knows how to convert it to/from JSON independently.
 */
sealed class InlineBlockPayload {

    /**
     * Payload for a TODO / Checklist block.
     *
     * Contains an ordered list of checklist items.
     * New items are appended to the end.
     * Items are displayed in sortOrder order.
     *
     * @param items All checklist items in this block.
     */
    @Serializable
    data class Todo(
        val items: List<TodoItem>
    ) : InlineBlockPayload()

    // ─── Future block payloads (reserved, not implemented yet) ───

    /**
     * Payload for an IMAGE block.
     * Will store: file path, optional caption, dimensions.
     */
    @Serializable
    data class Image(
        val filePath: String,
        val caption: String = "",
        val width: Int = 0,
        val height: Int = 0
    ) : InlineBlockPayload()

    /**
     * Payload for an AUDIO / voice note block.
     * Will store: file path, duration in milliseconds.
     */
    @Serializable
    data class Audio(
        val filePath: String,
        val durationMs: Long = 0L
    ) : InlineBlockPayload()
}