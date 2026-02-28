package com.greenicephoenix.voidnote.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a single item inside a TODO inline block.
 *
 * WHY A SEPARATE CLASS?
 * A TODO block contains MULTIPLE items (a checklist).
 * Each item has its own state (checked/unchecked) and text.
 * We need to model this as a list of structured items,
 * not a blob of text.
 *
 * WHERE IS THIS STORED?
 * TodoItem objects are serialized to JSON and stored in the
 * InlineBlockEntity's `payload` field. They are never stored
 * in the notes table. The notes table only stores the marker token.
 *
 * Example payload JSON:
 * {
 *   "items": [
 *     { "id": "uuid-1", "text": "Buy milk", "isChecked": false, "sortOrder": 0 },
 *     { "id": "uuid-2", "text": "Buy eggs", "isChecked": true,  "sortOrder": 1 }
 *   ]
 * }
 *
 * WHY sortOrder?
 * When items are reordered (future drag-to-reorder feature), we need a stable
 * way to track position. Using list index alone is fragile. sortOrder lets us
 * re-sort items correctly even if the list arrives in the wrong order.
 *
 * @Serializable — Required so kotlinx.serialization can convert this
 * to/from JSON automatically. Same pattern as FormatRange in this project.
 */
@Serializable
data class TodoItem(

    /**
     * Unique ID for this specific todo item.
     * Generated once at creation time, never changes.
     * Allows stable identity even when text changes.
     */
    val id: String,

    /**
     * The text content of this checklist item.
     * Editable by the user directly in the note editor.
     */
    val text: String,

    /**
     * Whether this item has been checked off.
     * When true, the UI renders strikethrough text.
     */
    val isChecked: Boolean = false,

    /**
     * Position of this item within the block's list.
     * 0-indexed. Lower = higher in the list.
     */
    val sortOrder: Int = 0
)