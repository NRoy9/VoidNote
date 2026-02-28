package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.*
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * NoteDao — Room Data Access Object for all note database operations.
 *
 * SPRINT 3 FIX:
 * Added trashNotesByFolder() — a single SQL UPDATE that moves every note
 * in a folder to trash AND clears their folderId in one database transaction.
 *
 * WHY ONE SQL QUERY INSTEAD OF A KOTLIN LOOP?
 * Previously we looped over each note and called individual update operations.
 * A folder with 50 notes = 50 round trips to SQLite. One UPDATE query handles
 * all 50 rows atomically — faster, and if the app crashes mid-operation,
 * SQLite rolls back the entire update rather than leaving half the notes trashed.
 */
@Dao
interface NoteDao {

    /**
     * Get all non-trashed notes.
     * Pinned notes appear first, then sorted by most recently updated.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isTrashed = 0 
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getAllNotes(): Flow<List<NoteEntity>>

    /**
     * Get all non-trashed notes inside a specific folder.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE folderId = :folderId AND isTrashed = 0 
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getNotesByFolder(folderId: String): Flow<List<NoteEntity>>

    /**
     * Get non-trashed notes with no folder (root level / main list).
     */
    @Query("""
        SELECT * FROM notes 
        WHERE folderId IS NULL AND isTrashed = 0 
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getNotesWithoutFolder(): Flow<List<NoteEntity>>

    /**
     * Get a single note by ID (includes trashed/archived — unfiltered).
     * Returns null if not found.
     */
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?

    /**
     * Get all pinned, non-trashed notes.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isPinned = 1 AND isTrashed = 0 
        ORDER BY updatedAt DESC
    """)
    fun getPinnedNotes(): Flow<List<NoteEntity>>

    /**
     * Get all archived, non-trashed notes.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isArchived = 1 AND isTrashed = 0 
        ORDER BY updatedAt DESC
    """)
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    /**
     * Get all trashed notes.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isTrashed = 1 
        ORDER BY updatedAt DESC
    """)
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    /**
     * Full-text search across title and content, excluding trash.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        AND isTrashed = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    /**
     * Insert or replace a note.
     * REPLACE strategy: if a note with the same ID exists, it is overwritten.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    /**
     * Insert multiple notes in one call.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    /**
     * Update an existing note row.
     */
    @Update
    suspend fun updateNote(note: NoteEntity)

    /**
     * Delete a note row permanently (no recovery possible).
     * Only called from emptyTrash() or permanent single-note deletion.
     */
    @Delete
    suspend fun deleteNote(note: NoteEntity)

    /**
     * Permanently delete all trashed notes (empty trash).
     */
    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun deleteAllTrashedNotes()

    /**
     * SPRINT 3 FIX — Bulk-trash all notes belonging to a folder.
     *
     * Sets isTrashed = 1, folderId = NULL, updatedAt = current time
     * for every note in the given folder IN A SINGLE SQL TRANSACTION.
     *
     * WHY folderId = NULL on trash?
     * Trash is a global bin with no folder concept. Clearing folderId means:
     * - No orphan risk: restoring later always puts the note in the main list
     * - No stale references: the note never points to a folder that may be deleted
     *
     * WHY only isTrashed = 0?
     * We only trash notes that aren't already trashed. Notes that are already
     * in trash (somehow got there before the folder delete) are untouched.
     * Archived notes (isArchived = 1) ARE included — archiving doesn't protect
     * a note from the folder being deleted. They go to trash like the rest.
     *
     * @param folderId  ID of the folder whose notes should be trashed
     * @param timestamp Current time in milliseconds (passed in, not generated
     *                  inside SQL, so all rows get the exact same timestamp)
     */
    @Query("""
        UPDATE notes 
        SET isTrashed = 1, folderId = NULL, isArchived = 0, updatedAt = :timestamp
        WHERE folderId = :folderId AND isTrashed = 0
    """)
    suspend fun trashNotesByFolder(folderId: String, timestamp: Long)

    /**
     * Total count of non-trashed notes as a reactive stream.
     */
    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0")
    fun getNoteCount(): Flow<Int>
}