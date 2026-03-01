package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.*
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * NoteDao — Room Data Access Object for all note database operations.
 *
 * SPRINT 3 FIXES IN THIS FILE:
 *
 * 1. getNotesByFolder() — added AND isArchived = 0
 *    Archived notes have their folderId cleared immediately on archive
 *    (see NoteRepositoryImpl.toggleArchive), so they naturally won't match
 *    this query anyway. But the extra filter is a defensive safety net —
 *    if for any reason folderId is not cleared, archived notes still won't
 *    leak into the folder view. Belt and braces.
 *
 * 2. trashNotesByFolder() — new bulk SQL UPDATE
 *    Called when a folder is deleted. Trashes all non-archived, non-trashed
 *    notes in that folder in one atomic SQL statement. Archived notes are
 *    excluded because by this point they have folderId = NULL and won't
 *    match the WHERE clause.
 */
@Dao
interface NoteDao {

    /**
     * All non-trashed notes, pinned first then by recency.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isTrashed = 0 
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getAllNotes(): Flow<List<NoteEntity>>

    /**
     * Notes inside a specific folder.
     *
     * SPRINT 3 FIX: Added AND isArchived = 0
     *
     * WHY: Archived notes should not appear in folder view. The user archived
     * the note to remove it from their active workspace — that includes the
     * folder. The note belongs in Archive, not in the folder list.
     *
     * With the new archive behaviour (folderId cleared on archive), an archived
     * note won't have this folderId anyway. But this filter ensures correctness
     * even if something unexpected keeps folderId set.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE folderId = :folderId AND isTrashed = 0 AND isArchived = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getNotesByFolder(folderId: String): Flow<List<NoteEntity>>

    /**
     * Root-level notes — no folder, not trashed, not archived.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE folderId IS NULL AND isTrashed = 0 AND isArchived = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getNotesWithoutFolder(): Flow<List<NoteEntity>>

    /**
     * One-shot read of a single note by ID (unfiltered — includes trashed/archived).
     */
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?

    /**
     * Pinned, non-trashed notes.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isPinned = 1 AND isTrashed = 0 
        ORDER BY updatedAt DESC
    """)
    fun getPinnedNotes(): Flow<List<NoteEntity>>

    /**
     * Archived, non-trashed notes.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isArchived = 1 AND isTrashed = 0 
        ORDER BY updatedAt DESC
    """)
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    /**
     * All trashed notes.
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun deleteAllTrashedNotes()

    /**
     * SPRINT 3 — Bulk-trash all eligible notes belonging to a folder.
     *
     * Called when a folder is deleted. In one atomic SQL transaction:
     *   - Sets isTrashed = 1
     *   - Clears folderId = NULL  (so restore from trash → main list, no orphan)
     *   - Sets updatedAt = timestamp
     *
     * WHERE clause: folderId = :folderId AND isTrashed = 0
     *
     * Archived notes are NOT affected by this query. Because folderId is
     * cleared when a note is archived (see NoteRepositoryImpl.toggleArchive),
     * archived notes have folderId = NULL and therefore won't match
     * folderId = :folderId. They stay safely in Archive, untouched by the
     * folder deletion. This is intentional — the user archived those notes;
     * deleting the folder should not change their status.
     *
     * @param folderId  The folder being deleted
     * @param timestamp Current time in ms — passed in so all rows get the
     *                  exact same timestamp rather than each row computing NOW()
     */
    @Query("""
        UPDATE notes 
        SET isTrashed = 1, folderId = NULL, updatedAt = :timestamp
        WHERE folderId = :folderId AND isTrashed = 0
    """)
    suspend fun trashNotesByFolder(folderId: String, timestamp: Long)

    /**
     * Total count of non-trashed notes.
     */
    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0")
    fun getNoteCount(): Flow<Int>
}