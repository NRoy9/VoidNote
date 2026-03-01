package com.greenicephoenix.voidnote.data.repository

import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.mapper.toDomainModel
import com.greenicephoenix.voidnote.data.mapper.toDomainModels
import com.greenicephoenix.voidnote.data.mapper.toEntity
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * NoteRepositoryImpl — Room-backed implementation of NoteRepository.
 *
 * SPRINT 3 FIXES IN THIS FILE:
 *
 * 1. FolderDao injected (previously only NoteDao was injected).
 *    No longer needed for archive logic (see fix 3 below), but kept for
 *    future use and consistency with the data layer pattern.
 *    Both DAOs are provided by DatabaseModule, Hilt injects both automatically.
 *
 * 2. moveToTrash() clears folderId.
 *    When a note is trashed it leaves the folder immediately. Trash is a
 *    global bin with no folder concept. Restoring from trash always → main list.
 *
 * 3. toggleArchive() clears folderId on ARCHIVE (simplified).
 *    The previous version kept folderId on archive and then checked folder
 *    existence on unarchive. This created two problems:
 *      - Archived notes still appeared in folder view (folderId still set)
 *      - If the folder was deleted, trashNotesByFolder caught the archived
 *        note (folderId still matched) and trashed it — user lost an archived note
 *    New behaviour:
 *      ARCHIVE  → clear folderId immediately. Note leaves the folder.
 *                 It belongs in Archive now, not in any folder.
 *      UNARCHIVE → folderId is already null. Note goes to main list.
 *                  No folder-existence check needed. Simple and safe.
 *
 * 4. trashNotesByFolder() — new.
 *    Bulk SQL UPDATE that trashes all non-archived, non-trashed notes in a
 *    folder. Archived notes are naturally excluded because their folderId
 *    was cleared in step 3 above — they don't match the WHERE clause.
 */
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao
) : NoteRepository {

    // ── Read ──────────────────────────────────────────────────────────────

    override fun getAllNotes(): Flow<List<Note>> =
        noteDao.getAllNotes().map { it.toDomainModels() }

    override fun getNotesByFolder(folderId: String): Flow<List<Note>> =
        noteDao.getNotesByFolder(folderId).map { it.toDomainModels() }

    override fun getNotesWithoutFolder(): Flow<List<Note>> =
        noteDao.getNotesWithoutFolder().map { it.toDomainModels() }

    override suspend fun getNoteById(noteId: String): Note? =
        noteDao.getNoteById(noteId)?.toDomainModel()

    override fun getPinnedNotes(): Flow<List<Note>> =
        noteDao.getPinnedNotes().map { it.toDomainModels() }

    override fun getArchivedNotes(): Flow<List<Note>> =
        noteDao.getArchivedNotes().map { it.toDomainModels() }

    override fun getTrashedNotes(): Flow<List<Note>> =
        noteDao.getTrashedNotes().map { it.toDomainModels() }

    override fun searchNotes(query: String): Flow<List<Note>> =
        noteDao.searchNotes(query).map { it.toDomainModels() }

    override fun getNoteCount(): Flow<Int> =
        noteDao.getNoteCount()

    // ── Write ─────────────────────────────────────────────────────────────

    override suspend fun insertNote(note: Note, folderId: String?) {
        noteDao.insertNote(note.toEntity(folderId))
    }

    override suspend fun updateNote(note: Note, folderId: String?) {
        noteDao.updateNote(note.toEntity(folderId))
    }

    override suspend fun moveNoteToFolder(noteId: String, folderId: String?) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(folderId = folderId, updatedAt = System.currentTimeMillis())
        )
    }

    // ── Trash ─────────────────────────────────────────────────────────────

    /**
     * Move a single note to trash.
     *
     * SPRINT 3 FIX: folderId cleared on trash.
     *
     * Trash has no folder concept. Clearing folderId here means:
     *   - The note immediately disappears from the folder view
     *   - Restoring from trash always puts the note in the main list
     *   - No orphan possible regardless of what happens to the folder later
     *
     * isArchived is also set to false — a note can't be both trashed
     * and archived at the same time.
     */
    override suspend fun moveToTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(
                isTrashed = true,
                folderId = null,
                isArchived = false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Restore a note from trash to the main list.
     *
     * folderId was cleared when the note was trashed, so it is null here.
     * The note always returns to the main list. Simple, no edge cases.
     */
    override suspend fun restoreFromTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(
                isTrashed = false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteNotePermanently(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.deleteNote(note)
    }

    override suspend fun emptyTrash() {
        noteDao.deleteAllTrashedNotes()
    }

    /**
     * SPRINT 3 — Bulk-trash all active notes in a folder.
     *
     * Delegates to a single SQL UPDATE in NoteDao.
     * Archived notes are not affected — their folderId was already cleared
     * when they were archived, so they don't match the SQL WHERE clause.
     */
    override suspend fun trashNotesByFolder(folderId: String) {
        noteDao.trashNotesByFolder(
            folderId = folderId,
            timestamp = System.currentTimeMillis()
        )
    }

    // ── Pin / Archive ─────────────────────────────────────────────────────

    override suspend fun togglePin(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis())
        )
    }

    /**
     * Toggle the archived state of a note.
     *
     * SPRINT 3 FIX — simplified. folderId is cleared on ARCHIVE.
     *
     * ARCHIVING (isArchived false → true):
     *   - isArchived = true
     *   - folderId = null  ← KEY CHANGE
     *   - isTrashed = false (safety, shouldn't be trashed when archiving)
     *
     *   WHY CLEAR folderId ON ARCHIVE?
     *   Three problems are solved at once:
     *   (a) The note immediately disappears from the folder view and count.
     *       Previously it stayed visible in the folder even after archiving.
     *   (b) If the folder is later deleted, trashNotesByFolder() won't touch
     *       this archived note — its folderId is null, doesn't match the query.
     *       Previously the archived note would be unintentionally trashed.
     *   (c) Unarchive logic becomes trivial (see below) — no folder-existence
     *       check needed, no complexity, no edge cases.
     *
     * UNARCHIVING (isArchived true → false):
     *   - isArchived = false
     *   - folderId is already null (was cleared on archive)
     *   - Note appears in the main list
     *
     *   WHY ALWAYS MAIN LIST ON UNARCHIVE?
     *   The original folder may or may not still exist. Checking adds complexity
     *   and the user explicitly chose to archive this note — their mental model
     *   of "where it belongs" may have changed since then. Main list is the
     *   universally correct fallback: always visible, never lost, user can
     *   drag it into a folder themselves if they want.
     *
     * NO FOLDER-EXISTENCE CHECK NEEDED ANYWHERE IN THIS METHOD.
     * The previous implementation had a folderDao.getFolderById() check here.
     * It is completely removed. The logic is now straightforward:
     *   archive → clear folderId, set isArchived = true
     *   unarchive → set isArchived = false (folderId already null)
     */
    override suspend fun toggleArchive(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return

        if (note.isArchived) {
            // UNARCHIVING — simply flip the flag, note goes to main list
            noteDao.updateNote(
                note.copy(
                    isArchived = false,
                    // folderId is already null (was cleared on archive)
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            // ARCHIVING — flip the flag AND clear folder reference
            noteDao.updateNote(
                note.copy(
                    isArchived = true,
                    folderId = null,     // leave the folder immediately
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}