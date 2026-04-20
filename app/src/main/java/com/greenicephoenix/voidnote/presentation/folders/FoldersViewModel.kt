package com.greenicephoenix.voidnote.presentation.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.manager.FolderLockManager
import com.greenicephoenix.voidnote.data.security.FolderPasswordManager
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
 * SPRINT 15 ADDITIONS:
 *
 * Unlock flow:
 * - User taps a password-protected folder → onFolderClick() checks isPasswordProtected()
 * - If protected and not yet unlocked → sets pendingUnlockFolder (shows password dialog)
 * - User enters password → submitUnlockPassword() verifies via FolderPasswordManager
 * - If correct → FolderLockManager.unlock() + navigate into folder
 * - If wrong → sets wrongPassword = true (dialog shows error state)
 *
 * FolderLockManager is a @Singleton so its unlock state is shared with
 * FolderNotesViewModel — both see the same unlocked set.
 */
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val noteRepository: NoteRepository,
    private val folderLockManager: FolderLockManager,       // Sprint 15
    private val folderPasswordManager: FolderPasswordManager // Sprint 15
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog

    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName

    private val _pendingDeleteFolder = MutableStateFlow<Folder?>(null)
    val pendingDeleteFolder: StateFlow<Folder?> = _pendingDeleteFolder

    // ── Sprint 15: Unlock dialog state ───────────────────────────────────────
    // Non-null = show the unlock password dialog for this folder
    private val _pendingUnlockFolder = MutableStateFlow<Folder?>(null)
    val pendingUnlockFolder: StateFlow<Folder?> = _pendingUnlockFolder

    // The text the user is typing into the unlock password field
    private val _unlockPasswordInput = MutableStateFlow("")
    val unlockPasswordInput: StateFlow<String> = _unlockPasswordInput

    // True when the user submitted a wrong password — shows error in dialog
    private val _wrongPassword = MutableStateFlow(false)
    val wrongPassword: StateFlow<Boolean> = _wrongPassword

    val uiState: StateFlow<FoldersUiState> = combine(
        folderRepository.getAllFolders(),
        folderRepository.getFolderCount()
    ) { folders, count ->
        FoldersUiState(folders = folders, isLoading = false, totalCount = count)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FoldersUiState(isLoading = true)
    )

    // ── Folder tap handling ───────────────────────────────────────────────────

    /**
     * Called when the user taps a folder card.
     *
     * Decision tree:
     * 1. Not password-protected → navigate immediately.
     * 2. Protected + already unlocked this session → navigate immediately.
     * 3. Protected + locked → show the unlock password dialog.
     *
     * @param folder        The tapped folder domain model.
     * @param onNavigate    Lambda that navigates to FolderNotesScreen with the folder ID.
     */
    fun onFolderClick(folder: Folder, onNavigate: (String) -> Unit) {
        when {
            // No password set — open directly
            !folder.isPasswordProtected() -> onNavigate(folder.id)

            // Password set but already unlocked this session — open directly
            folderLockManager.isUnlocked(folder.id) -> onNavigate(folder.id)

            // Password set and locked — show unlock dialog
            else -> {
                _pendingUnlockFolder.value = folder
                _unlockPasswordInput.value = ""
                _wrongPassword.value = false
            }
        }
    }

    fun onUnlockPasswordChange(input: String) {
        _unlockPasswordInput.value = input
        // Clear the error as soon as the user starts typing again
        if (_wrongPassword.value) _wrongPassword.value = false
    }

    fun dismissUnlockDialog() {
        _pendingUnlockFolder.value = null
        _unlockPasswordInput.value = ""
        _wrongPassword.value = false
    }

    /**
     * Verify the typed password and unlock the folder if correct.
     *
     * WHY A COROUTINE?
     * PBKDF2 with 100,000 iterations is intentionally slow (that's what makes
     * it secure). Running it on the main thread would freeze the UI for ~200ms.
     * viewModelScope.launch() dispatches to a background thread automatically
     * via Room's coroutine integration.
     *
     * @param onNavigate Lambda called with folderId on successful unlock.
     */
    fun submitUnlockPassword(onNavigate: (String) -> Unit) {
        val folder = _pendingUnlockFolder.value ?: return
        val password = _unlockPasswordInput.value

        viewModelScope.launch {
            val correct = folderPasswordManager.verifyPassword(
                password      = password,
                storedHashB64 = folder.passwordHash ?: return@launch,
                storedSaltB64 = folder.passwordSalt ?: return@launch
            )
            if (correct) {
                folderLockManager.unlock(folder.id)
                dismissUnlockDialog()
                onNavigate(folder.id)
            } else {
                _wrongPassword.value = true
            }
        }
    }

    // ── Create ────────────────────────────────────────────────────────────────

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
                    id        = UUID.randomUUID().toString(),
                    name      = name,
                    createdAt = System.currentTimeMillis()
                )
            )
            hideCreateDialog()
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun requestDeleteFolder(folder: Folder) { _pendingDeleteFolder.value = folder }
    fun cancelDeleteFolder() { _pendingDeleteFolder.value = null }

    fun confirmDeleteFolder() {
        val folder = _pendingDeleteFolder.value ?: return
        viewModelScope.launch {
            noteRepository.trashNotesByFolder(folder.id)
            folderRepository.deleteFolder(folder.id)
            // Also clear from unlock memory — folder no longer exists
            folderLockManager.lock(folder.id)
        }
        _pendingDeleteFolder.value = null
    }

    // ── Rename ────────────────────────────────────────────────────────────────

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