package com.greenicephoenix.voidnote.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Trash Screen
 *
 * Manages trashed notes with restore and permanent delete capabilities
 *
 * Features:
 * - Display all trashed notes
 * - Restore individual notes
 * - Permanently delete individual notes
 * - Empty entire trash (delete all)
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    // Loading state for empty trash operation
    private val _isEmptyingTrash = MutableStateFlow(false)

    /**
     * UI State - Combines trashed notes and loading state
     */
    val uiState: StateFlow<TrashUiState> = combine(
        noteRepository.getTrashedNotes(),
        _isEmptyingTrash
    ) { trashedNotes, isEmptying ->
        TrashUiState(
            trashedNotes = trashedNotes,
            isLoading = false,
            isEmptyingTrash = isEmptying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrashUiState(isLoading = true)
    )

    /**
     * Restore a note from trash
     * Moves note back to main notes list
     */
    fun restoreNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.restoreFromTrash(noteId)
        }
    }

    /**
     * Permanently delete a note
     * This action cannot be undone!
     */
    fun permanentlyDeleteNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.deleteNotePermanently(noteId)
        }
    }

    /**
     * Empty entire trash
     * Permanently deletes ALL trashed notes
     * Shows loading state during operation
     */
    fun emptyTrash() {
        viewModelScope.launch {
            _isEmptyingTrash.value = true
            try {
                noteRepository.emptyTrash()
            } finally {
                _isEmptyingTrash.value = false
            }
        }
    }
}

/**
 * UI State for Trash Screen
 *
 * @param trashedNotes List of notes in trash
 * @param isLoading Initial loading state
 * @param isEmptyingTrash Loading state for empty trash operation
 */
data class TrashUiState(
    val trashedNotes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
    val isEmptyingTrash: Boolean = false
) {
    /**
     * Check if trash is empty
     */
    val isEmpty: Boolean = trashedNotes.isEmpty()

    /**
     * Get count of trashed notes
     */
    val count: Int = trashedNotes.size
}