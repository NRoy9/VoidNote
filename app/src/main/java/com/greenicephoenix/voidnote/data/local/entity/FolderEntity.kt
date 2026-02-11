package com.greenicephoenix.voidnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database Entity for Folders
 *
 * Folders organize notes into groups
 * Example: "Work Notes", "Personal", "Recipes"
 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    // For nested folders (folder inside folder)
    // null = root level folder
    val parentFolderId: String? = null,

    // Optional: Color code folders
    val color: String? = null,

    val createdAt: Long,

    // Track modification time
    val updatedAt: Long = createdAt
)