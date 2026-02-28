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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * FoldersViewModel — manages the folder list screen.
 *
 * SPRINT 3 FIX:
 * confirmDeleteFolder() now uses trashNotesByFolder() instead of the old
 * moveNoteToFolder(null) loop. Notes go to trash, not root, so the user
 * can recover them. folderId is cleared inside the repository so restore
 * from trash always goes to the main list — no orphan risk.
 */
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog

    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName

    // Non-null = show delete confirmation for that folder
    private val _pendingDeleteFolder = MutableStateFlow<Folder?>(null)
    val pendingDeleteFolder: StateFlow<Folder?> = _pendingDeleteFolder

    val uiState: StateFlow<FoldersUiState> = combine(
        folderRepository.getAllFolders(),
        folderRepository.getFolderCount()
    ) { folders, count ->
        FoldersUiState(folders = folders, isLoading = false, totalCount = count)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FoldersUiState(isLoading = true)
    )

    // ── Create ────────────────────────────────────────────────────────────

    fun showCreateDialog() {
        _newFolderName.value = ""
        _showCreateDialog.value = true
    }

    fun hideCreateDialog() {
        _showCreateDialog.value = false
        _newFolderName.value = ""
    }

    fun onFolderNameChange(name: String) { _newFolderName.value = name }

    fun createFolder() {
        val name = _newFolderName.value.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            folderRepository.createFolder(
                Folder(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    createdAt = System.currentTimeMillis()
                )
            )
            hideCreateDialog()
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────

    fun requestDeleteFolder(folder: Folder) { _pendingDeleteFolder.value = folder }
    fun cancelDeleteFolder() { _pendingDeleteFolder.value = null }

    /**
     * Delete a folder from the folder list screen.
     *
     * WHAT HAPPENS:
     * 1. trashNotesByFolder() — all notes in the folder go to trash with
     *    folderId cleared. Single SQL query, atomic.
     * 2. deleteFolder() — folder row deleted.
     *
     * The user can visit TrashScreen to restore any note they want.
     * Notes are never lost silently.
     */
    fun confirmDeleteFolder() {
        val folder = _pendingDeleteFolder.value ?: return
        viewModelScope.launch {
            // Trash all notes in this folder first (clears their folderId)
            noteRepository.trashNotesByFolder(folder.id)
            // Then delete the folder
            folderRepository.deleteFolder(folder.id)
        }
        _pendingDeleteFolder.value = null
    }

    // ── Rename ────────────────────────────────────────────────────────────

    fun renameFolder(folderId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val folder = folderRepository.getFolderById(folderId) ?: return@launch
            folderRepository.updateFolder(
                folder.copy(name = trimmed, updatedAt = System.currentTimeMillis())
            )
        }
    }
}

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = true,
    val totalCount: Int = 0
)