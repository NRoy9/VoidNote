package com.greenicephoenix.voidnote.presentation.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * FoldersViewModel — manages the folder list screen.
 *
 * SPRINT 3 CHANGE:
 * Added NoteRepository to the constructor so that deleting a folder also
 * moves its notes to root (folderId = null) before removing the folder row.
 *
 * WHY DOES FOLDERSVIEWMODEL NEED NOTEREPOSITORY?
 * The FoldersScreen shows the full list of all folders. Each folder has a
 * delete (trash) icon. When the user taps that icon, we need to:
 *   1. Move all notes inside the folder to root level (folderId = null)
 *   2. Delete the folder
 * Step 1 requires NoteRepository. Without it, notes become orphaned — their
 * folderId points to a deleted folder and they disappear from every screen.
 *
 * This is the same logic as FolderNotesViewModel.deleteFolder(), centralised
 * so both screens produce the same safe behaviour.
 */
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository      // SPRINT 3: added for safe deletion
) : ViewModel() {

    // ── Dialog visibility ─────────────────────────────────────────────────
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog

    // ── New folder name input ─────────────────────────────────────────────
    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName

    // ── Delete confirmation — which folder is pending deletion? ───────────
    // null = no dialog shown. Non-null = confirmation dialog open for that folder.
    // WHY STORE THE WHOLE FOLDER OBJECT?
    // The confirmation dialog needs the folder name for its message
    // ("Delete 'Work Notes'?") and the ID to call deleteFolder().
    // Storing the object means one state, not two (pendingDeleteId + pendingDeleteName).
    private val _pendingDeleteFolder = MutableStateFlow<Folder?>(null)
    val pendingDeleteFolder: StateFlow<Folder?> = _pendingDeleteFolder

    // ── Main UI state ─────────────────────────────────────────────────────
    // combine() merges two Flows into one. Whenever either the folder list
    // or the count changes, a new FoldersUiState is emitted.
    // stateIn() converts the Flow to a StateFlow (always has a current value)
    // and keeps it alive while the screen is subscribed.
    val uiState: StateFlow<FoldersUiState> = combine(
        folderRepository.getAllFolders(),
        folderRepository.getFolderCount()
    ) { folders, count ->
        FoldersUiState(
            folders = folders,
            isLoading = false,
            totalCount = count
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FoldersUiState(isLoading = true)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE FOLDER
    // ─────────────────────────────────────────────────────────────────────────

    fun showCreateDialog() {
        _newFolderName.value = ""
        _showCreateDialog.value = true
    }

    fun hideCreateDialog() {
        _showCreateDialog.value = false
        _newFolderName.value = ""
    }

    fun onFolderNameChange(name: String) {
        _newFolderName.value = name
    }

    fun createFolder() {
        val name = _newFolderName.value.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val folder = Folder(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis()
            )
            folderRepository.createFolder(folder)
            hideCreateDialog()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE FOLDER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Request deletion — shows the confirmation dialog for this folder.
     *
     * We don't delete immediately because deletion is irreversible (even though
     * notes are preserved, the folder structure is gone). The confirmation
     * dialog also tells the user how many notes will be moved, which helps
     * them make an informed decision.
     */
    fun requestDeleteFolder(folder: Folder) {
        _pendingDeleteFolder.value = folder
    }

    /** Cancel the deletion — dismiss the dialog, no changes made. */
    fun cancelDeleteFolder() {
        _pendingDeleteFolder.value = null
    }

    /**
     * Confirm deletion — move notes to root, then delete the folder.
     *
     * OPERATION ORDER:
     * 1. .first() takes one snapshot of the notes Flow — we don't need
     *    a live subscription, just the current list.
     * 2. moveNoteToFolder(noteId, null) sets folderId = null, making the
     *    note appear in the main notes list (root level).
     * 3. deleteFolder() removes the folder row from the DB.
     *
     * If there are no notes in the folder, step 2 is skipped (empty forEach).
     * The folder is still deleted cleanly.
     */
    fun confirmDeleteFolder() {
        val folder = _pendingDeleteFolder.value ?: return

        viewModelScope.launch {
            // Step 1: Get all notes currently in this folder
            val notesInFolder = noteRepository.getNotesByFolder(folder.id).first()

            // Step 2: Move each note to root level
            notesInFolder.forEach { note ->
                noteRepository.moveNoteToFolder(note.id, null)
            }

            // Step 3: Delete the folder
            folderRepository.deleteFolder(folder.id)
        }

        // Dismiss the dialog immediately (the coroutine runs in background)
        _pendingDeleteFolder.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENAME FOLDER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rename a folder.
     *
     * Called directly (no confirmation needed — renaming is non-destructive).
     * The folder list will update reactively because getAllFolders() is a Flow.
     */
    fun renameFolder(folderId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            val folder = folderRepository.getFolderById(folderId) ?: return@launch
            folderRepository.updateFolder(
                folder.copy(
                    name = trimmed,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

/**
 * UI State for the Folders Screen.
 */
data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = true,
    val totalCount: Int = 0
)