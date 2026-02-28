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
 * SPRINT 3 FIXES:
 *
 * 1. FolderDao injected alongside NoteDao.
 *    NoteRepositoryImpl and FolderRepositoryImpl are both in the data layer,
 *    so one can reference the other's DAO without creating circular dependencies.
 *    FolderDao is needed so toggleArchive() can verify a folder still exists
 *    before deciding whether to keep or clear a note's folderId on unarchive.
 *
 * 2. moveToTrash() now clears folderId.
 *    Trash is a global bin with no folder context. Clearing folderId means
 *    restoring from trash always puts the note in the main list — predictable,
 *    zero orphan risk regardless of whether the folder still exists.
 *
 * 3. toggleArchive() checks folder existence on UNARCHIVE.
 *    Archive preserves folder context (folderId is kept on archive).
 *    On unarchive: if the folder still exists → keep folderId (note returns
 *    to its folder). If folder was deleted → clear folderId (note goes to
 *    main list). The user never loses a note to an invisible orphan state.
 *
 * 4. trashNotesByFolder() — new method.
 *    Called when a folder is deleted. Sends all notes in that folder to trash
 *    with folderId cleared, using a single SQL UPDATE (atomic, fast).
 */
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao   // SPRINT 3: needed for archive folder-check
) : NoteRepository {

    // ─────────────────────────────────────────────────────────────────────────
    // READ OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // TRASH OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Move a single note to trash.
     *
     * SPRINT 3 FIX: folderId is cleared (set to null).
     *
     * WHY?
     * Trash is a global recovery bin — it has no folder concept.
     * If we kept folderId and the folder was deleted before the user restores
     * the note, the note would be orphaned (folderId points to a non-existent
     * folder, note invisible on every screen). Clearing it on the way IN
     * means restoreFromTrash() is always safe: the note always comes back to
     * the main list.
     *
     * BEFORE (broken):
     *   note.folderId = "work-folder-id" → goes to trash → folder gets deleted
     *   → user restores note → folderId still "work-folder-id" → folder gone
     *   → note invisible everywhere = ORPHANED
     *
     * AFTER (fixed):
     *   note.folderId = "work-folder-id" → goes to trash with folderId = null
     *   → folder gets deleted → user restores note → folderId null → main list ✓
     */
    override suspend fun moveToTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(
                isTrashed = true,
                folderId = null,     // FIXED: clear folder context on trash
                isArchived = false,  // un-archive if it was archived before trashing
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Restore a note from trash.
     *
     * Simply sets isTrashed = false. Since folderId was cleared when the note
     * was trashed, the note returns to the main list (root level). No folder
     * check needed — nothing can be orphaned here.
     */
    override suspend fun restoreFromTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(
                isTrashed = false,
                updatedAt = System.currentTimeMillis()
                // folderId stays null — note goes to main list
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
     * SPRINT 3 FIX — Trash all notes in a folder at once.
     *
     * Called when a folder is deleted. Uses a single SQL UPDATE to:
     * - Set isTrashed = 1 on all notes in the folder
     * - Set folderId = NULL (clear folder context — safe restore later)
     * - Set isArchived = 0 (a trashed note is no longer archived)
     * - Set updatedAt = now
     *
     * The notes appear in TrashScreen immediately and can be individually
     * restored (to the main list) or permanently deleted from there.
     *
     * This replaces the old loop-and-permanently-delete approach.
     * The user can always recover notes they didn't mean to trash.
     */
    override suspend fun trashNotesByFolder(folderId: String) {
        noteDao.trashNotesByFolder(
            folderId = folderId,
            timestamp = System.currentTimeMillis()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PIN / ARCHIVE
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun togglePin(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis())
        )
    }

    /**
     * Toggle the archived state of a note.
     *
     * ARCHIVING (isArchived false → true):
     * folderId is KEPT. Archive means "I still care about this note, just
     * hiding it from the main view." The folder membership is preserved so
     * that unarchiving can return the note to its original location.
     *
     * UNARCHIVING (isArchived true → false):
     * We check whether the note's folder still exists.
     *
     * CASE 1 — folder still exists:
     *   Keep folderId. The note returns exactly where it was before archiving.
     *   User sees no interruption to their folder organisation.
     *
     * CASE 2 — folder was deleted while note was archived:
     *   Clear folderId (set to null). The note goes to the main list.
     *   This is the correct fallback — the user can see the note and
     *   decide where to put it. It's never invisible (orphaned).
     *
     * CASE 3 — folderId is already null:
     *   Note was never in a folder. Nothing to check. It goes to main list.
     *
     * WHY CHECK IN THE REPOSITORY AND NOT THE VIEWMODEL?
     * The folder-existence check is a data concern — it queries the database.
     * Repositories own data logic. ViewModels own UI/navigation logic.
     * Keeping the check here means any ViewModel that calls toggleArchive()
     * gets the correct behaviour automatically with no extra code.
     */
    override suspend fun toggleArchive(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return

        if (note.isArchived) {
            // UNARCHIVING — check if the folder this note belonged to still exists
            val folderStillExists = note.folderId?.let { id ->
                folderDao.getFolderById(id) != null
            } ?: false
            // folderStillExists = false if folderId was null OR folder not found

            noteDao.updateNote(
                note.copy(
                    isArchived = false,
                    // Keep folderId if folder exists, clear it if folder is gone
                    folderId = if (folderStillExists) note.folderId else null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            // ARCHIVING — just flip the flag, keep folderId intact
            noteDao.updateNote(
                note.copy(
                    isArchived = true,
                    updatedAt = System.currentTimeMillis()
                    // folderId unchanged
                )
            )
        }
    }
}