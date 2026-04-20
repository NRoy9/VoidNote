package com.greenicephoenix.voidnote.widget

import com.greenicephoenix.voidnote.data.local.VoidNoteDatabase
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import java.util.UUID

/**
 * WidgetNoteRepository — lightweight data access for the Quick Capture Widget.
 *
 * WHY NOT USE NoteRepositoryImpl?
 * NoteRepositoryImpl is injected via Hilt and returns domain Flow objects.
 * The widget runs in a non-Hilt context and needs simple suspend functions
 * returning plain data — no Flows, no Hilt, no ViewModel lifecycle.
 *
 * NoteEntity field types (important — these use TypeConverters):
 *   tags           → List<String>      (StringListConverter)
 *   contentFormats → List<FormatRange> (FormatRangeConverter)
 *   linkedNoteIds  → List<String>      (StringListConverter)
 * We pass emptyList() for all three — Room's TypeConverters handle serialisation.
 */
class WidgetNoteRepository(
    private val db: VoidNoteDatabase,
    private val encryption: NoteEncryptionManager
) {

    /**
     * Returns the 3 most recently updated non-trashed, non-archived,
     * non-diary notes as plain decrypted [WidgetNote] objects for display
     * in the medium widget's recent notes list.
     */
    suspend fun getRecentNotes(limit: Int = 3): List<WidgetNote> {
        return db.noteDao()
            .getRecentNotesForWidget(limit)
            .map { entity ->
                WidgetNote(
                    id      = entity.id,
                    title   = safeDecrypt(entity.title),
                    preview = safeDecrypt(entity.content).take(80).trim()
                )
            }
    }

    /**
     * Inserts a new note with the given title and content.
     *
     * Both fields are encrypted with AES-256-GCM before writing.
     * Returns the new note's UUID so the caller can open the editor for it.
     *
     * @param title   The note title. If blank, stored as encrypted empty string.
     * @param content The note body text.
     * @return The UUID of the newly created note.
     */
    suspend fun insertQuickNote(title: String, content: String): String {
        val id  = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = NoteEntity(
            id             = id,
            title          = encryption.encrypt(title.ifBlank { "Quick Note" }),
            content        = encryption.encrypt(content),
            createdAt      = now,
            updatedAt      = now,
            isPinned       = false,
            isArchived     = false,
            isTrashed      = false,
            trashedAt      = null,
            tags           = emptyList(),   // TypeConverter: List<String> → stored as JSON
            folderId       = null,
            contentFormats = emptyList(),   // TypeConverter: List<FormatRange> → stored as JSON
            color          = null,
            linkedNoteIds  = emptyList(),   // TypeConverter: List<String> → stored as JSON
            isDiaryEntry   = false
        )
        db.noteDao().insertNote(entity)
        return id
    }

    /**
     * Safely decrypts a string. If decryption fails (e.g. corrupted data),
     * returns an empty string rather than crashing the widget.
     */
    private fun safeDecrypt(ciphertext: String): String {
        return try {
            encryption.decrypt(ciphertext)
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Lightweight data class used by the widget to display recent notes.
 * Contains only what the RemoteViews list needs — no full Note domain model.
 */
data class WidgetNote(
    val id: String,
    val title: String,
    val preview: String
)