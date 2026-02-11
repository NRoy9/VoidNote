package com.greenicephoenix.voidnote.domain.repository

import com.greenicephoenix.voidnote.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Repository Interface for Notes
 *
 * This defines WHAT operations are available, not HOW they work
 * The actual implementation is in the data layer
 *
 * Benefits:
 * - ViewModels depend on interface, not implementation
 * - Easy to swap implementations (e.g., mock for testing)
 * - Clean separation of concerns
 */
interface NoteRepository {

    /**
     * Get all notes (excluding trashed)
     * Returns Flow for reactive updates
     */
    fun getAllNotes(): Flow<List<Note>>

    /**
     * Get notes in a specific folder
     */
    fun getNotesByFolder(folderId: String): Flow<List<Note>>

    /**
     * Get notes without any folder (root level)
     */
    fun getNotesWithoutFolder(): Flow<List<Note>>

    /**
     * Get single note by ID
     * Returns null if not found
     */
    suspend fun getNoteById(noteId: String): Note?

    /**
     * Get pinned notes only
     */
    fun getPinnedNotes(): Flow<List<Note>>

    /**
     * Get archived notes
     */
    fun getArchivedNotes(): Flow<List<Note>>

    /**
     * Get trashed notes
     */
    fun getTrashedNotes(): Flow<List<Note>>

    /**
     * Search notes by query
     */
    fun searchNotes(query: String): Flow<List<Note>>

    /**
     * Insert or update a note
     */
    suspend fun insertNote(note: Note, folderId: String? = null)

    /**
     * Update existing note
     */
    suspend fun updateNote(note: Note, folderId: String? = null)

    /**
     * Move note to trash
     */
    suspend fun moveToTrash(noteId: String)

    /**
     * Restore note from trash
     */
    suspend fun restoreFromTrash(noteId: String)

    /**
     * Delete note permanently
     */
    suspend fun deleteNotePermanently(noteId: String)

    /**
     * Empty trash (delete all trashed notes)
     */
    suspend fun emptyTrash()

    /**
     * Pin/unpin note
     */
    suspend fun togglePin(noteId: String)

    /**
     * Archive/unarchive note
     */
    suspend fun toggleArchive(noteId: String)

    /**
     * Get total note count
     */
    fun getNoteCount(): Flow<Int>

    /**
     * Move note to a folder
     */
    suspend fun moveNoteToFolder(noteId: String, folderId: String?)
}