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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * FolderNotesViewModel — manages all state for the folder notes screen.
 *
 * SPRINT 3 FIXES:
 *
 * Fix #1 — Live rename (from previous fix):
 * Uses combine(observeFolder, getNotesByFolder) so the top bar title
 * updates immediately when the user renames the folder.
 *
 * Fix #2 — Delete always goes to trash (this session):
 * confirmDelete() now calls trashNotesByFolder() — notes go to the trash
 * screen and can be recovered. The "permanently delete" option is removed.
 * This matches Android conventions: nothing should vanish without going
 * through trash first.
 *
 * The deleteNotes Boolean parameter is gone. The only question when deleting
 * a folder is now "are you sure?" — not "what should happen to notes?".
 */
@HiltViewModel
class FolderNotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderNotesUiState())
    val uiState: StateFlow<FolderNotesUiState> = _uiState.asStateFlow()

    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()

    private val _renameText = MutableStateFlow("")
    val renameText: StateFlow<String> = _renameText.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private var currentFolderId: String = ""

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Observe folder details and its notes reactively using combine().
     * When the folder is renamed, observeFolder() re-emits → combine() runs
     * → folderName in uiState updates → top bar title updates live.
     */
    fun loadFolder(folderId: String) {
        currentFolderId = folderId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            combine(
                folderRepository.observeFolder(folderId),
                noteRepository.getNotesByFolder(folderId)
            ) { folder, notes ->
                FolderNotesUiState(
                    folderName = folder?.name ?: _uiState.value.folderName,
                    notes = notes,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────

    fun createNoteInFolder(folderId: String, onNavigateToEditor: (String) -> Unit) {
        viewModelScope.launch {
            val noteId = UUID.randomUUID().toString()
            noteRepository.insertNote(
                Note(
                    id = noteId,
                    title = "",
                    content = "",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    folderId = folderId
                ),
                folderId = folderId
            )
            onNavigateToEditor(noteId)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENAME
    // ─────────────────────────────────────────────────────────────────────────

    fun openRenameDialog() {
        _renameText.value = _uiState.value.folderName
        _showRenameDialog.value = true
    }

    fun onRenameTextChange(text: String) {
        _renameText.value = text
    }

    fun dismissRenameDialog() {
        _showRenameDialog.value = false
        _renameText.value = ""
    }

    fun confirmRename() {
        val newName = _renameText.value.trim()
        if (newName.isBlank()) return
        viewModelScope.launch {
            val folder = folderRepository.getFolderById(currentFolderId) ?: return@launch
            folderRepository.updateFolder(
                folder.copy(name = newName, updatedAt = System.currentTimeMillis())
            )
        }
        dismissRenameDialog()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────

    fun openDeleteDialog() { _showDeleteDialog.value = true }
    fun dismissDeleteDialog() { _showDeleteDialog.value = false }

    /**
     * Delete this folder. All notes inside go to trash (recoverable).
     *
     * WHAT HAPPENS:
     * 1. trashNotesByFolder() sends every note in this folder to trash in a
     *    single SQL UPDATE. Their folderId is cleared (null) so restoring
     *    from trash puts them in the main list — no orphan risk.
     * 2. The folder row is deleted.
     * 3. The screen navigates back (the folder no longer exists).
     *
     * NOTES GO TO TRASH, NOT PERMANENT DELETE:
     * This is intentional. The user deleted a folder, not necessarily the
     * notes inside. They can go to TrashScreen and restore any note they
     * want. Permanently deleting notes from a folder delete would be a
     * data-loss disaster with no recovery path.
     *
     * @param onNavigateBack Called after DB operations complete.
     */
    fun confirmDelete(onNavigateBack: () -> Unit) {
        viewModelScope.launch {
            // Step 1: trash all notes in this folder (clears their folderId)
            noteRepository.trashNotesByFolder(currentFolderId)

            // Step 2: delete the folder itself
            folderRepository.deleteFolder(currentFolderId)

            // Step 3: leave the screen
            onNavigateBack()
        }
        dismissDeleteDialog()
    }
}

data class FolderNotesUiState(
    val folderName: String = "",
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true
)