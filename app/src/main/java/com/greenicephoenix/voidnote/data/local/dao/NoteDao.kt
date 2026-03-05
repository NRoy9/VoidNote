package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.*
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * NoteDao — Room Data Access Object for all note database operations.
 *
 * VERSION 5 ADDITIONS:
 *
 * 1. getAllNotesOnce() — one-shot (non-Flow) snapshot for export.
 *    ImportExportManager needs a single snapshot of all notes, not a live
 *    stream. Using a suspend fun instead of Flow avoids keeping a database
 *    cursor open for the duration of the ZIP write operation.
 *
 * 2. getExpiredTrashedNoteIds() — finds note IDs to auto-delete.
 *    Called by TrashCleanupWorker BEFORE deleting, so we know which
 *    inline_blocks rows to remove first (no orphan block rows left behind).
 *
 * 3. deleteExpiredTrashedNotes() — the actual auto-delete SQL.
 *    Deletes notes in trash where trashedAt is older than 30 days.
 *    Notes with trashedAt = NULL are never touched (pre-v5 trashed notes).
 *
 * SPRINT 3 FIXES (unchanged):
 * - getNotesByFolder() has AND isArchived = 0 guard
 * - trashNotesByFolder() bulk-trash SQL for folder delete
 */
@Dao
interface NoteDao {

    /**
     * All non-trashed notes as a live reactive stream, pinned first then by recency.
     * Used by NotesListViewModel to keep the UI in sync automatically.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isTrashed = 0 
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getAllNotes(): Flow<List<NoteEntity>>

    /**
     * One-shot snapshot of ALL notes (including trashed/archived).
     * Used by ImportExportManager for export — needs a complete picture once,
     * not a live stream.
     *
     * DIFFERENCE FROM getAllNotes():
     * getAllNotes() returns Flow<List<NoteEntity>> — stays open, re-emits on changes.
     * getAllNotesOnce() is suspend — runs once, returns, done. No isTrashed filter
     * because export should include everything (so the user can restore deleted notes).
     */
    // For plain text export — excludes trashed notes
    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllNotesOnce(): List<NoteEntity>

    // For secure backup — includes trashed notes (user may want to restore them)
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllNotesWithTrash(): List<NoteEntity>

    /**
     * Notes inside a specific folder (non-trashed, non-archived).
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

    @Query("""
        SELECT * FROM notes 
        WHERE isPinned = 1 AND isTrashed = 0 
        ORDER BY updatedAt DESC
    """)
    fun getPinnedNotes(): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE isArchived = 1 AND isTrashed = 0 
        ORDER BY updatedAt DESC
    """)
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE isTrashed = 1 
        ORDER BY updatedAt DESC
    """)
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        AND isTrashed = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNote(note: NoteEntity)

    /**
     * Bulk insert/replace notes in a single atomic database transaction.
     *
     * WHY @Transaction HERE:
     * During Change Vault Password, all notes must be re-encrypted and written
     * in one atomic operation. If the process is killed halfway through:
     *   WITHOUT @Transaction: some notes encrypted with new key, some with old key → unreadable
     *   WITH @Transaction:    Room rolls back every write → old-key ciphertext intact → app works
     *
     * OnConflictStrategy.REPLACE means if a note ID already exists (it always will
     * during password change), the existing row is replaced with the new one.
     * This is safe because we are replacing with newly re-encrypted content.
     */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun deleteAllTrashedNotes()

    /**
     * Bulk-trash all eligible notes in a folder when that folder is deleted.
     * (Sprint 3 — unchanged)
     */
    @Query("""
        UPDATE notes 
        SET isTrashed = 1, folderId = NULL, updatedAt = :timestamp
        WHERE folderId = :folderId AND isTrashed = 0
    """)
    suspend fun trashNotesByFolder(folderId: String, timestamp: Long)

    /**
     * Total count of non-trashed notes (reactive, for the Settings storage card).
     */
    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0")
    fun getNoteCount(): Flow<Int>

    // ─── Trash auto-delete (v5) ───────────────────────────────────────────────

    /**
     * Get IDs of notes that have been in the trash for more than 30 days.
     *
     * WHY FETCH IDs BEFORE DELETING?
     * TrashCleanupWorker must delete inline_blocks for these notes BEFORE
     * deleting the notes themselves. If we deleted the notes first, we'd
     * lose the noteId reference needed to find their blocks.
     * So the flow is: getExpiredIds → delete their blocks → delete the notes.
     *
     * @param cutoffTime  Notes with trashedAt < cutoffTime will be returned.
     *                    Caller passes: System.currentTimeMillis() - 30 days in ms.
     */
    @Query("""
        SELECT id FROM notes 
        WHERE isTrashed = 1 
        AND trashedAt IS NOT NULL 
        AND trashedAt < :cutoffTime
    """)
    suspend fun getExpiredTrashedNoteIds(cutoffTime: Long): List<String>

    /**
     * Delete notes that have been in the trash for more than 30 days.
     * Called by TrashCleanupWorker AFTER deleting their inline blocks.
     *
     * Notes with trashedAt = NULL are never deleted by this query —
     * they were trashed before v5 and we protect them from silent loss.
     *
     * @param cutoffTime  Notes with trashedAt < cutoffTime will be deleted.
     */
    @Query("""
        DELETE FROM notes 
        WHERE isTrashed = 1 
        AND trashedAt IS NOT NULL 
        AND trashedAt < :cutoffTime
    """)
    suspend fun deleteExpiredTrashedNotes(cutoffTime: Long)
}