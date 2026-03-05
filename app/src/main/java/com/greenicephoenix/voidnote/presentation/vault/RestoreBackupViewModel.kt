package com.greenicephoenix.voidnote.presentation.vault

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.manager.ImportExportManager
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import com.greenicephoenix.voidnote.presentation.settings.BackupHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * RestoreBackupViewModel — Flow A fresh install restore from .vnbackup.
 *
 * ─── FLOW ────────────────────────────────────────────────────────────────────
 *
 * 1. User taps "Restore from existing backup" on VaultSetupScreen
 * 2. NavGraph navigates to RestoreBackupScreen
 * 3. User picks a .vnbackup file → onFileSelected() reads the header
 *    (salt + verificationBlob + counts) WITHOUT loading the full backup
 * 4. User types their vault password
 * 5. confirmRestore() runs:
 *    a) verifyPasswordAgainstBlob(password, salt, blob)
 *       → false: show "Wrong password" — DB untouched
 *       → true: proceed
 *    b) PBKDF2(password + salt) → same masterKey that encrypted the backup
 *    c) activateKey(masterKey)
 *    d) importSecureBackup() inserts all rows (encrypted as-is, no re-encrypt)
 *    e) Persist salt + wrapped key + verification blob in DataStore
 *    f) Mark vault setup complete → onSuccess() → NavGraph to NotesList
 *
 * ─── WHY NO RE-ENCRYPTION? ────────────────────────────────────────────────
 *
 * Notes in the backup ARE encrypted with masterKey (same key we just derived).
 * We insert the encrypted ciphertext directly. The activated session key
 * decrypts them correctly at read time. No plaintext ever touches memory.
 */
@HiltViewModel
class RestoreBackupViewModel @Inject constructor(
    private val encryption: NoteEncryptionManager,
    private val preferencesManager: PreferencesManager,
    private val importExportManager: ImportExportManager
) : ViewModel() {

    // ── File selection state ──────────────────────────────────────────────────

    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

    private var selectedUri: Uri? = null

    // True once we've successfully read the backup header
    private val _fileReady = MutableStateFlow(false)
    val fileReady: StateFlow<Boolean> = _fileReady.asStateFlow()

    // Backup header shown to user (note count, folder count, version)
    private val _backupHeader = MutableStateFlow<BackupHeader?>(null)
    val backupHeader: StateFlow<BackupHeader?> = _backupHeader.asStateFlow()

    // Cached cryptographic header for password verification
    private var cachedSalt: String = ""
    private var cachedBlob: String = ""

    // ── Password state ────────────────────────────────────────────────────────

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _showPassword = MutableStateFlow(false)
    val showPassword: StateFlow<Boolean> = _showPassword.asStateFlow()

    // ── Async state ───────────────────────────────────────────────────────────

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Derived ───────────────────────────────────────────────────────────────

    // NOTE: canRestore is intentionally NOT a StateFlow.
    // The Screen derives it directly from the three individual StateFlows it
    // already collects (fileReady, password, isLoading). This avoids combine/stateIn
    // complexity entirely. See RestoreBackupScreen.kt.
    // ── Input handlers ────────────────────────────────────────────────────────

    fun onPasswordChange(value: String) {
        _password.value = value
        _errorMessage.value = null
    }

    fun toggleShowPassword() { _showPassword.value = !_showPassword.value }

    // ── File selection ────────────────────────────────────────────────────────

    /**
     * Called when the user picks a .vnbackup file from the file picker.
     *
     * Reads ONLY backup.json header — does not parse notes or extract media.
     * This keeps the password entry step fast even for large backups.
     *
     * If the file is invalid (missing salt, not a .vnbackup) we show an error
     * and keep _fileReady = false so the Restore button stays disabled.
     */
    fun onFileSelected(
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String?
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _fileReady.value = false
            _backupHeader.value = null

            try {
                val header = importExportManager.readBackupHeader(contentResolver, uri)

                when {
                    header.salt.isEmpty() -> {
                        _errorMessage.value =
                            "This backup is from an older version and cannot be imported. " +
                                    "Use the plain text export from that device to recover your notes."
                        return@launch
                    }
                    header.verificationBlob.isEmpty() -> {
                        // Old backup without blob — we'll still try, but warn the user
                        // verifyPasswordAgainstBlob() handles empty blob gracefully
                        _errorMessage.value = null
                    }
                }

                cachedSalt = header.salt
                cachedBlob = header.verificationBlob
                selectedUri = uri
                _selectedFileName.value = displayName ?: uri.lastPathSegment ?: "backup.vnbackup"
                _backupHeader.value = header
                _fileReady.value = true

            } catch (e: Exception) {
                _errorMessage.value =
                    "Could not read backup file. Make sure it is a valid .vnbackup file."
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    /**
     * Execute the full restore once the user taps "Restore".
     *
     * @param contentResolver  Pass LocalContext.current.contentResolver
     * @param onSuccess        NavGraph navigates to NotesList, clears back stack
     */
    fun confirmRestore(contentResolver: ContentResolver, onSuccess: () -> Unit) {
        // Guard directly on StateFlow values — no derived canRestore needed
        if (!_fileReady.value || _password.value.isEmpty() || _isLoading.value) return
        val uri = selectedUri ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // ── a) Verify password BEFORE touching the DB ─────────────────
                // verifyPasswordAgainstBlob() is a pure function — does not
                // modify the session key. Safe to call at any time.
                val correct = encryption.verifyPasswordAgainstBlob(
                    password   = _password.value,
                    saltBase64 = cachedSalt,
                    blobBase64 = cachedBlob
                )
                if (!correct) {
                    _errorMessage.value = "Wrong password. Please try again."
                    return@launch
                }

                // ── b–f) Full import (derive key, insert rows, persist) ────────
                // importSecureBackup() handles everything after verification:
                //   - PBKDF2(password + backup.salt) → masterKey
                //   - activateKey(masterKey)
                //   - Insert all NoteEntity, FolderEntity, InlineBlockEntity rows
                //   - Copy media .enc files
                //   - Store salt + wrappedKey + verificationBlob in DataStore
                //   - Mark vault setup complete
                val result = importExportManager.importSecureBackup(
                    contentResolver = contentResolver,
                    uri             = uri,
                    enteredPassword = _password.value
                )

                if (!result.isSuccess) {
                    _errorMessage.value = result.error
                    return@launch
                }

                onSuccess()

            } catch (e: Exception) {
                _errorMessage.value = "Restore failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}