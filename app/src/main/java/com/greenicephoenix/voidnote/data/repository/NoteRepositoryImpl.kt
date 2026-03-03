package com.greenicephoenix.voidnote.data.repository

import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.mapper.toDomainModel
import com.greenicephoenix.voidnote.data.mapper.toDomainModels
import com.greenicephoenix.voidnote.data.mapper.toEntity
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * NoteRepositoryImpl — Room-backed implementation of NoteRepository.
 *
 * ─── ENCRYPTION LAYER ────────────────────────────────────────────────────────
 *
 * ON WRITE: Note (plain text) → encrypt fields → NoteEntity (ciphertext) → Room
 * ON READ:  Room → NoteEntity (ciphertext) → decrypt fields → Note (plain text)
 *
 * NO OTHER CLASS KNOWS ENCRYPTION EXISTS.
 * ViewModels, screens, the editor — all work with plain text Note objects.
 *
 * ENCRYPTED FIELDS: title, content, tags (each tag individually)
 * NOT ENCRYPTED: id, folderId, timestamps, flags — metadata, not sensitive
 *
 * ─── VERSION 5 CHANGE ────────────────────────────────────────────────────────
 *
 * moveToTrash() now records the current timestamp in trashedAt.
 * restoreFromTrash() now clears trashedAt back to null.
 *
 * This feeds TrashCleanupWorker, which runs daily and permanently deletes
 * notes where trashedAt is older than 30 days.
 *
 * ─── FLAG-ONLY UPDATES ───────────────────────────────────────────────────────
 *
 * togglePin, toggleArchive, moveToTrash, restoreFromTrash, moveNoteToFolder
 * all update only boolean flags or timestamps — not encrypted content.
 * They work directly on NoteEntity from the DAO, bypassing decrypt → re-encrypt.
 * This is both faster and correct — the ciphertext in the DB is untouched.
 */
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val encryption: NoteEncryptionManager
) : NoteRepository {

    // ─── Encryption helpers ───────────────────────────────────────────────────

    private fun Note.encrypted(): Note = copy(
        title   = encryption.encrypt(title),
        content = encryption.encrypt(content),
        tags    = tags.map { encryption.encrypt(it) }
    )

    private fun Note.decrypted(): Note = copy(
        title   = encryption.decrypt(title),
        content = encryption.decrypt(content),
        tags    = tags.map { encryption.decrypt(it) }
    )

    // ─── Read ─────────────────────────────────────────────────────────────────

    override fun getAllNotes(): Flow<List<Note>> =
        noteDao.getAllNotes()
            .map { entities -> entities.toDomainModels().map { it.decrypted() } }

    override fun getNotesByFolder(folderId: String): Flow<List<Note>> =
        noteDao.getNotesByFolder(folderId)
            .map { entities -> entities.toDomainModels().map { it.decrypted() } }

    override fun getNotesWithoutFolder(): Flow<List<Note>> =
        noteDao.getNotesWithoutFolder()
            .map { entities -> entities.toDomainModels().map { it.decrypted() } }

    override suspend fun getNoteById(noteId: String): Note? =
        noteDao.getNoteById(noteId)?.toDomainModel()?.decrypted()

    override fun getPinnedNotes(): Flow<List<Note>> =
        noteDao.getPinnedNotes()
            .map { entities -> entities.toDomainModels().map { it.decrypted() } }

    override fun getArchivedNotes(): Flow<List<Note>> =
        noteDao.getArchivedNotes()
            .map { entities -> entities.toDomainModels().map { it.decrypted() } }

    override fun getTrashedNotes(): Flow<List<Note>> =
        noteDao.getTrashedNotes()
            .map { entities -> entities.toDomainModels().map { it.decrypted() } }

    /**
     * SQL-based search — NOT the primary search path.
     * SearchViewModel uses getAllNotes() + in-memory Kotlin filter instead,
     * because SQL LIKE cannot match against encrypted ciphertext.
     */
    override fun searchNotes(query: String): Flow<List<Note>> =
        noteDao.searchNotes(query)
            .map { entities -> entities.toDomainModels().map { it.decrypted() } }

    override fun getNoteCount(): Flow<Int> =
        noteDao.getNoteCount()

    // ─── Write ────────────────────────────────────────────────────────────────

    override suspend fun insertNote(note: Note, folderId: String?) {
        noteDao.insertNote(note.encrypted().toEntity(folderId))
    }

    override suspend fun updateNote(note: Note, folderId: String?) {
        noteDao.updateNote(note.encrypted().toEntity(folderId))
    }

    override suspend fun moveNoteToFolder(noteId: String, folderId: String?) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(folderId = folderId, updatedAt = System.currentTimeMillis())
        )
    }

    // ─── Trash ────────────────────────────────────────────────────────────────

    /**
     * Move a note to trash and record WHEN it was trashed.
     *
     * VERSION 5: trashedAt is now set to the current timestamp.
     * TrashCleanupWorker reads this value to find notes older than 30 days.
     *
     * folderId is cleared so if the note is restored, it goes to the main
     * list rather than back into a (possibly deleted) folder.
     */
    override suspend fun moveToTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(
                isTrashed  = true,
                trashedAt  = System.currentTimeMillis(),   // ← v5: record when trashed
                folderId   = null,
                isArchived = false,
                updatedAt  = System.currentTimeMillis()
            )
        )
    }

    /**
     * Restore a note from trash.
     *
     * VERSION 5: trashedAt is cleared back to null.
     * This ensures a re-trashed note gets a fresh 30-day timer if it
     * is moved to trash again in the future.
     */
    override suspend fun restoreFromTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(
                isTrashed = false,
                trashedAt = null,                          // ← v5: clear the trash timestamp
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

    override suspend fun trashNotesByFolder(folderId: String) {
        noteDao.trashNotesByFolder(
            folderId  = folderId,
            timestamp = System.currentTimeMillis()
        )
    }

    // ─── Pin / Archive ────────────────────────────────────────────────────────

    override suspend fun togglePin(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis())
        )
    }

    override suspend fun toggleArchive(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        if (note.isArchived) {
            noteDao.updateNote(
                note.copy(isArchived = false, updatedAt = System.currentTimeMillis())
            )
        } else {
            noteDao.updateNote(
                note.copy(
                    isArchived = true,
                    folderId   = null,
                    updatedAt  = System.currentTimeMillis()
                )
            )
        }
    }
}