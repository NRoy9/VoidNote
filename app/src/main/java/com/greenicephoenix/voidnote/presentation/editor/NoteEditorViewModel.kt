package com.greenicephoenix.voidnote.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * ViewModel for Note Editor Screen
 *
 * Features:
 * - Auto-save with debouncing (waits 500ms after typing stops)
 * - Load existing note or create new one
 * - Track folder assignment
 * - Real-time save status
 * - Delete functionality (move to trash)
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository
) : ViewModel() {

    // Get noteId from navigation arguments
    private val noteId: String = savedStateHandle.get<String>("noteId") ?: "new"

    // Private mutable state
    private val _uiState = MutableStateFlow(NoteEditorUiState())

    // Public read-only state
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    // Job for debounced auto-save
    private var autoSaveJob: Job? = null

    // Current note ID (generated for new notes)
    private var currentNoteId: String = noteId

    // Track which folder this note belongs to (if any)
    private var currentFolderId: String? = null

    // ✅ NEW: Track if note is being deleted
    private var isDeleting = false

    init {
        loadNote()
    }

    /**
     * Load note if editing existing, or create blank note
     */
    private fun loadNote() {
        if (noteId == "new") {
            // New note - generate ID and set as new
            currentNoteId = UUID.randomUUID().toString()
            _uiState.value = _uiState.value.copy(
                title = "",
                content = "",
                isNewNote = true,
                isLoading = false
            )
        } else {
            // Load existing note from database
            viewModelScope.launch {
                val note = noteRepository.getNoteById(noteId)
                if (note != null) {
                    currentNoteId = note.id
                    currentFolderId = note.folderId

                    _uiState.value = _uiState.value.copy(
                        title = note.title,
                        content = note.content,
                        isPinned = note.isPinned,
                        isArchived = note.isArchived,
                        tags = note.tags,
                        isNewNote = false,
                        isLoading = false
                    )
                } else {
                    // Note not found - treat as new
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
     * Set the folder for this note (called when creating from folder view)
     */
    fun setFolder(folderId: String?) {
        currentFolderId = folderId
    }

    /**
     * Update note title
     */
    fun onTitleChange(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle)
        scheduleAutoSave()
    }

    /**
     * Update note content
     */
    fun onContentChange(newContent: String) {
        _uiState.value = _uiState.value.copy(content = newContent)
        scheduleAutoSave()
    }

    /**
     * Toggle bold formatting
     */
    fun toggleBold() {
        _uiState.value = _uiState.value.copy(
            isBoldActive = !_uiState.value.isBoldActive
        )
    }

    /**
     * Toggle italic formatting
     */
    fun toggleItalic() {
        _uiState.value = _uiState.value.copy(
            isItalicActive = !_uiState.value.isItalicActive
        )
    }

    /**
     * Toggle underline formatting
     */
    fun toggleUnderline() {
        _uiState.value = _uiState.value.copy(
            isUnderlineActive = !_uiState.value.isUnderlineActive
        )
    }

    /**
     * Schedule auto-save with debouncing
     * Waits 500ms after user stops typing before saving
     * ✅ FIXED: Don't schedule if deleting
     */
    private fun scheduleAutoSave() {
        // ✅ Don't auto-save if we're deleting the note
        if (isDeleting) {
            return
        }

        // Cancel previous save job
        autoSaveJob?.cancel()

        // Schedule new save job
        autoSaveJob = viewModelScope.launch {
            delay(500) // Wait 500ms
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
            createdAt = if (state.isNewNote) System.currentTimeMillis() else 0L,
            updatedAt = System.currentTimeMillis(),
            isPinned = state.isPinned,
            isArchived = state.isArchived,
            isTrashed = false,
            tags = state.tags,
            folderId = currentFolderId
        )

        if (state.isNewNote) {
            // Insert new note
            noteRepository.insertNote(note, folderId = currentFolderId)
            _uiState.value = _uiState.value.copy(isNewNote = false)
        } else {
            // Update existing note
            noteRepository.updateNote(note, folderId = currentFolderId)
        }

        // Update last saved time
        _uiState.value = _uiState.value.copy(
            lastSaved = System.currentTimeMillis(),
            isSaving = false
        )
    }

    /**
     * Force save immediately (when user leaves screen)
     * ✅ FIXED: Don't save if note is being deleted
     */
    fun forceSave() {
        // ✅ Don't save if we're deleting the note
        if (isDeleting) {
            android.util.Log.d("NoteEditor", "Skipping forceSave - note is being deleted")
            return
        }

        autoSaveJob?.cancel()
        viewModelScope.launch {
            saveNote()
        }
    }

    /**
     * ✅ FIXED: Delete current note (move to trash)
     * Cancels auto-save to prevent overwriting trash status
     */
    fun deleteNote() {
        viewModelScope.launch {
            // ✅ Set deletion flag to prevent auto-save
            isDeleting = true

            // ✅ Cancel any pending auto-save
            autoSaveJob?.cancel()

            // First, make sure the note is saved before deleting (if it's a new note)
            if (_uiState.value.isNewNote &&
                (_uiState.value.title.isNotBlank() || _uiState.value.content.isNotBlank())) {
                // Save the note first (without auto-save interference)
                saveNote()
            }

            // Small delay to ensure save completes
            kotlinx.coroutines.delay(100)

            // Now move to trash
            noteRepository.moveToTrash(currentNoteId)

            android.util.Log.d("NoteEditor", "Note $currentNoteId moved to trash")
        }
    }

    /**
     * Pin/unpin note
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
     * Add a tag to the note
     */
    fun addTag(tag: String) {
        val trimmedTag = tag.trim()
        if (trimmedTag.isBlank()) return

        val currentTags = _uiState.value.tags
        if (trimmedTag !in currentTags) {
            _uiState.value = _uiState.value.copy(
                tags = currentTags + trimmedTag
            )
            scheduleAutoSave()
        }
    }

    /**
     * Remove a tag from the note
     */
    fun removeTag(tag: String) {
        _uiState.value = _uiState.value.copy(
            tags = _uiState.value.tags - tag
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
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val tags: List<String> = emptyList(),
    val isNewNote: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val lastSaved: Long = 0L,

    // Formatting states
    val isBoldActive: Boolean = false,
    val isItalicActive: Boolean = false,
    val isUnderlineActive: Boolean = false
)