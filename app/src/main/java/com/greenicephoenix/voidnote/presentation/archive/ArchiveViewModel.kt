package com.greenicephoenix.voidnote.presentation.archive

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
 * ViewModel for Archive Screen.
 *
 * SORT SUPPORT (Sprint 8 polish):
 * Added in-memory sort so the user can order archived notes by last modified,
 * created date, or title — same sort options as the main notes list.
 * Sort is NOT persisted (in-memory only) — resets to LAST_MODIFIED on each
 * visit to the archive. This is intentional: archive is used infrequently
 * and a persistent sort adds complexity without meaningful benefit.
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    // ── Sort state ────────────────────────────────────────────────────────────
    // Defaults to LAST_MODIFIED — most recently touched note at the top.
    private val _noteSort = MutableStateFlow(NoteSort.UPDATED_DESC)
    val noteSort: StateFlow<NoteSort> = _noteSort

    fun onSortSelected(sort: NoteSort) {
        _noteSort.value = sort
    }

    // ── UI state ──────────────────────────────────────────────────────────────
    // Combines the archived notes Flow with the current sort selection.
    // `combine` re-emits whenever either source changes (new note archived,
    // or user picks a different sort).
    val uiState: StateFlow<ArchiveUiState> = combine(
        noteRepository.getArchivedNotes(),
        _noteSort
    ) { notes, sort ->
        val sorted = when (sort) {
            NoteSort.UPDATED_DESC -> notes.sortedByDescending { it.updatedAt }
            NoteSort.CREATED_DESC  -> notes.sortedByDescending { it.createdAt }
            NoteSort.TITLE_ASC      -> notes.sortedBy      { it.title.lowercase() }
            NoteSort.TITLE_DESC     -> notes.sortedByDescending { it.title.lowercase() }
        }
        ArchiveUiState(archivedNotes = sorted, isLoading = false)
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = ArchiveUiState(isLoading = true)
    )

    /** Restore a note from archive back to the main notes list. */
    fun restoreNote(noteId: String) {
        viewModelScope.launch { noteRepository.toggleArchive(noteId) }
    }

    /** Move an archived note directly to trash without unarchiving first. */
    fun moveToTrash(noteId: String) {
        viewModelScope.launch { noteRepository.moveToTrash(noteId) }
    }
}

/**
 * UI State for Archive Screen.
 */
data class ArchiveUiState(
    val archivedNotes : List<Note> = emptyList(),
    val isLoading     : Boolean    = true
) {
    val isEmpty : Boolean get() = !isLoading && archivedNotes.isEmpty()
    val count   : Int     get() = archivedNotes.size
}