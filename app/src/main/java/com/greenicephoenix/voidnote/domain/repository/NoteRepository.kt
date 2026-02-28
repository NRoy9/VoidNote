package com.greenicephoenix.voidnote.domain.repository

import com.greenicephoenix.voidnote.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * NoteRepository — interface defining all note data operations.
 *
 * Lives in the domain layer — no Android or Room imports here.
 * ViewModels depend on this interface, not the implementation.
 *
 * SPRINT 3 FIX:
 * Added trashNotesByFolder() so folder deletion sends notes to trash
 * (recoverable) instead of permanently deleting them.
 */
interface NoteRepository {

    /** All non-trashed notes as a reactive stream. */
    fun getAllNotes(): Flow<List<Note>>

    /** Notes in a specific folder (non-trashed) as a reactive stream. */
    fun getNotesByFolder(folderId: String): Flow<List<Note>>

    /** Root-level notes (no folder, non-trashed) as a reactive stream. */
    fun getNotesWithoutFolder(): Flow<List<Note>>

    /** One-shot read of a single note by ID. Returns null if not found. */
    suspend fun getNoteById(noteId: String): Note?

    /** All pinned, non-trashed notes as a reactive stream. */
    fun getPinnedNotes(): Flow<List<Note>>

    /** All archived, non-trashed notes as a reactive stream. */
    fun getArchivedNotes(): Flow<List<Note>>

    /** All trashed notes as a reactive stream. */
    fun getTrashedNotes(): Flow<List<Note>>

    /** Search notes by title or content (excludes trash). */
    fun searchNotes(query: String): Flow<List<Note>>

    /** Insert or update a note. */
    suspend fun insertNote(note: Note, folderId: String? = null)

    /** Update an existing note. */
    suspend fun updateNote(note: Note, folderId: String? = null)

    /**
     * Move a single note to trash.
     * folderId is cleared — restoring always goes to the main list.
     */
    suspend fun moveToTrash(noteId: String)

    /**
     * Restore a note from trash back to the main notes list.
     * folderId stays null (was cleared on trash) — note appears in main list.
     */
    suspend fun restoreFromTrash(noteId: String)

    /** Permanently delete a single note (used inside TrashScreen only). */
    suspend fun deleteNotePermanently(noteId: String)

    /** Permanently delete all trashed notes (empty trash). */
    suspend fun emptyTrash()

    /** Toggle the pinned state of a note. */
    suspend fun togglePin(noteId: String)

    /**
     * Toggle the archived state of a note.
     *
     * On ARCHIVE (isArchived false → true): keeps folderId so the note
     * remembers which folder it came from.
     *
     * On UNARCHIVE (isArchived true → false): checks if the folder still
     * exists. If yes → keeps folderId (note returns to folder). If no →
     * clears folderId (note goes to main list). No orphan possible.
     */
    suspend fun toggleArchive(noteId: String)

    /** Total non-trashed note count as a reactive stream. */
    fun getNoteCount(): Flow<Int>

    /** Move a note to a different folder (or null for root level). */
    suspend fun moveNoteToFolder(noteId: String, folderId: String?)

    /**
     * SPRINT 3 FIX — Send all notes in a folder to trash in one operation.
     *
     * Used when a folder is deleted. Notes go to trash (recoverable) rather
     * than being permanently deleted. folderId is cleared on all notes so
     * restoring from trash puts them in the main list — no orphan risk.
     *
     * @param folderId The folder whose notes should all be trashed.
     */
    suspend fun trashNotesByFolder(folderId: String)
}