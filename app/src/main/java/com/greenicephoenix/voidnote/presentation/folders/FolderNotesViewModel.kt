package com.greenicephoenix.voidnote.presentation.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.manager.FolderLockManager
import com.greenicephoenix.voidnote.data.security.FolderPasswordManager
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
 * SPRINT 15 ADDITIONS:
 *
 * Password management (from the ⋮ menu inside an open folder):
 * - setPassword()       — hash + salt a new password, save to DB
 * - changePassword()    — verify old password first, then set new one
 * - removePassword()    — verify current password, then clear hash + salt
 * - lockFolder()        — remove this folder from FolderLockManager, navigate back
 *
 * Dialog state:
 * - _showSetPasswordSheet   — bottom sheet for setting a new password
 * - _showChangePasswordSheet — bottom sheet for changing existing password
 * - _showRemovePasswordSheet — bottom sheet for removing password (requires current password)
 * - _passwordError           — error message shown in the active sheet
 *
 * WHY VERIFY BEFORE REMOVE/CHANGE?
 * Someone who grabs an unlocked phone shouldn't be able to silently remove
 * the password. Requiring re-entry for destructive operations is standard
 * security practice (same pattern as Google/Apple account password changes).
 */
@HiltViewModel
class FolderNotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val folderLockManager: FolderLockManager,        // Sprint 15
    private val folderPasswordManager: FolderPasswordManager  // Sprint 15
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderNotesUiState())
    val uiState: StateFlow<FolderNotesUiState> = _uiState.asStateFlow()

    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()

    private val _renameText = MutableStateFlow("")
    val renameText: StateFlow<String> = _renameText.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    // ── Sprint 15: Password sheet visibility ─────────────────────────────────
    private val _showSetPasswordSheet = MutableStateFlow(false)
    val showSetPasswordSheet: StateFlow<Boolean> = _showSetPasswordSheet.asStateFlow()

    private val _showChangePasswordSheet = MutableStateFlow(false)
    val showChangePasswordSheet: StateFlow<Boolean> = _showChangePasswordSheet.asStateFlow()

    private val _showRemovePasswordSheet = MutableStateFlow(false)
    val showRemovePasswordSheet: StateFlow<Boolean> = _showRemovePasswordSheet.asStateFlow()

    // Error message shown inside whichever sheet is active; null = no error
    private val _passwordError = MutableStateFlow<String?>(null)
    val passwordError: StateFlow<String?> = _passwordError.asStateFlow()

    private var currentFolderId: String = ""

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadFolder(folderId: String) {
        currentFolderId = folderId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            combine(
                folderRepository.observeFolder(folderId),
                noteRepository.getNotesByFolder(folderId)
            ) { folder, notes ->
                FolderNotesUiState(
                    folderName          = folder?.name ?: _uiState.value.folderName,
                    isPasswordProtected = folder?.isPasswordProtected() ?: false, // Sprint 15
                    notes               = notes,
                    isLoading           = false
                )
            }.collect { _uiState.value = it }
        }
    }

    // ── Create ────────────────────────────────────────────────────────────────

    fun createNoteInFolder(folderId: String, onNavigateToEditor: (String) -> Unit) {
        viewModelScope.launch {
            val noteId = UUID.randomUUID().toString()
            noteRepository.insertNote(
                Note(
                    id        = noteId,
                    title     = "",
                    content   = "",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    folderId  = folderId
                ),
                folderId = folderId
            )
            onNavigateToEditor(noteId)
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    fun openRenameDialog() {
        _renameText.value = _uiState.value.folderName
        _showRenameDialog.value = true
    }

    fun onRenameTextChange(text: String) { _renameText.value = text }

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

    // ── Delete ────────────────────────────────────────────────────────────────

    fun openDeleteDialog() { _showDeleteDialog.value = true }
    fun dismissDeleteDialog() { _showDeleteDialog.value = false }

    fun confirmDelete(onNavigateBack: () -> Unit) {
        viewModelScope.launch {
            noteRepository.trashNotesByFolder(currentFolderId)
            folderRepository.deleteFolder(currentFolderId)
            folderLockManager.lock(currentFolderId)
            onNavigateBack()
        }
        dismissDeleteDialog()
    }

    // ── Sprint 15: Password management ───────────────────────────────────────

    /**
     * Open the "Set Password" sheet (only for folders with no password yet).
     */
    fun openSetPasswordSheet() {
        _passwordError.value = null
        _showSetPasswordSheet.value = true
    }

    fun dismissSetPasswordSheet() {
        _showSetPasswordSheet.value = false
        _passwordError.value = null
    }

    /**
     * Hash and store a new password for this folder.
     * Called from the Set Password bottom sheet.
     *
     * @param newPassword     The password the user typed.
     * @param confirmPassword The confirmation field — must match.
     */
    fun setPassword(newPassword: String, confirmPassword: String) {
        if (newPassword.length < 4) {
            _passwordError.value = "Password must be at least 4 characters"
            return
        }
        if (newPassword != confirmPassword) {
            _passwordError.value = "Passwords don't match"
            return
        }
        viewModelScope.launch {
            val folder = folderRepository.getFolderById(currentFolderId) ?: return@launch
            val salt = folderPasswordManager.generateSalt()
            val hash = folderPasswordManager.hashPassword(newPassword, salt)
            folderRepository.updateFolder(
                folder.copy(
                    passwordHash = hash,
                    passwordSalt = salt,
                    updatedAt    = System.currentTimeMillis()
                )
            )
            // Mark as unlocked so the user isn't immediately prompted again
            folderLockManager.unlock(currentFolderId)
            dismissSetPasswordSheet()
        }
    }

    /**
     * Open the "Change Password" sheet (only for folders that already have a password).
     */
    fun openChangePasswordSheet() {
        _passwordError.value = null
        _showChangePasswordSheet.value = true
    }

    fun dismissChangePasswordSheet() {
        _showChangePasswordSheet.value = false
        _passwordError.value = null
    }

    /**
     * Verify the current password, then replace it with the new one.
     *
     * @param currentPassword The user's existing folder password.
     * @param newPassword     The new password to set.
     * @param confirmPassword Must match newPassword.
     */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        if (newPassword.length < 4) {
            _passwordError.value = "New password must be at least 4 characters"
            return
        }
        if (newPassword != confirmPassword) {
            _passwordError.value = "New passwords don't match"
            return
        }
        viewModelScope.launch {
            val folder = folderRepository.getFolderById(currentFolderId) ?: return@launch
            // Must verify current password before allowing change
            val correct = folderPasswordManager.verifyPassword(
                password      = currentPassword,
                storedHashB64 = folder.passwordHash ?: return@launch,
                storedSaltB64 = folder.passwordSalt ?: return@launch
            )
            if (!correct) {
                _passwordError.value = "Current password is incorrect"
                return@launch
            }
            val newSalt = folderPasswordManager.generateSalt()
            val newHash = folderPasswordManager.hashPassword(newPassword, newSalt)
            folderRepository.updateFolder(
                folder.copy(
                    passwordHash = newHash,
                    passwordSalt = newSalt,
                    updatedAt    = System.currentTimeMillis()
                )
            )
            dismissChangePasswordSheet()
        }
    }

    /**
     * Open the "Remove Password" sheet.
     * Requires the user to enter the current password — prevents silent removal.
     */
    fun openRemovePasswordSheet() {
        _passwordError.value = null
        _showRemovePasswordSheet.value = true
    }

    fun dismissRemovePasswordSheet() {
        _showRemovePasswordSheet.value = false
        _passwordError.value = null
    }

    /**
     * Verify the current password, then clear passwordHash + passwordSalt.
     * After this the folder is unprotected and opens without a prompt.
     */
    fun removePassword(currentPassword: String) {
        viewModelScope.launch {
            val folder = folderRepository.getFolderById(currentFolderId) ?: return@launch
            val correct = folderPasswordManager.verifyPassword(
                password      = currentPassword,
                storedHashB64 = folder.passwordHash ?: return@launch,
                storedSaltB64 = folder.passwordSalt ?: return@launch
            )
            if (!correct) {
                _passwordError.value = "Incorrect password"
                return@launch
            }
            folderRepository.updateFolder(
                folder.copy(
                    passwordHash = null,
                    passwordSalt = null,
                    updatedAt    = System.currentTimeMillis()
                )
            )
            // Folder is now unprotected — lock state irrelevant, clean up anyway
            folderLockManager.lock(currentFolderId)
            dismissRemovePasswordSheet()
        }
    }

    /**
     * Immediately re-lock this folder and navigate back to the folder list.
     * The user will need to enter the password again to re-open it.
     */
    fun lockFolder(onNavigateBack: () -> Unit) {
        folderLockManager.lock(currentFolderId)
        onNavigateBack()
    }

    // ── Note actions ──────────────────────────────────────────────────────────

    fun togglePin(noteId: String) {
        viewModelScope.launch { noteRepository.togglePin(noteId) }
    }

    fun archiveNote(noteId: String) {
        viewModelScope.launch { noteRepository.toggleArchive(noteId) }
    }

    fun undoArchive(noteId: String) {
        viewModelScope.launch { noteRepository.toggleArchive(noteId) }
    }

    fun moveToTrash(noteId: String) {
        viewModelScope.launch { noteRepository.moveToTrash(noteId) }
    }
}

data class FolderNotesUiState(
    val folderName: String          = "",
    val isPasswordProtected: Boolean = false,  // Sprint 15
    val notes: List<Note>           = emptyList(),
    val isLoading: Boolean          = true
)