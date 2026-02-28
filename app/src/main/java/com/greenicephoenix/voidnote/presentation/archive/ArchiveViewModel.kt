package com.greenicephoenix.voidnote.presentation.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Archive Screen.
 *
 * The archive is for notes the user wants to keep but not see on the
 * main screen — "out of sight, not deleted". Unlike trash, archived notes:
 * - Are NOT auto-deleted after 30 days
 * - Can be restored to the main notes list at any time
 * - Can be moved directly to trash without restoring first
 *
 * DATA FLOW:
 * noteRepository.getArchivedNotes() returns a Flow<List<Note>> that Room
 * updates automatically whenever any note's isArchived flag changes.
 * The ViewModel maps it into ArchiveUiState and exposes it as a StateFlow.
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    /**
     * UI state derived from the archived notes Flow.
     *
     * StateFlow.stateIn() converts the cold Room Flow into a hot StateFlow
     * that the Compose UI observes. WhileSubscribed(5000) keeps the Flow
     * active for 5 seconds after the last subscriber — this prevents
     * re-querying the DB on quick config changes like screen rotation.
     */
    val uiState: StateFlow<ArchiveUiState> = noteRepository
        .getArchivedNotes()
        .map { notes ->
            ArchiveUiState(
                archivedNotes = notes,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ArchiveUiState(isLoading = true)
        )

    /**
     * Restore a note from archive back to the main notes list.
     *
     * Calls toggleArchive() which flips isArchived = false.
     * The note reappears immediately on NotesListScreen because both
     * screens observe the same Room Flow — no refresh needed.
     */
    fun restoreNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.toggleArchive(noteId)
        }
    }

    /**
     * Move an archived note directly to trash.
     *
     * The note is removed from archive and placed in trash.
     * It will be auto-deleted after 30 days if not restored.
     */
    fun moveToTrash(noteId: String) {
        viewModelScope.launch {
            noteRepository.moveToTrash(noteId)
        }
    }
}

/**
 * UI State for Archive Screen.
 *
 * @param archivedNotes List of all currently archived notes.
 * @param isLoading     True while the initial database query is in flight.
 */
data class ArchiveUiState(
    val archivedNotes: List<Note> = emptyList(),
    val isLoading: Boolean = true
) {
    /** True when there are no archived notes to show. */
    val isEmpty: Boolean get() = !isLoading && archivedNotes.isEmpty()

    /** Count of archived notes for display in the top bar. */
    val count: Int get() = archivedNotes.size
}