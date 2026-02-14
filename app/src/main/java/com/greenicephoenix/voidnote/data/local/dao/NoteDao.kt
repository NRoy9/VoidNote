package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.*
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) for Notes
 *
 * Defines all database operations for notes
 * Room generates the implementation automatically
 *
 * Flow = Reactive data stream (updates automatically when database changes)
 */
@Dao
interface NoteDao {

    /**
     * Get all notes (excluding trashed)
     * Ordered by: Pinned first, then by updated date
     *
     * Flow automatically updates UI when data changes
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isTrashed = 0 
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getAllNotes(): Flow<List<NoteEntity>>

    /**
     * Get notes in a specific folder
     */
    @Query("""
        SELECT * FROM notes 
        WHERE folderId = :folderId AND isTrashed = 0 
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getNotesByFolder(folderId: String): Flow<List<NoteEntity>>

    /**
     * Get notes without any folder (root level)
     */
    @Query("""
        SELECT * FROM notes 
        WHERE folderId IS NULL AND isTrashed = 0 
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getNotesWithoutFolder(): Flow<List<NoteEntity>>

    /**
     * Get single note by ID
     */
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?

    /**
     * Get pinned notes
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isPinned = 1 AND isTrashed = 0 
        ORDER BY updatedAt DESC
    """)
    fun getPinnedNotes(): Flow<List<NoteEntity>>

    /**
     * Get archived notes
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isArchived = 1 AND isTrashed = 0 
        ORDER BY updatedAt DESC
    """)
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    /**
     * Get trashed notes
     */
    @Query("""
        SELECT * FROM notes 
        WHERE isTrashed = 1 
        ORDER BY updatedAt DESC
    """)
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    /**
     * Search notes by title or content
     */
    @Query("""
        SELECT * FROM notes 
        WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        AND isTrashed = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>


    /**
     * Insert or update a note
     * OnConflictStrategy.REPLACE = If note exists, replace it
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    /**
     * Insert multiple notes at once
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    /**
     * Update existing note
     */
    @Update
    suspend fun updateNote(note: NoteEntity)

    /**
     * Delete note permanently
     */
    @Delete
    suspend fun deleteNote(note: NoteEntity)

    /**
     * Delete all trashed notes (empty trash)
     */
    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun deleteAllTrashedNotes()

    /**
     * Get note count
     */
    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0")
    fun getNoteCount(): Flow<Int>
}