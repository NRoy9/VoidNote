package com.greenicephoenix.voidnote.data.mapper

import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.model.NoteColor

/**
 * Mapper functions to convert between NoteEntity (database) and Note (domain).
 *
 * Keeps the data layer and domain layer cleanly separated — neither model
 * knows about the other, and the mapper is the only place that bridges them.
 *
 * VERSION 5: trashedAt field added to both models and mapped here.
 * VERSION 7 (Sprint 6): color field added — stored as String, mapped to NoteColor enum.
 * VERSION 8 (Sprint 11): linkedNoteIds field added — List<String>, stored via StringListConverter.
 * VERSION 9 (Sprint 12): isDiaryEntry field added — Boolean, stored as INTEGER 0/1.
 */

/**
 * Convert NoteEntity (database row) → Note (domain model).
 * Called after every database read in NoteRepositoryImpl.
 */
fun NoteEntity.toDomainModel(): Note {
    return Note(
        id             = this.id,
        title          = this.title,
        content        = this.content,
        contentFormats = this.contentFormats,
        createdAt      = this.createdAt,
        updatedAt      = this.updatedAt,
        isPinned       = this.isPinned,
        isArchived     = this.isArchived,
        isTrashed      = this.isTrashed,
        trashedAt      = this.trashedAt,
        tags           = this.tags,
        folderId       = this.folderId,
        color          = NoteColor.fromString(this.color),
        linkedNoteIds  = this.linkedNoteIds,
        isDiaryEntry   = this.isDiaryEntry   // v9: journal flag
    )
}

/**
 * Convert Note (domain model) → NoteEntity (database row).
 * Called before every database write in NoteRepositoryImpl.
 *
 * @param folderId  Optional override — if the Note's folderId is null,
 *                  this parameter is used instead. Allows the repository
 *                  to assign a folder when inserting a new note.
 */
fun Note.toEntity(folderId: String? = null): NoteEntity {
    return NoteEntity(
        id             = this.id,
        title          = this.title,
        content        = this.content,
        contentFormats = this.contentFormats,
        createdAt      = this.createdAt,
        updatedAt      = this.updatedAt,
        isPinned       = this.isPinned,
        isArchived     = this.isArchived,
        isTrashed      = this.isTrashed,
        trashedAt      = this.trashedAt,
        tags           = this.tags,
        folderId       = this.folderId ?: folderId,
        color          = this.color?.name,
        linkedNoteIds  = this.linkedNoteIds,
        isDiaryEntry   = this.isDiaryEntry   // v9: journal flag
    )
}

/**
 * Convert a list of NoteEntity → list of Note.
 * Convenience extension used throughout NoteRepositoryImpl.
 */
fun List<NoteEntity>.toDomainModels(): List<Note> {
    return this.map { it.toDomainModel() }
}