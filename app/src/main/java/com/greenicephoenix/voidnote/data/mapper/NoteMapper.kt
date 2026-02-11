package com.greenicephoenix.voidnote.data.mapper

import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import com.greenicephoenix.voidnote.domain.model.Note

/**
 * Mapper functions to convert between Entity (database) and Domain models
 *
 * Why separate models?
 * - Database layer (Entity) = How data is stored
 * - Domain layer (Note) = How business logic uses data
 * - Keeps database changes isolated from business logic
 */

/**
 * Convert NoteEntity (database) to Note (domain model)
 */
fun NoteEntity.toDomainModel(): Note {
    return Note(
        id = this.id,
        title = this.title,
        content = this.content,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        isPinned = this.isPinned,
        isArchived = this.isArchived,
        isTrashed = this.isTrashed,
        color = this.color,
        tags = this.tags,
        folderId = this.folderId // ✅ ADD THIS LINE - This was missing!
    )
}

/**
 * Convert Note (domain model) to NoteEntity (database)
 */
fun Note.toEntity(folderId: String? = null): NoteEntity {
    return NoteEntity(
        id = this.id,
        title = this.title,
        content = this.content,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        isPinned = this.isPinned,
        isArchived = this.isArchived,
        isTrashed = this.isTrashed,
        color = this.color,
        tags = this.tags,
        folderId = this.folderId ?: folderId // Prefer note's folderId, fallback to parameter
    )
}

/**
 * Convert list of NoteEntity to list of Note
 */
fun List<NoteEntity>.toDomainModels(): List<Note> {
    return this.map { it.toDomainModel() }
}