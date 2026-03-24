package com.greenicephoenix.voidnote.presentation.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.manager.ImportExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Which external app the user is migrating FROM.
 * Each format has its own file type hint shown to the user + MIME types
 * passed to the system file picker.
 */
enum class MigratorFormat(
    val label      : String,
    val emoji      : String,
    val description: String,
    val exportHint : String,        // shown in the format card to guide the user
    val mimeTypes  : Array<String>  // passed to ActivityResultContracts.OpenDocument
) {
    EVERNOTE(
        label       = "Evernote",
        emoji       = "🐘",
        description = "Import notes from an Evernote .enex export file",
        exportHint  = "Evernote → File → Export Notes → Export as ENEX",
        mimeTypes   = arrayOf("application/octet-stream", "*/*")
    ),
    KEEP(
        label       = "Google Keep",
        emoji       = "📌",
        description = "Import notes from a Google Takeout ZIP containing Keep data",
        exportHint  = "takeout.google.com → select Keep → Download",
        mimeTypes   = arrayOf("application/zip", "application/octet-stream", "*/*")
    ),
    NOTION(
        label       = "Notion",
        emoji       = "📝",
        description = "Import pages from a Notion Markdown & CSV export ZIP",
        exportHint  = "Notion → Settings → Export workspace → Markdown & CSV",
        mimeTypes   = arrayOf("application/zip", "application/octet-stream", "*/*")
    )
}

/**
 * State machine for MigratorScreen.
 *
 * Idle → (user picks format) → (file picker) → ReadingFile
 *      → Preview → (user taps Import) → Importing
 *      → Success | Error
 */
sealed class MigratorState {
    /** Format picker visible — no file chosen yet */
    object Idle : MigratorState()

    /** File selected; counting notes in the background — show spinner */
    data class ReadingFile(val format: MigratorFormat) : MigratorState()

    /** File read OK; show preview card + Import button */
    data class Preview(
        val format    : MigratorFormat,
        val fileName  : String,
        val noteCount : Int
    ) : MigratorState()

    /** Import running — non-dismissible progress dialog */
    object Importing : MigratorState()

    /** Import completed successfully */
    data class Success(
        val notesImported  : Int,
        val foldersCreated : Int = 0
    ) : MigratorState()

    /** Something went wrong — show message + retry option */
    data class Error(val message: String) : MigratorState()
}

@HiltViewModel
class MigratorViewModel @Inject constructor(
    private val importExportManager: ImportExportManager
) : ViewModel() {

    private val _state = MutableStateFlow<MigratorState>(MigratorState.Idle)
    val state: StateFlow<MigratorState> = _state.asStateFlow()
    private var selectedFileName : String = ""

    // Cached so confirmImport() can access the URI without the Screen passing it back
    private var selectedUri    : Uri?            = null
    private var selectedFormat : MigratorFormat? = null

    // ── File selected ─────────────────────────────────────────────────────────

    /**
     * Called by the Screen after the OpenDocument launcher returns a URI.
     * Reads the file just enough to get a note count (no decryption needed).
     */
    fun onFileSelected(
        format          : MigratorFormat,
        uri             : Uri,
        contentResolver : ContentResolver,
        displayName     : String?
    ) {
        selectedUri    = uri
        selectedFormat = format
        selectedFileName = displayName ?: uri.lastPathSegment ?: "file"

        viewModelScope.launch {
            _state.value = MigratorState.ReadingFile(format)
            try {
                val count = when (format) {
                    MigratorFormat.EVERNOTE -> importExportManager.countEvernoteNotes(contentResolver, uri)
                    MigratorFormat.KEEP     -> importExportManager.countKeepNotes(contentResolver, uri)
                    MigratorFormat.NOTION   -> importExportManager.countMarkdownNotes(contentResolver, uri, displayName ?: "")
                }
                _state.value = MigratorState.Preview(
                    format    = format,
                    fileName  = displayName ?: uri.lastPathSegment ?: "file",
                    noteCount = count
                )
            } catch (e: Exception) {
                _state.value = MigratorState.Error("Could not read file: ${e.message}")
            }
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Execute the actual import. Called when user taps "Import N Notes".
     * Notes are encrypted into the active vault during import — no separate
     * password step is needed (these formats are not vault-encrypted).
     */
    fun confirmImport(contentResolver: ContentResolver) {
        val uri    = selectedUri    ?: return
        val format = selectedFormat ?: return

        viewModelScope.launch {
            _state.value = MigratorState.Importing
            try {
                val result = when (format) {
                    MigratorFormat.EVERNOTE -> importExportManager.importEvernote(contentResolver, uri)
                    MigratorFormat.KEEP     -> importExportManager.importGoogleKeep(contentResolver, uri)
                    MigratorFormat.NOTION -> importExportManager.importMarkdown(contentResolver, uri, selectedFileName)
                }
                if (result.isSuccess) {
                    _state.value = MigratorState.Success(
                        notesImported  = result.notesImported,
                        foldersCreated = result.foldersImported
                    )
                } else {
                    _state.value = MigratorState.Error(result.error ?: "Import failed")
                }
            } catch (e: Exception) {
                _state.value = MigratorState.Error("Import failed: ${e.message}")
            }
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    /** Return to the format picker (Try Again / Choose Different App) */
    fun reset() {
        _state.value   = MigratorState.Idle
        selectedUri    = null
        selectedFormat = null
    }
}