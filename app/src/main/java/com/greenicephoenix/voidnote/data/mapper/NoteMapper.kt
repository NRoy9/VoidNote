package com.greenicephoenix.voidnote.data.mapper

import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import com.greenicephoenix.voidnote.domain.model.Note

/**
 * Mapper functions to convert between Entity (database) and Domain models
 *
 * Keeps data layer and domain layer cleanly separated.
 */

/**
 * Convert NoteEntity (database) → Note (domain)
 */
fun NoteEntity.toDomainModel(): Note {
    return Note(
        id = this.id,
        title = this.title,
        content = this.content,
        contentFormats = this.contentFormats,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        isPinned = this.isPinned,
        isArchived = this.isArchived,
        isTrashed = this.isTrashed,
        tags = this.tags,
        folderId = this.folderId
    )
}

fun Note.toEntity(folderId: String? = null): NoteEntity {
    return NoteEntity(
        id = this.id,
        title = this.title,
        content = this.content,
        contentFormats = this.contentFormats,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        isPinned = this.isPinned,
        isArchived = this.isArchived,
        isTrashed = this.isTrashed,
        tags = this.tags,
        folderId = this.folderId ?: folderId
    )
}

/**
 * Convert list of NoteEntity → list of Note
 */
fun List<NoteEntity>.toDomainModels(): List<Note> {
    return this.map { it.toDomainModel() }
}