package com.greenicephoenix.voidnote.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.greenicephoenix.voidnote.domain.model.FormatType

/**
 * ViewModel for Note Editor Screen
 *
 * Clean, simplified version focused on core functionality:
 * - Auto-save with debouncing
 * - Load/create notes
 * - Tag management
 * - Pin/delete operations
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository
) : ViewModel() {

    // Get noteId from navigation
    private val noteId: String = savedStateHandle.get<String>("noteId") ?: "new"

    // UI state
    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    // Auto-save job
    private var autoSaveJob: Job? = null

    // Current note ID
    private var currentNoteId: String = noteId

    // Folder assignment
    private var currentFolderId: String? = null

    // Deletion flag
    private var isDeleting = false

    init {
        loadNote()
    }

    /**
     * Load existing note or create new one
     */
    private fun loadNote() {
        if (noteId == "new") {
            // New note
            currentNoteId = UUID.randomUUID().toString()
            _uiState.value = _uiState.value.copy(
                title = "",
                content = "",
                isNewNote = true,
                isLoading = false
            )

        } else {
            // Load existing note
            viewModelScope.launch {
                val note = noteRepository.getNoteById(noteId)
                if (note != null) {
                    currentNoteId = note.id
                    currentFolderId = note.folderId

                    _uiState.value = _uiState.value.copy(
                        title = note.title,
                        content = note.content,
                        contentFormats = note.contentFormats, // ✅ RESTORE FORMATTING
                        isPinned = note.isPinned,
                        isArchived = note.isArchived,
                        tags = note.tags,
                        isNewNote = false,
                        isLoading = false
                    )
                } else {
                    // Not found, treat as new
                    currentNoteId = UUID.randomUUID().toString()
                    _uiState.value = _uiState.value.copy(
                        isNewNote = true,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Set folder for this note
     */
    fun setFolder(folderId: String?) {
        currentFolderId = folderId
    }

    /**
     * Update title
     */
    fun onTitleChange(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle)
        scheduleAutoSave()
    }

    /**
     * Update content and apply active formatting to new characters
     */
    fun onContentChange(newContent: String) {
        val oldContent = _uiState.value.content
        val oldFormats = _uiState.value.contentFormats

        // Check if text was added
        if (newContent.length > oldContent.length) {
            val insertPos = findInsertPosition(oldContent, newContent)
            val insertLength = newContent.length - oldContent.length

            // Apply active formatting to newly inserted text
            var newFormats = adjustFormatsForTextChange(oldFormats, oldContent, newContent)

            if (insertPos >= 0 && insertLength > 0) {
                val insertEnd = insertPos + insertLength

                if (_uiState.value.activeBold) {
                    newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.BOLD)
                }
                if (_uiState.value.activeItalic) {
                    newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.ITALIC)
                }
                if (_uiState.value.activeUnderline) {
                    newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.UNDERLINE)
                }
                _uiState.value.activeHeading?.let { heading ->
                    newFormats = addFormat(newFormats, insertPos, insertEnd, heading)
                }
            }

            _uiState.value = _uiState.value.copy(
                content = newContent,
                contentFormats = newFormats
            )
        } else {
            // Text was deleted or replaced
            val newFormats = adjustFormatsForTextChange(oldFormats, oldContent, newContent)
            _uiState.value = _uiState.value.copy(
                content = newContent,
                contentFormats = newFormats
            )
        }

        scheduleAutoSave()
    }

    /**
     * Find where text was inserted
     */
    private fun findInsertPosition(oldText: String, newText: String): Int {
        // Find first difference
        var i = 0
        while (i < oldText.length && i < newText.length && oldText[i] == newText[i]) {
            i++
        }
        return i
    }

    /**
     * Adjust formatting ranges when text changes
     */
    private fun adjustFormatsForTextChange(
        formats: List<FormatRange>,
        oldText: String,
        newText: String
    ): List<FormatRange> {
        val lengthDiff = newText.length - oldText.length

        if (lengthDiff == 0) return formats

        val changePos = findInsertPosition(oldText, newText)

        return formats.mapNotNull { range ->
            when {
                // Format is entirely before the change - keep as is
                range.end <= changePos -> range

                // Format starts after the change - shift it
                range.start >= changePos -> FormatRange(
                    start = (range.start + lengthDiff).coerceAtLeast(0),
                    end = (range.end + lengthDiff).coerceAtLeast(range.start + 1),
                    type = range.type
                )

                // Format spans the change - extend it
                range.start < changePos && range.end > changePos -> FormatRange(
                    start = range.start,
                    end = (range.end + lengthDiff).coerceAtLeast(range.start + 1),
                    type = range.type
                )

                else -> null
            }
        }.filter { it.start < newText.length && it.end <= newText.length && it.start < it.end }
    }

    /**
     * Schedule auto-save with debouncing
     */
    private fun scheduleAutoSave() {
        if (isDeleting) return

        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500)
            saveNote()
        }
    }

    /**
     * Save note to database
     */
    private suspend fun saveNote() {
        val state = _uiState.value

        // Don't save empty notes
        if (state.title.isBlank() && state.content.isBlank()) {
            return
        }

        val note = Note(
            id = currentNoteId,
            title = state.title,
            content = state.content,
            contentFormats = state.contentFormats,
            createdAt = if (state.isNewNote) System.currentTimeMillis() else 0L,
            updatedAt = System.currentTimeMillis(),
            isPinned = state.isPinned,
            isArchived = state.isArchived,
            isTrashed = false,
            tags = state.tags,
            folderId = currentFolderId
        )

        if (state.isNewNote) {
            noteRepository.insertNote(note, folderId = currentFolderId)
            _uiState.value = _uiState.value.copy(isNewNote = false)
        } else {
            noteRepository.updateNote(note, folderId = currentFolderId)
        }

        _uiState.value = _uiState.value.copy(
            lastSaved = System.currentTimeMillis(),
            isSaving = false
        )
    }

    /**
     * Force save immediately
     */
    fun forceSave() {
        if (isDeleting) {
            android.util.Log.d("NoteEditor", "Skipping forceSave - deleting")
            return
        }

        autoSaveJob?.cancel()
        viewModelScope.launch {
            saveNote()
        }
    }

    /**
     * Delete note (move to trash)
     */
    fun deleteNote() {
        viewModelScope.launch {
            isDeleting = true
            autoSaveJob?.cancel()

            // Save first if new note
            if (_uiState.value.isNewNote &&
                (_uiState.value.title.isNotBlank() || _uiState.value.content.isNotBlank())) {
                saveNote()
            }

            delay(100)
            noteRepository.moveToTrash(currentNoteId)
            android.util.Log.d("NoteEditor", "Note $currentNoteId moved to trash")
        }
    }

    /**
     * Toggle pin
     */
    fun togglePin() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPinned = !_uiState.value.isPinned
            )
            noteRepository.togglePin(currentNoteId)
        }
    }

    /**
     * Archive note
     */
    fun archiveNote() {
        viewModelScope.launch {
            noteRepository.toggleArchive(currentNoteId)
        }
    }

    /**
     * Add tag
     */
    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return

        val current = _uiState.value.tags
        if (trimmed !in current && current.size < 5) {
            _uiState.value = _uiState.value.copy(
                tags = current + trimmed
            )
            scheduleAutoSave()
        }
    }

    /**
     * Remove tag
     */
    fun removeTag(tag: String) {
        _uiState.value = _uiState.value.copy(
            tags = _uiState.value.tags - tag
        )
        scheduleAutoSave()
    }

    /**
     * Apply formatting to selection
     */
    fun applyFormatting(start: Int, end: Int, type: FormatType) {
        val current = _uiState.value.contentFormats

        val hasFormat = hasFormat(current, start, end, type)

        val newFormats = if (hasFormat) {
            removeFormat(current, start, end, type)
        } else {
            addFormat(current, start, end, type)
        }

        _uiState.value = _uiState.value.copy(contentFormats = newFormats)
        scheduleAutoSave()
    }

    /**
     * Toggle active formatting for new text
     */
    fun toggleActiveBold() {
        _uiState.value = _uiState.value.copy(
            activeBold = !_uiState.value.activeBold
        )
    }

    fun toggleActiveItalic() {
        _uiState.value = _uiState.value.copy(
            activeItalic = !_uiState.value.activeItalic
        )
    }

    fun toggleActiveUnderline() {
        _uiState.value = _uiState.value.copy(
            activeUnderline = !_uiState.value.activeUnderline
        )
    }

    fun setActiveHeading(type: FormatType?) {
        _uiState.value = _uiState.value.copy(activeHeading = type)
    }

    /**
     * Clear all formatting
     */
    fun clearAllFormatting() {
        _uiState.value = _uiState.value.copy(
            contentFormats = emptyList(),
            activeBold = false,
            activeItalic = false,
            activeUnderline = false,
            activeHeading = null
        )
        scheduleAutoSave()
    }
}

/**
 * UI State for Note Editor
 */
data class NoteEditorUiState(
    val title: String = "",
    val content: String = "",
    val contentFormats: List<FormatRange> = emptyList(),  // ✅ NEW: Store formatting
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val tags: List<String> = emptyList(),
    val isNewNote: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val lastSaved: Long = 0L,

    // Active formatting for new text
    val activeBold: Boolean = false,
    val activeItalic: Boolean = false,
    val activeUnderline: Boolean = false,
    val activeHeading: FormatType? = null
)