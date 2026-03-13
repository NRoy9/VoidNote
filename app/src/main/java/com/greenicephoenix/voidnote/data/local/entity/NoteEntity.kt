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
 * VERSION 8 (Sprint 11): Added linkedNoteIds column for note linking feature.
 *
 * WHY IS color A NULLABLE STRING?
 * We store the NoteColor enum name (e.g. "RED", "BLUE") as plain text.
 * - null = no color assigned (default appearance)
 * - Storing enum names (not ordinals) means we can safely add/reorder variants
 *   without corrupting old data.
 * - NoteColor.fromString() handles unrecognised names gracefully (returns null).
 *
 * WHY IS linkedNoteIds A LIST (not a join table)?
 * Note links are directional and personal — "this note references that note".
 * A List<String> stored via StringListConverter keeps the schema simple.
 * A separate join table would be over-engineering for this use case.
 * The list is bounded (users won't link hundreds of notes to one note) so
 * column storage is fine.
 *
 * DB MIGRATIONS:
 * color       — added in MIGRATION_6_7: ALTER TABLE notes ADD COLUMN color TEXT
 * linkedNoteIds — added in MIGRATION_7_8: ALTER TABLE notes ADD COLUMN linkedNoteIds TEXT NOT NULL DEFAULT ''
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
    val color: String? = null,

    /**
     * Sprint 11: Comma-separated UUIDs of notes this note is linked to.
     * Stored/read by StringListConverter. Empty string = no links.
     * Added via MIGRATION_7_8 — existing rows get '' (empty string) automatically.
     */
    val linkedNoteIds: List<String> = emptyList()
)