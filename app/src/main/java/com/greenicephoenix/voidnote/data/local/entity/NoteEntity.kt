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
 *
 * WHY IS trashedAt NULLABLE (Long?)?
 * Notes that existed before version 5 have no trash timestamp — Room will
 * give them NULL for this column automatically when the DB is migrated
 * (or wiped via fallbackToDestructiveMigration during alpha).
 * A non-nullable field with no default would require a migration DEFAULT value.
 * Nullable is simpler and correct: NULL means "not in trash" or "trashed
 * before we started tracking timestamps".
 *
 * HOW TrashCleanupWorker uses it:
 *   DELETE FROM notes
 *   WHERE isTrashed = 1
 *   AND trashedAt IS NOT NULL
 *   AND trashedAt < (now - 30 days)
 *
 * Notes with trashedAt = NULL are never auto-deleted — they were trashed
 * before this feature shipped and we give the user the benefit of the doubt.
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

    val contentFormats: List<FormatRange>
)