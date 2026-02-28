package com.greenicephoenix.voidnote.data.repository

import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.mapper.toDomainModel
import com.greenicephoenix.voidnote.data.mapper.toEntity
import com.greenicephoenix.voidnote.data.mapper.toFolderDomainModels
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * FolderRepositoryImpl — Room-backed implementation of FolderRepository.
 *
 * SPRINT 3 FIX:
 * Implements observeFolder() by delegating to FolderDao.observeFolderById()
 * and mapping the nullable FolderEntity? to a nullable domain Folder?.
 *
 * WHY .map { it?.toDomainModel() }?
 * The DAO returns Flow<FolderEntity?> — nullable because the folder might
 * not exist. We can't call toDomainModel() on null, so we use the safe
 * call operator (?.) inside the map transform.
 * Result: if the entity is null, the domain model is also null.
 * The ViewModel handles null by treating it as "folder deleted, navigate back".
 */
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {

    override fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFolders()
            .map { entities -> entities.toFolderDomainModels() }
    }

    override fun getRootFolders(): Flow<List<Folder>> {
        return folderDao.getRootFolders()
            .map { entities -> entities.toFolderDomainModels() }
    }

    override fun getSubFolders(parentId: String): Flow<List<Folder>> {
        return folderDao.getSubFolders(parentId)
            .map { entities -> entities.toFolderDomainModels() }
    }

    override suspend fun getFolderById(folderId: String): Folder? {
        return folderDao.getFolderById(folderId)?.toDomainModel()
    }

    /**
     * SPRINT 3 FIX — Observe a single folder reactively.
     *
     * Delegates to FolderDao.observeFolderById() and maps each emission.
     * The .map {} transform runs every time Room detects a change to that
     * folder row — which happens immediately after updateFolder() is called.
     */
    override fun observeFolder(folderId: String): Flow<Folder?> {
        return folderDao.observeFolderById(folderId)
            .map { entity -> entity?.toDomainModel() }
    }

    override suspend fun createFolder(folder: Folder) {
        folderDao.insertFolder(folder.toEntity())
    }

    override suspend fun updateFolder(folder: Folder) {
        folderDao.updateFolder(folder.toEntity())
    }

    override suspend fun deleteFolder(folderId: String) {
        val folder = folderDao.getFolderById(folderId)
        folder?.let {
            folderDao.deleteFolder(it)
        }
    }

    override fun getFolderCount(): Flow<Int> {
        return folderDao.getFolderCount()
    }
}