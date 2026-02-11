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
 * Implementation of FolderRepository
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