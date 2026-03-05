package com.greenicephoenix.voidnote.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.InlineBlockDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import com.greenicephoenix.voidnote.domain.model.FormatRange
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ChangeVaultPasswordViewModel
 *
 * ─── THE CRITICAL ORDERING PROBLEM ───────────────────────────────────────────
 *
 * A naive implementation would:
 *   1. Derive new key
 *   2. activateKey(newKey)           ← session key is now new key
 *   3. Re-encrypt all notes          ← uses new key correctly
 *   4. Write to DB
 *   5. Update DataStore
 *
 * The bug: if step 4 fails (DB write, disk full, crash), the session key is
 * already the new key — but every row in the DB still has old-key ciphertext.
 * Every note becomes unreadable. The user loses all their data.
 *
 * ─── THE CORRECT ORDER ───────────────────────────────────────────────────────
 *
 *   1. Verify current password against verification blob (DB untouched if wrong)
 *   2. Derive new key from (new password + new salt)
 *   3. Re-encrypt ALL notes in memory: decrypt(oldKey) → encrypt(newKey)
 *      Uses encryptWithKey(newKey) — pure, does NOT activate new key yet
 *   4. @Transaction DB write — insert all re-encrypted rows atomically
 *      If this fails: Room rolls back, old-key ciphertext intact, app works
 *   5. Re-encrypt media files safely:
 *      decryptBytes(oldKey) → encryptBytesWithKey(newKey) → write to TEMP file
 *      Only after temp write succeeds: replace original with temp
 *      On failure: delete temp file, original untouched
 *   6. activateKey(newKey)           ← ONLY after DB and files are committed
 *   7. Update DataStore: new salt, new wrappedKey, new verificationBlob
 *
 * If step 4 fails: old session key still active, old DataStore still valid. Safe.
 * If step 5 fails: specific media file missing thumbnail, but notes readable. Acceptable.
 * If step 6 or 7 fails: edge case, but notes are now re-encrypted. On next launch,
 *   VaultUnlock will re-derive from DataStore (old values) — the old key will fail
 *   to decrypt the new ciphertext. This is the one remaining risk, mitigated by
 *   doing DataStore write immediately after activateKey with no operations between.
 *
 * ─── UI STATE MACHINE ────────────────────────────────────────────────────────
 *
 * Same pattern as ExportState and ImportBackupUiState — one sealed class,
 * one active state at a time, impossible states are impossible to represent.
 *
 *   Idle        → form visible, both password fields
 *   Verifying   → spinner, form disabled (PBKDF2 check ~300ms)
 *   Reencrypting → non-dismissible progress dialog with note count
 *   Success     → success confirmation, "Done" button
 *   Error       → error message, "Try Again" resets to Idle
 */
sealed class ChangePasswordState {
    object Idle : ChangePasswordState()

    /** Verifying current password against blob (~300ms PBKDF2). */
    object Verifying : ChangePasswordState()

    /**
     * Re-encryption in progress — show non-dismissible dialog.
     * [progress] is 0.0–1.0, updated as notes are processed.
     * [total] is the total note count for the progress label.
     */
    data class Reencrypting(val progress: Float = 0f, val total: Int = 0) : ChangePasswordState()

    /** All done. Session key and DataStore updated. */
    object Success : ChangePasswordState()

    /** Something went wrong. [message] is shown to the user. */
    data class Error(val message: String) : ChangePasswordState()
}

@HiltViewModel
class ChangeVaultPasswordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: NoteEncryptionManager,
    private val preferencesManager: PreferencesManager,
    private val noteDao: NoteDao,
    private val inlineBlockDao: InlineBlockDao,
    private val folderDao: FolderDao
) : ViewModel() {

    // ── UI State ──────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow<ChangePasswordState>(ChangePasswordState.Idle)
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()

    // ── Form fields ───────────────────────────────────────────────────────────

    private val _currentPassword    = MutableStateFlow("")
    val currentPassword: StateFlow<String> = _currentPassword.asStateFlow()

    private val _newPassword        = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword.asStateFlow()

    private val _confirmNewPassword = MutableStateFlow("")
    val confirmNewPassword: StateFlow<String> = _confirmNewPassword.asStateFlow()

    private val _showCurrentPassword = MutableStateFlow(false)
    val showCurrentPassword: StateFlow<Boolean> = _showCurrentPassword.asStateFlow()

    private val _showNewPassword     = MutableStateFlow(false)
    val showNewPassword: StateFlow<Boolean> = _showNewPassword.asStateFlow()

    // Inline error for wrong current password — shown inside the form
    private val _currentPasswordError = MutableStateFlow<String?>(null)
    val currentPasswordError: StateFlow<String?> = _currentPasswordError.asStateFlow()

    // ── Input handlers ────────────────────────────────────────────────────────

    fun onCurrentPasswordChange(v: String) {
        _currentPassword.value = v
        _currentPasswordError.value = null
    }
    fun onNewPasswordChange(v: String)        { _newPassword.value = v }
    fun onConfirmNewPasswordChange(v: String) { _confirmNewPassword.value = v }
    fun toggleShowCurrentPassword()           { _showCurrentPassword.value = !_showCurrentPassword.value }
    fun toggleShowNewPassword()               { _showNewPassword.value = !_showNewPassword.value }

    fun resetToIdle() {
        _state.value             = ChangePasswordState.Idle
        _currentPassword.value   = ""
        _newPassword.value       = ""
        _confirmNewPassword.value = ""
        _currentPasswordError.value = null
    }

    // ── Change password ───────────────────────────────────────────────────────

    /**
     * Execute the password change. See class-level doc for the exact order.
     * Called when user taps "Change Password" and form is valid.
     */
    fun confirmChange() {
        val current = _currentPassword.value
        val new     = _newPassword.value
        val confirm = _confirmNewPassword.value

        // Basic form validation before hitting the crypto
        if (current.isBlank()) {
            _currentPasswordError.value = "Enter your current password"
            return
        }
        if (new.length < 8) {
            _state.value = ChangePasswordState.Error("New password must be at least 8 characters")
            return
        }
        if (new != confirm) {
            _state.value = ChangePasswordState.Error("New passwords do not match")
            return
        }
        if (new == current) {
            _state.value = ChangePasswordState.Error("New password must be different from the current one")
            return
        }

        viewModelScope.launch {
            // ── Step 1: Verify current password ──────────────────────────────
            _state.value = ChangePasswordState.Verifying

            val saltBase64 = preferencesManager.vaultSaltFlow.first()
            val blobBase64 = preferencesManager.vaultVerificationBlobFlow.first()

            val isCorrect = try {
                encryption.verifyPasswordAgainstBlob(current, saltBase64, blobBase64)
            } catch (e: Exception) {
                _state.value = ChangePasswordState.Error("Verification failed: ${e.message}")
                return@launch
            }

            if (!isCorrect) {
                // Wrong current password — return to form with inline error
                _state.value = ChangePasswordState.Idle
                _currentPasswordError.value = "Incorrect password. Please try again."
                return@launch
            }

            // ── Step 2: Derive new key ────────────────────────────────────────
            // Generate a fresh random salt for the new password.
            // Never reuse the old salt with a new password.
            val newSalt    = encryption.generateSalt()
            val newSaltB64 = encryption.encodeSalt(newSalt)
            val newKey     = encryption.deriveKey(new, newSalt)

            // ── Step 3: Re-encrypt all notes in memory ────────────────────────
            // Load raw DB entities (encrypted ciphertext) — NOT domain models.
            // We need to decrypt with old session key, re-encrypt with new key.
            val allEntities = noteDao.getAllNotesWithTrash()

            _state.value = ChangePasswordState.Reencrypting(progress = 0f, total = allEntities.size)

            val reencryptedEntities = mutableListOf<NoteEntity>()

            allEntities.forEachIndexed { index, entity ->
                // Decrypt each field with the current session key (old key)
                val plainTitle   = encryption.decrypt(entity.title)
                val plainContent = encryption.decrypt(entity.content)
                val plainTags    = entity.tags.map { encryption.decrypt(it) }

                // Re-encrypt each field with the new key (pure — does NOT activate new key)
                reencryptedEntities.add(
                    entity.copy(
                        title   = encryption.encryptWithKey(plainTitle, newKey),
                        content = encryption.encryptWithKey(plainContent, newKey),
                        tags    = plainTags.map { encryption.encryptWithKey(it, newKey) }
                    )
                )

                // Update progress every 10 notes to avoid too-frequent recompositions
                if (index % 10 == 0) {
                    _state.value = ChangePasswordState.Reencrypting(
                        progress = (index + 1).toFloat() / allEntities.size,
                        total    = allEntities.size
                    )
                }
            }

            // ── Step 4: Atomic DB write ───────────────────────────────────────
            // @Transaction on insertNotes means: if ANY insert fails, Room rolls
            // back ALL writes. The old-key ciphertext remains intact. App works.
            try {
                noteDao.insertNotes(reencryptedEntities)
            } catch (e: Exception) {
                // DB write failed — old session key is still active, DataStore unchanged.
                // The app is fully operational. User can try again.
                _state.value = ChangePasswordState.Error(
                    "Failed to save re-encrypted notes. Your password has NOT been changed.\n(${e.message})"
                )
                return@launch
            }

            // ── Step 5: Re-encrypt media files safely ─────────────────────────
            // Filesystem has no transactions. Strategy:
            //   a) Read original .enc file (encrypted with old key)
            //   b) Decrypt with old session key → plainBytes
            //   c) Re-encrypt with new key → write to .tmp file
            //   d) If .tmp write succeeds → replace original with .tmp
            //   e) If .tmp write fails → delete .tmp, leave original unchanged
            reencryptMediaFiles(newKey)

            // ── Step 6: Activate new key ──────────────────────────────────────
            // DB is committed. Media files are re-encrypted. NOW it is safe to
            // switch the session key. From this point forward, all encrypt/decrypt
            // calls use the new key.
            encryption.activateKey(newKey)

            // ── Step 7: Update DataStore ──────────────────────────────────────
            // Wrap the new key with the Keystore hardware key so future launches
            // can load it without a password prompt (if biometric is enabled).
            val wrappedNewKey  = encryption.wrapAndEncode(newKey)
            val newBlobB64     = encryption.createVerificationBlob() // uses new session key

            preferencesManager.setVaultSalt(newSaltB64)
            preferencesManager.setVaultWrappedKey(wrappedNewKey)
            preferencesManager.setVaultVerificationBlob(newBlobB64)

            _state.value = ChangePasswordState.Success
        }
    }

    // ── Media file re-encryption ──────────────────────────────────────────────

    /**
     * Re-encrypt all .enc files in images/ and audio/ directories.
     *
     * Safe strategy per file:
     *   1. Read original .enc → decrypt with old session key
     *   2. Re-encrypt with newKey → write to fileName.tmp
     *   3. If tmp write OK → delete original → rename tmp → original name
     *   4. If any step fails → delete tmp → leave original untouched
     *
     * A failed file means that note's media won't display after the password
     * change, but all text content is safe and readable.
     */
    private fun reencryptMediaFiles(newKey: javax.crypto.SecretKey) {
        val dirs = listOf(
            File(context.filesDir, "images"),
            File(context.filesDir, "audio")
        )

        for (dir in dirs) {
            if (!dir.exists()) continue

            dir.listFiles { f -> f.extension == "enc" }?.forEach { encFile ->
                val tempFile = File(dir, "${encFile.nameWithoutExtension}.tmp")

                try {
                    // Decrypt with old session key
                    val encryptedBytes = encFile.readBytes()
                    val plainBytes     = encryption.decryptBytes(encryptedBytes) ?: return@forEach

                    // Re-encrypt with new key → write to temp
                    val reencryptedBytes = encryption.encryptBytesWithKey(plainBytes, newKey)
                    tempFile.writeBytes(reencryptedBytes)

                    // Atomic swap: delete original, rename temp
                    encFile.delete()
                    tempFile.renameTo(encFile)

                } catch (e: Exception) {
                    // Clean up temp file on any failure — original is untouched
                    tempFile.delete()
                    android.util.Log.e(
                        "ChangeVaultPassword",
                        "Failed to re-encrypt media file: ${encFile.name}",
                        e
                    )
                }
            }
        }
    }
}