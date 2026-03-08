package com.greenicephoenix.voidnote.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.model.NoteSort
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
 * ViewModel for Trash Screen.
 *
 * SORT SUPPORT:
 * Same pattern as ArchiveViewModel — in-memory sort, resets to LAST_MODIFIED
 * on each visit. Trash is used infrequently so persistent sort isn't needed.
 *
 * The combine() now takes three flows: trashedNotes, isEmptyingTrash, noteSort.
 * Whenever any of the three changes, the UI state recomputes.
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _isEmptyingTrash = MutableStateFlow(false)

    private val _noteSort = MutableStateFlow(NoteSort.UPDATED_DESC)
    val noteSort: StateFlow<NoteSort> = _noteSort

    fun onSortSelected(sort: NoteSort) {
        _noteSort.value = sort
    }

    val uiState: StateFlow<TrashUiState> = combine(
        noteRepository.getTrashedNotes(),
        _isEmptyingTrash,
        _noteSort
    ) { trashedNotes, isEmptying, sort ->
        val sorted = when (sort) {
            NoteSort.UPDATED_DESC -> trashedNotes.sortedByDescending { it.updatedAt }
            NoteSort.CREATED_DESC  -> trashedNotes.sortedByDescending { it.createdAt }
            NoteSort.TITLE_ASC      -> trashedNotes.sortedBy      { it.title.lowercase() }
            NoteSort.TITLE_DESC      -> trashedNotes.sortedByDescending { it.title.lowercase() }
        }
        TrashUiState(
            trashedNotes    = sorted,
            isLoading       = false,
            isEmptyingTrash = isEmptying
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = TrashUiState(isLoading = true)
    )

    fun restoreNote(noteId: String) {
        viewModelScope.launch { noteRepository.restoreFromTrash(noteId) }
    }

    fun permanentlyDeleteNote(noteId: String) {
        viewModelScope.launch { noteRepository.deleteNotePermanently(noteId) }
    }

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

data class TrashUiState(
    val trashedNotes    : List<Note> = emptyList(),
    val isLoading       : Boolean    = true,
    val isEmptyingTrash : Boolean    = false
) {
    val isEmpty : Boolean get() = trashedNotes.isEmpty()
    val count   : Int     get() = trashedNotes.size
}