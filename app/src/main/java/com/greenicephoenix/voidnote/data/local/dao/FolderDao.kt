package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.*
import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * FolderDao — Room Data Access Object for folder operations.
 *
 * SPRINT 3 FIX:
 * Added observeFolderById() — a Flow-based version of getFolderById().
 *
 * WHY TWO VERSIONS?
 * - getFolderById() is a suspend fun — one-shot read. Use it when you need
 *   the folder data once (e.g. before a write operation).
 * - observeFolderById() is a Flow — stays open and re-emits whenever that
 *   folder row changes in the database. Use it when the UI needs to react
 *   to changes (e.g. the top bar title updating after a rename).
 *
 * Room generates the implementation for both automatically. The difference
 * is just the return type: suspend + nullable = one-shot, Flow = reactive.
 */
@Dao
interface FolderDao {

    /**
     * Get all folders as a reactive stream, ordered alphabetically.
     * UI observing this will update when any folder is added, renamed, or deleted.
     */
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    /**
     * Get root level folders (no parent) as a reactive stream.
     */
    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY name ASC")
    fun getRootFolders(): Flow<List<FolderEntity>>

    /**
     * Get subfolders of a parent folder as a reactive stream.
     */
    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY name ASC")
    fun getSubFolders(parentId: String): Flow<List<FolderEntity>>

    /**
     * One-shot read of a single folder by ID.
     * Returns null if the folder doesn't exist.
     * Use this for operations that need the current folder data once
     * (e.g. before calling updateFolder or deleteFolder).
     */
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): FolderEntity?

    /**
     * SPRINT 3 FIX — Reactive stream for a single folder by ID.
     *
     * Re-emits whenever the folder row is updated — e.g. after a rename.
     * Used in FolderNotesViewModel via combine() so the top bar title
     * updates immediately when the user renames the folder, without
     * needing to navigate away and back.
     *
     * Returns Flow<FolderEntity?> — nullable because the folder could be
     * deleted while the screen is open (which triggers the delete flow anyway).
     */
    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun observeFolderById(folderId: String): Flow<FolderEntity?>

    /** Insert or replace a folder. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    /** Update an existing folder row. */
    @Update
    suspend fun updateFolder(folder: FolderEntity)

    /** Delete a folder row. */
    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    /** Get total folder count as a reactive stream. */
    @Query("SELECT COUNT(*) FROM folders")
    fun getFolderCount(): Flow<Int>
}