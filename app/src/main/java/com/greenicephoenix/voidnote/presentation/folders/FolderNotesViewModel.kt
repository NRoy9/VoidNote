package com.greenicephoenix.voidnote.presentation.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for Folder Notes Screen
 *
 * Handles:
 * - Loading folder details
 * - Loading notes in folder
 * - Creating new notes in folder
 */
@HiltViewModel
class FolderNotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderNotesUiState())
    val uiState: StateFlow<FolderNotesUiState> = _uiState.asStateFlow()

    private var currentFolderId: String = ""

    /**
     * Load folder and its notes
     */
    fun loadFolder(folderId: String) {
        currentFolderId = folderId

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load folder details
            val folder = folderRepository.getFolderById(folderId)

            // Load notes in this folder
            noteRepository.getNotesByFolder(folderId).collect { notes ->
                _uiState.value = _uiState.value.copy(
                    folderName = folder?.name ?: "Unknown Folder",
                    notes = notes,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Create new note in this folder
     */
    fun createNoteInFolder(folderId: String, onNavigateToEditor: (String) -> Unit) {
        viewModelScope.launch {
            val noteId = UUID.randomUUID().toString()

            // Create empty note in database with folderId
            val note = Note(
                id = noteId,
                title = "",
                content = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                folderId = folderId // IMPORTANT: Set folder ID here
            )

            // Insert into database
            noteRepository.insertNote(note, folderId = folderId)

            // Now navigate to editor to edit this note
            onNavigateToEditor(noteId)
        }
    }
}

/**
 * UI State for Folder Notes Screen
 */
data class FolderNotesUiState(
    val folderName: String = "",
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true
)