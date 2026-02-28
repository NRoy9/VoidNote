package com.greenicephoenix.voidnote.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.greenicephoenix.voidnote.security.BiometricLockManager
import javax.inject.Inject
import kotlinx.coroutines.flow.map

enum class ExportFormat {
    JSON, TXT
}

/**
 * ViewModel for Settings Screen
 *
 * NOW WITH PERSISTENT THEME STORAGE!
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val preferencesManager: PreferencesManager,
    private val biometricLockManager: BiometricLockManager
) : ViewModel() {

    // ── Biometric lock ────────────────────────────────────────────────────────

    /**
     * Whether this device supports biometric/device-credential authentication.
     * Checked once at ViewModel creation — hardware doesn't change at runtime.
     * The Settings screen uses this to show/hide the biometric toggle item.
     */
    val isBiometricAvailable: Boolean = biometricLockManager.isAvailable()

    /**
     * Reactive biometric lock preference from DataStore.
     * Settings screen observes this to keep the Switch in sync.
     */
    val biometricLockEnabled: StateFlow<Boolean> = preferencesManager.biometricLockFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Enable or disable the biometric lock.
     * Called by the Switch onCheckedChange in SettingsScreen.
     */
    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setBiometricLock(enabled)
        }
    }

    // Current theme from DataStore
    val currentTheme: StateFlow<AppTheme> = preferencesManager.themeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.DARK
        )

    // UI State
    val uiState: StateFlow<SettingsUiState> = combine(
        noteRepository.getNoteCount(),
        folderRepository.getFolderCount(),
        currentTheme
    ) { noteCount, folderCount, theme ->
        SettingsUiState(
            noteCount = noteCount,
            folderCount = folderCount,
            currentTheme = theme,
            appVersion = getAppVersion()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    /**
     * Change app theme (now persists!)
     */
    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            preferencesManager.setTheme(theme)
        }
    }

    /**
     * Clear all notes (with confirmation)
     */
    fun clearAllNotes() {
        viewModelScope.launch {
            try {
                // Get all notes and delete permanently
                val allNotes = noteRepository.getAllNotes().first()
                allNotes.forEach { note ->
                    noteRepository.deleteNotePermanently(note.id)
                }

                // Get all folders and delete
                val allFolders = folderRepository.getAllFolders().first()
                allFolders.forEach { folder ->
                    folderRepository.deleteFolder(folder.id)
                }

                // Empty trash as well
                noteRepository.emptyTrash()

                android.util.Log.d("Settings", "All data cleared successfully")
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Failed to clear data", e)
            }
        }
    }

    /**
     * Export all notes
     */
    fun exportNotes() {
        viewModelScope.launch {
            try {
                val notes = noteRepository.getAllNotes().first()

                // Create simple text export
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())

                val exportText = buildString {
                    appendLine("VOID NOTE BACKUP")
                    appendLine("Export Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                    appendLine("Total Notes: ${notes.size}")
                    appendLine()
                    appendLine("=" .repeat(50))
                    appendLine()

                    notes.forEach { note ->
                        appendLine("TITLE: ${note.title.ifBlank { "Untitled" }}")
                        appendLine("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.createdAt))}")
                        if (note.tags.isNotEmpty()) {
                            appendLine("Tags: ${note.tags.joinToString(", ")}")
                        }
                        if (note.isPinned) appendLine("[PINNED]")
                        appendLine()
                        appendLine(note.content)
                        appendLine()
                        appendLine("-" .repeat(50))
                        appendLine()
                    }
                }

                // Save to file
                val fileName = "voidnote_backup_$timestamp.txt"
                val exportDir = java.io.File(context.getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val file = java.io.File(exportDir, fileName)
                file.writeText(exportText)

                android.util.Log.d("Settings", "Export successful: ${file.absolutePath}")

                // Show success somehow (we'll add toast/snackbar later)

            } catch (e: Exception) {
                android.util.Log.e("Settings", "Export failed", e)
            }
        }
    }

    /**
     * Export notes to user-selected location
     * Supports both JSON (structured) and TXT (human-readable) formats
     */
    fun exportNotesToUri(
        contentResolver: android.content.ContentResolver,
        uri: android.net.Uri,
        format: ExportFormat
    ) {
        viewModelScope.launch {
            try {
                val notes = noteRepository.getAllNotes().first()
                val folders = folderRepository.getAllFolders().first()

                when (format) {
                    ExportFormat.JSON -> exportAsJson(contentResolver, uri, notes, folders)
                    ExportFormat.TXT -> exportAsTxt(contentResolver, uri, notes)
                }

                android.util.Log.d("Settings", "Export successful to: $uri (format: $format)")

            } catch (e: Exception) {
                android.util.Log.e("Settings", "Export failed", e)
            }
        }
    }

    /**
     * Export as JSON - preserves all formatting and metadata
     */
    private suspend fun exportAsJson(
        contentResolver: android.content.ContentResolver,
        uri: android.net.Uri,
        notes: List<com.greenicephoenix.voidnote.domain.model.Note>,
        folders: List<com.greenicephoenix.voidnote.domain.model.Folder>
    ) {
        val backup = VoidNoteBackup(
            version = "1.0",
            exportDate = System.currentTimeMillis(),
            appVersion = getAppVersion(),
            noteCount = notes.size,
            folderCount = folders.size,
            notes = notes.map { note ->
                NoteBackup(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    contentType = ContentType.RICH_TEXT,
                    formatting = parseFormatting(note.content),  // Parse formatting from content
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    isPinned = note.isPinned,
                    isArchived = note.isArchived,
                    tags = note.tags,
                    folderId = note.folderId,
                    // Future: add images, audio, drawings
                    images = emptyList(),
                    audioFiles = emptyList(),
                    drawings = emptyList()
                )
            },
            folders = folders.map { folder ->
                FolderBackup(
                    id = folder.id,
                    name = folder.name,
                    createdAt = folder.createdAt,
                    parentFolderId = null  // Future: nested folders
                )
            }
        )

        // Serialize to JSON
        val json = kotlinx.serialization.json.Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        val jsonString = json.encodeToString(VoidNoteBackup.serializer(), backup)

        // Write to file
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(jsonString.toByteArray())
        }
    }

    /**
     * Export as plain text - human-readable format
     */
    private suspend fun exportAsTxt(
        contentResolver: android.content.ContentResolver,
        uri: android.net.Uri,
        notes: List<com.greenicephoenix.voidnote.domain.model.Note>
    ) {
        val exportText = buildString {
            appendLine("VOID NOTE BACKUP")
            appendLine("Export Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("Total Notes: ${notes.size}")
            appendLine()
            appendLine("=" .repeat(50))
            appendLine()

            notes.forEach { note ->
                appendLine("TITLE: ${note.title.ifBlank { "Untitled" }}")
                appendLine("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.createdAt))}")
                if (note.tags.isNotEmpty()) {
                    appendLine("Tags: ${note.tags.joinToString(", ")}")
                }
                if (note.isPinned) appendLine("[PINNED]")
                if (note.isArchived) appendLine("[ARCHIVED]")
                appendLine()
                appendLine(note.content)
                appendLine()
                appendLine("-" .repeat(50))
                appendLine()
            }
        }

        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(exportText.toByteArray())
        }
    }

    /**
     * Parse formatting from content
     * TODO: Implement when we add rich text support
     * For now, returns empty list
     */
    private fun parseFormatting(content: String): List<FormattingSpan> {
        // Future: Parse markdown-style formatting
        // **bold** → FormattingSpan(start, end, BOLD)
        // *italic* → FormattingSpan(start, end, ITALIC)
        // - [ ] checkbox → FormattingSpan(start, end, CHECKBOX_UNCHECKED)
        return emptyList()
    }

    /**
     * Get app version from BuildConfig
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}