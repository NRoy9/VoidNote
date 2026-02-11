package com.greenicephoenix.voidnote.domain.model

/**
 * Folder - Domain model for organizing notes
 *
 * Folders help users organize notes into categories
 * Examples: "Work", "Personal", "Recipes", "Banking"
 *
 * @param id Unique identifier
 * @param name Folder name (e.g., "Work Notes")
 * @param parentFolderId Parent folder ID for nested folders (null = root)
 * @param color Optional color for visual distinction
 * @param createdAt Creation timestamp
 * @param updatedAt Last modification timestamp
 */
data class Folder(
    val id: String,
    val name: String,
    val parentFolderId: String? = null,
    val color: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt
) {
    /**
     * Check if this is a root-level folder (no parent)
     */
    fun isRootFolder(): Boolean = parentFolderId == null

    /**
     * Check if folder name is valid
     */
    fun hasValidName(): Boolean = name.isNotBlank()
}