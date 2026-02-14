package com.greenicephoenix.voidnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.greenicephoenix.voidnote.data.local.converter.StringListConverter
import com.greenicephoenix.voidnote.domain.model.FormatRange

@Entity(tableName = "notes")
@TypeConverters(StringListConverter::class)
data class NoteEntity(

    @PrimaryKey
    val id: String,

    val title: String,

    val content: String,

    val createdAt: Long,

    val updatedAt: Long,

    val isPinned: Boolean = false,

    val isArchived: Boolean = false,

    val isTrashed: Boolean = false,

    val tags: List<String> = emptyList(),

    val folderId: String? = null,

    val contentFormats: List<FormatRange>
)
