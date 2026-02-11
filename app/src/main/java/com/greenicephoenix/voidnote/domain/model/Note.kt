package com.greenicephoenix.voidnote.domain.model

/**
 * Note - Domain model representing a note in the app
 *
 * This is the "business logic" version of a note.
 * Clean, simple, no database annotations (that's in data layer)
 *
 * @param id Unique identifier for the note
 * @param title Note title
 * @param content Note content (will be encrypted in storage)
 * @param createdAt Timestamp when note was created (milliseconds)
 * @param updatedAt Timestamp when note was last updated
 * @param isPinned Whether note is pinned to top
 * @param isArchived Whether note is archived
 * @param isTrashed Whether note is in trash
 * @param color Optional color tag for the note
 * @param tags List of tag names associated with this note
 * @param folderId Id of folder associated with this note
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val color: String? = null,
    val tags: List<String> = emptyList(),
    val folderId: String? = null
) {
    /**
     * Get a preview of the content (first 2 lines)
     * Used in the notes list to show a snippet
     */
    fun getContentPreview(maxLength: Int = 100): String {
        return if (content.length > maxLength) {
            content.take(maxLength).trim() + "..."
        } else {
            content
        }
    }

    /**
     * Check if note is empty (no title and no content)
     */
    fun isEmpty(): Boolean {
        return title.isBlank() && content.isBlank()
    }
}