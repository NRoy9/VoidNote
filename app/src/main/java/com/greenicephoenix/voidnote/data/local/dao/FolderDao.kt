package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.*
import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Folders
 */
@Dao
interface FolderDao {

    /**
     * Get all folders ordered by name
     */
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    /**
     * Get root level folders (no parent)
     */
    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY name ASC")
    fun getRootFolders(): Flow<List<FolderEntity>>

    /**
     * Get subfolders of a parent folder
     */
    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY name ASC")
    fun getSubFolders(parentId: String): Flow<List<FolderEntity>>

    /**
     * Get single folder by ID
     */
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): FolderEntity?

    /**
     * Insert or update folder
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    /**
     * Update folder
     */
    @Update
    suspend fun updateFolder(folder: FolderEntity)

    /**
     * Delete folder
     */
    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    /**
     * Get folder count
     */
    @Query("SELECT COUNT(*) FROM folders")
    fun getFolderCount(): Flow<Int>
}