package com.greenicephoenix.voidnote.data.repository

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
 * Implementation of NoteRepository
 *
 * This is where the actual database operations happen
 * Hilt will inject this implementation when NoteRepository is needed
 *
 * @param noteDao Room DAO for database operations
 */
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao
) : NoteRepository {

    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes()
            .map { entities -> entities.toDomainModels() }
    }

    override fun getNotesByFolder(folderId: String): Flow<List<Note>> {
        return noteDao.getNotesByFolder(folderId)
            .map { entities -> entities.toDomainModels() }
    }

    /**
     * Move note to a folder
     */
    override suspend fun moveNoteToFolder(noteId: String, folderId: String?) {
        val note = noteDao.getNoteById(noteId)
        note?.let {
            noteDao.updateNote(
                it.copy(
                    folderId = folderId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun getNotesWithoutFolder(): Flow<List<Note>> {
        return noteDao.getNotesWithoutFolder()
            .map { entities -> entities.toDomainModels() }
    }

    override suspend fun getNoteById(noteId: String): Note? {
        return noteDao.getNoteById(noteId)?.toDomainModel()
    }

    override fun getPinnedNotes(): Flow<List<Note>> {
        return noteDao.getPinnedNotes()
            .map { entities -> entities.toDomainModels() }
    }

    override fun getArchivedNotes(): Flow<List<Note>> {
        return noteDao.getArchivedNotes()
            .map { entities -> entities.toDomainModels() }
    }

    override fun getTrashedNotes(): Flow<List<Note>> {
        return noteDao.getTrashedNotes()
            .map { entities -> entities.toDomainModels() }
    }

    override fun searchNotes(query: String): Flow<List<Note>> {
        return noteDao.searchNotes(query)
            .map { entities -> entities.toDomainModels() }
    }

    override suspend fun insertNote(note: Note, folderId: String?) {
        android.util.Log.d("NoteRepository", "insertNote called - noteId: ${note.id}, folderId from param: $folderId, folderId from note: ${note.folderId}")
        val entity = note.toEntity(folderId)
        android.util.Log.d("NoteRepository", "Entity created with folderId: ${entity.folderId}")
        noteDao.insertNote(entity)
    }

    override suspend fun updateNote(note: Note, folderId: String?) {
        android.util.Log.d("NoteRepository", "updateNote called - noteId: ${note.id}, folderId from param: $folderId, folderId from note: ${note.folderId}")
        val entity = note.toEntity(folderId)
        android.util.Log.d("NoteRepository", "Entity created with folderId: ${entity.folderId}")
        noteDao.updateNote(entity)
    }

    override suspend fun moveToTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId)
        note?.let {
            noteDao.updateNote(it.copy(isTrashed = true, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun restoreFromTrash(noteId: String) {
        val note = noteDao.getNoteById(noteId)
        note?.let {
            noteDao.updateNote(it.copy(isTrashed = false, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun deleteNotePermanently(noteId: String) {
        val note = noteDao.getNoteById(noteId)
        note?.let {
            noteDao.deleteNote(it)
        }
    }

    override suspend fun emptyTrash() {
        noteDao.deleteAllTrashedNotes()
    }

    override suspend fun togglePin(noteId: String) {
        val note = noteDao.getNoteById(noteId)
        note?.let {
            noteDao.updateNote(
                it.copy(
                    isPinned = !it.isPinned,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun toggleArchive(noteId: String) {
        val note = noteDao.getNoteById(noteId)
        note?.let {
            noteDao.updateNote(
                it.copy(
                    isArchived = !it.isArchived,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun getNoteCount(): Flow<Int> {
        return noteDao.getNoteCount()
    }

}