package com.greenicephoenix.voidnote.presentation.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
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
 * ViewModel for Folders Screen
 */
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository
) : ViewModel() {

    // Dialog state
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog

    // New folder name
    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName

    // UI State
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

    /**
     * Show create folder dialog
     */
    fun showCreateDialog() {
        _newFolderName.value = ""
        _showCreateDialog.value = true
    }

    /**
     * Hide create folder dialog
     */
    fun hideCreateDialog() {
        _showCreateDialog.value = false
        _newFolderName.value = ""
    }

    /**
     * Update new folder name
     */
    fun onFolderNameChange(name: String) {
        _newFolderName.value = name
    }

    /**
     * Create new folder
     */
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

    /**
     * Delete folder
     */
    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            folderRepository.deleteFolder(folderId)
        }
    }

    /**
     * Rename folder
     */
    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            val folder = folderRepository.getFolderById(folderId)
            folder?.let {
                folderRepository.updateFolder(
                    it.copy(
                        name = newName,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}

/**
 * UI State for Folders Screen
 */
data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = true,
    val totalCount: Int = 0
)