package com.greenicephoenix.voidnote.domain.model

/**
 * Note - Domain model representing a note in the app.
 *
 * This is the business layer representation.
 * No Room annotations here.
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val contentFormats: List<FormatRange> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val tags: List<String> = emptyList(),
    val folderId: String? = null
) {

    /**
     * Returns short preview text used in list screens.
     */
    fun getContentPreview(maxLength: Int = 100): String {
        return if (content.length > maxLength) {
            content.take(maxLength).trim() + "..."
        } else {
            content
        }
    }

    /**
     * Returns true if note has no meaningful content.
     */
    fun isEmpty(): Boolean {
        return title.isBlank() && content.isBlank()
    }
}