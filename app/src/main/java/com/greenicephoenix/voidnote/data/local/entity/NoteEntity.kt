package com.greenicephoenix.voidnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.greenicephoenix.voidnote.data.local.converter.StringListConverter
import com.greenicephoenix.voidnote.domain.model.FormatRange

/**
 * NoteEntity — Room database row for the notes table.
 *
 * VERSION 5 CHANGE: Added trashedAt field.
 * VERSION 7 (Sprint 6): Added color column for note color coding.
 *
 * WHY IS color A NULLABLE STRING?
 * We store the NoteColor enum name (e.g. "RED", "BLUE") as plain text.
 * - null = no color assigned (default appearance)
 * - Storing enum names (not ordinals) means we can safely add/reorder variants
 *   without corrupting old data.
 * - NoteColor.fromString() handles unrecognised names gracefully (returns null).
 *
 * DB MIGRATION:
 * This column was added in MIGRATION_6_7 using:
 *   ALTER TABLE notes ADD COLUMN color TEXT
 * SQLite adds NULL for all existing rows — which correctly maps to "no color".
 * No existing data is affected.
 */
@Entity(tableName = "notes")
@TypeConverters(StringListConverter::class)
data class NoteEntity(

    @PrimaryKey
    val id: String,

    val title: String,

    val content: String,

    val createdAt: Long,

    val updatedAt: Long,

    val isPinned: Boolean = false,

    val isArchived: Boolean = false,

    val isTrashed: Boolean = false,

    /**
     * Unix timestamp (millis) when this note was moved to trash.
     * NULL if the note has never been trashed, or was trashed before v5.
     * Set by NoteRepositoryImpl.moveToTrash().
     * Cleared (back to NULL) by NoteRepositoryImpl.restoreFromTrash().
     */
    val trashedAt: Long? = null,

    val tags: List<String> = emptyList(),

    val folderId: String? = null,

    val contentFormats: List<FormatRange>,

    /**
     * Sprint 6: The NoteColor enum name (e.g. "RED") or null for no color.
     * Added via MIGRATION_6_7 — existing rows get NULL automatically.
     */
    val color: String? = null
)