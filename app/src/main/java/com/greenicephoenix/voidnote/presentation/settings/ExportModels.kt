package com.greenicephoenix.voidnote.presentation.settings

import kotlinx.serialization.Serializable

/**
 * Export data models for JSON backup
 *
 * These models preserve ALL note data including:
 * - Text formatting (bold, italic, underline)
 * - Structure (checkboxes, lists, headings)
 * - Media (images, audio, drawings) - future support
 * - Metadata (tags, timestamps, folder assignment)
 */

/**
 * Root backup structure
 */
@Serializable
data class VoidNoteBackup(
    val version: String = "1.0",
    val exportDate: Long,
    val appVersion: String = "1.0.0",
    val noteCount: Int,
    val folderCount: Int,
    val notes: List<NoteBackup>,
    val folders: List<FolderBackup>
)

/**
 * Individual note backup
 */
@Serializable
data class NoteBackup(
    val id: String,
    val title: String,
    val content: String,
    val contentType: ContentType = ContentType.RICH_TEXT,
    val formatting: List<FormattingSpan> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val tags: List<String> = emptyList(),
    val folderId: String? = null,

    // Future support for media
    val images: List<ImageAttachment> = emptyList(),
    val audioFiles: List<AudioAttachment> = emptyList(),
    val drawings: List<DrawingAttachment> = emptyList()
)

/**
 * Content type enum
 */
@Serializable
enum class ContentType {
    PLAIN_TEXT,      // No formatting
    RICH_TEXT,       // Bold, italic, underline
    MARKDOWN,        // Markdown syntax
    CHECKLIST        // Todo items with checkboxes
}

/**
 * Text formatting span
 * Preserves bold, italic, underline, strikethrough
 */
@Serializable
data class FormattingSpan(
    val start: Int,           // Start character index
    val end: Int,             // End character index
    val type: FormattingType  // Bold, italic, etc.
)

@Serializable
enum class FormattingType {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    BULLET_LIST,
    NUMBERED_LIST,
    CHECKBOX_CHECKED,
    CHECKBOX_UNCHECKED,
    CODE_BLOCK
}

/**
 * Image attachment (future support)
 */
@Serializable
data class ImageAttachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val base64Data: String? = null,  // Optional: inline base64
    val filePath: String? = null,    // Or reference to external file
    val width: Int,
    val height: Int,
    val position: Int  // Character position in text
)

/**
 * Audio attachment (future support)
 */
@Serializable
data class AudioAttachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val base64Data: String? = null,
    val filePath: String? = null,
    val durationMs: Long,
    val position: Int
)

/**
 * Drawing attachment (future support)
 */
@Serializable
data class DrawingAttachment(
    val id: String,
    val fileName: String,
    val svgData: String,  // SVG format for scalability
    val position: Int
)

/**
 * Folder backup
 */
@Serializable
data class FolderBackup(
    val id: String,
    val name: String,
    val createdAt: Long,
    val parentFolderId: String? = null  // For nested folders
)