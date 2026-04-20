package com.greenicephoenix.voidnote.data.mapper

import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import com.greenicephoenix.voidnote.domain.model.Folder

/**
 * Mapper functions for Folder.
 *
 * SPRINT 15: passwordHash and passwordSalt fields added to both directions.
 */
fun FolderEntity.toDomainModel(): Folder {
    return Folder(
        id             = this.id,
        name           = this.name,
        parentFolderId = this.parentFolderId,
        color          = this.color,
        createdAt      = this.createdAt,
        updatedAt      = this.updatedAt,
        passwordHash   = this.passwordHash,
        passwordSalt   = this.passwordSalt
    )
}

/**
 * Convert Folder (domain) to FolderEntity (database)
 */
fun Folder.toEntity(): FolderEntity {
    return FolderEntity(
        id             = this.id,
        name           = this.name,
        parentFolderId = this.parentFolderId,
        color          = this.color,
        createdAt      = this.createdAt,
        updatedAt      = this.updatedAt,
        passwordHash   = this.passwordHash,  // Sprint 15
        passwordSalt   = this.passwordSalt   // Sprint 15
    )
}

/**
 * Convert list of FolderEntity to list of Folder
 */
fun List<FolderEntity>.toFolderDomainModels(): List<Folder> {
    return this.map { it.toDomainModel() }
}