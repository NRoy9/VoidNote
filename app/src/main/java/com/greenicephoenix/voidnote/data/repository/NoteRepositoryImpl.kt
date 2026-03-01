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
 * NoteEncryptionManager is injected and called at two boundaries:
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
 * ─── SEARCH STRATEGY ─────────────────────────────────────────────────────────
 *
 * searchNotes() below does SQL LIKE on DB content — but because content is
 * encrypted, the SQL LIKE matches nothing meaningful. It is therefore
 * NOT used by SearchViewModel.
 *
 * SearchViewModel correctly calls getAllNotes() instead, which returns fully
 * decrypted notes, and then filters them in-memory with Kotlin's .contains().
 * This is the correct pattern for any encrypted notes app — Standard Notes,
 * Bitwarden, and others all use the same approach.
 *
 * searchNotes() is kept in the interface and implemented here (with decryption)
 * so it is correct if anything calls it in future, but it is not the primary
 * search path.
 *
 * ─── FLAG-ONLY UPDATES ───────────────────────────────────────────────────────
 *
 * togglePin, toggleArchive, moveToTrash, restoreFromTrash, moveNoteToFolder
 * all update only boolean flags or folderId — not content. They work directly
 * on NoteEntity from the DAO, bypassing decrypt → re-encrypt. This is both
 * faster and correct — the ciphertext in the DB is untouched.
 */
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val encryption: NoteEncryptionManager
) : NoteRepository {

    // ─── Encryption helpers ───────────────────────────────────────────────────

    /**
     * Returns a copy of the Note with title, content, and tags encrypted.
     * Called before every write to the database.
     */
    private fun Note.encrypted(): Note = copy(
        title   = encryption.encrypt(title),
        content = encryption.encrypt(content),
        tags    = tags.map { encryption.encrypt(it) }
    )

    /**
     * Returns a copy of the Note with title, content, and tags decrypted.
     * Called after every read from the database.
     * decrypt() handles plain-text values gracefully for migration safety.
     */
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
     * SQL-based search — exists for interface completeness but is NOT the primary
     * search path. SearchViewModel uses getAllNotes() + in-memory Kotlin filter
     * instead, because SQL LIKE cannot match against encrypted ciphertext.
     *
     * This implementation decrypts results so it behaves correctly if called,
     * but results will be empty because the SQL WHERE clause matches nothing
     * in an encrypted database.
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

    override suspend fun moveToTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(
                isTrashed  = true,
                folderId   = null,
                isArchived = false,
                updatedAt  = System.currentTimeMillis()
            )
        )
    }

    override suspend fun restoreFromTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        noteDao.updateNote(
            note.copy(isTrashed = false, updatedAt = System.currentTimeMillis())
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

    /**
     * ARCHIVE: clear folderId, set isArchived = true → note leaves folder instantly.
     * UNARCHIVE: set isArchived = false → note goes to main list (folderId already null).
     * See Sprint 3 fix3 for full rationale on why folderId is cleared on archive.
     */
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