package com.greenicephoenix.voidnote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for the `inline_blocks` database table.
 *
 * WHAT DOES THIS TABLE STORE?
 * Each row represents one inline block embedded in a note.
 * The note's content string references this row via a marker token:
 *   ⟦block:TODO:abc-123⟧
 * The "abc-123" is this entity's `id`.
 *
 * WHY NOT STORE BLOCK DATA INSIDE THE NOTE?
 * Storing structured data (like checklist items) as JSON inside the
 * notes.content column would mean:
 * - We can't query individual todo items
 * - Updating one item requires re-parsing and re-saving the whole note
 * - Future sync is much harder (can't diff individual items)
 * A separate normalized table solves all of these problems.
 *
 * FOREIGN KEY:
 * noteId references notes.id with CASCADE DELETE.
 * This means: when a note is permanently deleted, ALL its blocks
 * are automatically deleted too. No orphan rows. No manual cleanup.
 *
 * INDEX ON noteId:
 * We frequently query "give me all blocks for note X".
 * An index makes this query fast. Without it, Room would scan
 * the entire table for every lookup.
 *
 * PAYLOAD:
 * Stored as a raw JSON string. The InlineBlockMapper is responsible
 * for serializing/deserializing the typed InlineBlockPayload object.
 * Room doesn't know about our sealed class — it just stores text.
 *
 * TYPE:
 * Stored as a String (the enum's name, e.g. "TODO").
 * We use InlineBlockType.valueOf(type) to convert back to enum.
 */
@Entity(
    tableName = "inline_blocks",

    // Foreign key constraint: auto-delete blocks when parent note is deleted
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],           // notes.id
            childColumns = ["noteId"],        // inline_blocks.noteId
            onDelete = ForeignKey.CASCADE     // Delete blocks when note is deleted
        )
    ],

    // Index on noteId for fast "get all blocks for note X" queries
    indices = [
        Index(value = ["noteId"])
    ]
)
data class InlineBlockEntity(

    /**
     * Primary key. This UUID is embedded in the note's content
     * as part of the marker token: ⟦block:TODO:THIS_ID⟧
     */
    @PrimaryKey
    val id: String,

    /**
     * The note this block belongs to.
     * References notes.id (enforced by foreign key above).
     */
    val noteId: String,

    /**
     * The block type as a string, e.g. "TODO", "IMAGE", "AUDIO".
     * Maps to InlineBlockType enum via InlineBlockType.valueOf(type).
     */
    val type: String,

    /**
     * The block's data serialized as JSON.
     *
     * Examples by type:
     * TODO  → {"items":[{"id":"...","text":"Buy milk","isChecked":false,"sortOrder":0}]}
     * IMAGE → {"filePath":"...","caption":"","width":800,"height":600}
     * AUDIO → {"filePath":"...","durationMs":5000}
     *
     * The InlineBlockMapper converts this JSON to/from InlineBlockPayload.
     */
    val payload: String,

    /**
     * When this block was created (Unix timestamp, milliseconds).
     * Used for ordering and future sync conflict resolution.
     */
    val createdAt: Long
)