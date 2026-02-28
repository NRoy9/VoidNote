package com.greenicephoenix.voidnote.domain.repository

import com.greenicephoenix.voidnote.domain.model.Folder
import kotlinx.coroutines.flow.Flow

/**
 * FolderRepository — interface defining all folder data operations.
 *
 * SPRINT 3 FIX:
 * Added observeFolder(folderId) — a Flow that re-emits whenever the folder
 * row changes. This is the domain-layer equivalent of FolderDao.observeFolderById().
 *
 * INTERFACE VS IMPLEMENTATION:
 * This interface lives in the domain layer — it has no knowledge of Room,
 * SQLite, or any Android APIs. It just describes what's possible.
 * FolderRepositoryImpl (data layer) is where Room is actually used.
 * ViewModels depend on this interface, not the implementation — so swapping
 * the storage layer later (e.g. to SQLCipher) requires zero ViewModel changes.
 */
interface FolderRepository {

    /** All folders as a reactive stream — updates when any folder changes. */
    fun getAllFolders(): Flow<List<Folder>>

    /** Root-level folders (no parent) as a reactive stream. */
    fun getRootFolders(): Flow<List<Folder>>

    /** Subfolders of a given parent as a reactive stream. */
    fun getSubFolders(parentId: String): Flow<List<Folder>>

    /**
     * One-shot read — get a folder by ID once.
     * Returns null if not found.
     */
    suspend fun getFolderById(folderId: String): Folder?

    /**
     * SPRINT 3 FIX — Reactive stream for a single folder.
     *
     * Re-emits whenever this folder's row changes in the database.
     * Used by FolderNotesViewModel to keep the top bar title in sync
     * after a rename — without this the name only updates on recreation.
     *
     * Returns Flow<Folder?> — nullable because the folder could be deleted
     * while the screen is open.
     */
    fun observeFolder(folderId: String): Flow<Folder?>

    /** Create a new folder. */
    suspend fun createFolder(folder: Folder)

    /** Update an existing folder (used for rename). */
    suspend fun updateFolder(folder: Folder)

    /** Delete a folder by ID. */
    suspend fun deleteFolder(folderId: String)

    /** Total folder count as a reactive stream. */
    fun getFolderCount(): Flow<Int>
}