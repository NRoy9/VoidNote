package com.greenicephoenix.voidnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.greenicephoenix.voidnote.data.local.converter.StringListConverter

/**
 * Room Database Entity for Notes
 *
 * This represents how notes are stored in SQLite database
 * The actual content will be encrypted before storing
 *
 * @Entity annotation marks this as a database table
 * tableName = "notes" sets the table name in SQLite
 */
@Entity(tableName = "notes")
@TypeConverters(StringListConverter::class)
data class NoteEntity(
    @PrimaryKey
    val id: String,

    val title: String,

    // Content will be encrypted before storing
    val content: String,

    val createdAt: Long,

    val updatedAt: Long,

    val isPinned: Boolean = false,

    val isArchived: Boolean = false,

    val isTrashed: Boolean = false,

    val color: String? = null,

    // TypeConverter handles List<String> ↔ String conversion
    val tags: List<String> = emptyList(),

    // Folder relationship (null = root level, no folder)
    val folderId: String? = null
)