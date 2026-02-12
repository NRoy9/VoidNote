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
 * NOW SUPPORTS FOLDERS!
 *
 * Features:
 * - Auto-save with debouncing (waits 500ms after typing stops)
 * - Load existing note or create new one
 * - Track folder assignment
 * - Real-time save status
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
                    currentFolderId = note.folderId // IMPORTANT: Store the folder ID

                    // Add debug log
                    android.util.Log.d("NoteEditor", "Loaded note ${note.id} with folderId: ${note.folderId}")

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
     */
    private fun scheduleAutoSave() {
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
     * NOW INCLUDES FOLDER ID!
     */
    private suspend fun saveNote() {
        val state = _uiState.value

        // Don't save empty notes
        if (state.title.isBlank() && state.content.isBlank()) {
            return
        }

        // Add debug log
        android.util.Log.d("NoteEditor", "Saving note $currentNoteId with folderId: $currentFolderId")

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
            folderId = currentFolderId // IMPORTANT: Include folder ID
        )

        // More debug
        android.util.Log.d("NoteEditor", "Note object created with folderId: ${note.folderId}")

        if (state.isNewNote) {
            // Insert new note WITH folder ID
            noteRepository.insertNote(note, folderId = currentFolderId)
            android.util.Log.d("NoteEditor", "Inserted new note")
            _uiState.value = _uiState.value.copy(isNewNote = false)
        } else {
            // Update existing note WITH folder ID
            noteRepository.updateNote(note, folderId = currentFolderId)
            android.util.Log.d("NoteEditor", "Updated existing note")
        }

        // Update last saved time
        _uiState.value = _uiState.value.copy(
            lastSaved = System.currentTimeMillis(),
            isSaving = false
        )
    }

    /**
     * Force save immediately (when user leaves screen)
     */
    fun forceSave() {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            saveNote()
        }
    }

    /**
     * Delete current note
     */
    fun deleteNote() {
        viewModelScope.launch {
            noteRepository.moveToTrash(currentNoteId)
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