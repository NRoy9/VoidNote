package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.*
import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * FolderDao — Room Data Access Object for folder operations.
 *
 * VERSION 5 ADDITION: getAllFoldersOnce()
 * Same pattern as NoteDao.getAllNotesOnce() — one-shot snapshot for export.
 *
 * SPRINT 3 FIX (unchanged):
 * observeFolderById() — reactive Flow so FolderNotesScreen title updates on rename.
 */
@Dao
interface FolderDao {

    /**
     * All folders as a reactive stream, ordered alphabetically.
     * UI observing this updates when any folder is added, renamed, or deleted.
     */
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    /**
     * One-shot snapshot of all folders.
     * Used by ImportExportManager for export — needs a complete list once,
     * not a live stream that stays open during the ZIP write.
     */
    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    suspend fun getAllFoldersOnce(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY name ASC")
    fun getRootFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY name ASC")
    fun getSubFolders(parentId: String): Flow<List<FolderEntity>>

    /**
     * One-shot read of a single folder by ID.
     * Returns null if the folder doesn't exist.
     */
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): FolderEntity?

    /**
     * Reactive stream for a single folder by ID.
     * Re-emits whenever the folder row is updated — e.g. after a rename.
     * Used in FolderNotesViewModel so the top bar title updates instantly.
     */
    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun observeFolderById(folderId: String): Flow<FolderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Query("SELECT COUNT(*) FROM folders")
    fun getFolderCount(): Flow<Int>
}