package com.greenicephoenix.voidnote.domain.repository

import com.greenicephoenix.voidnote.domain.model.Folder
import kotlinx.coroutines.flow.Flow

/**
 * Repository Interface for Folders
 */
interface FolderRepository {

    /**
     * Get all folders
     */
    fun getAllFolders(): Flow<List<Folder>>

    /**
     * Get root-level folders (no parent)
     */
    fun getRootFolders(): Flow<List<Folder>>

    /**
     * Get subfolders of a parent folder
     */
    fun getSubFolders(parentId: String): Flow<List<Folder>>

    /**
     * Get single folder by ID
     */
    suspend fun getFolderById(folderId: String): Folder?

    /**
     * Create new folder
     */
    suspend fun createFolder(folder: Folder)

    /**
     * Update existing folder
     */
    suspend fun updateFolder(folder: Folder)

    /**
     * Delete folder (and optionally its notes)
     */
    suspend fun deleteFolder(folderId: String)

    /**
     * Get folder count
     */
    fun getFolderCount(): Flow<Int>
}