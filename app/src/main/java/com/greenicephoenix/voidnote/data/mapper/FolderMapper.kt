package com.greenicephoenix.voidnote.data.mapper

import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import com.greenicephoenix.voidnote.domain.model.Folder

/**
 * Mapper functions for Folder
 */

/**
 * Convert FolderEntity (database) to Folder (domain)
 */
fun FolderEntity.toDomainModel(): Folder {
    return Folder(
        id = this.id,
        name = this.name,
        parentFolderId = this.parentFolderId,
        color = this.color,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

/**
 * Convert Folder (domain) to FolderEntity (database)
 */
fun Folder.toEntity(): FolderEntity {
    return FolderEntity(
        id = this.id,
        name = this.name,
        parentFolderId = this.parentFolderId,
        color = this.color,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

/**
 * Convert list of FolderEntity to list of Folder
 */
fun List<FolderEntity>.toFolderDomainModels(): List<Folder> {
    return this.map { it.toDomainModel() }
}