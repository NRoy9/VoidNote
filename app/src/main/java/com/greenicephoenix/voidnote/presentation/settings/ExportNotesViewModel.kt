package com.greenicephoenix.voidnote.presentation.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.manager.ImportExportManager
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ExportNotesViewModel — drives the Export Notes screen.
 *
 * Extracted from SettingsViewModel so export lives on its own screen
 * with its own lifecycle, consistent with ImportBackupScreen.
 *
 * State machine (same as before, now scoped to this ViewModel):
 *   Idle → ChoosingFormat → ConfirmingPassword → PasswordVerifying
 *       → ReadyToExport (screen launches file picker) → Exporting
 *       → ExportSuccess / ExportError
 *
 * Plain text ZIP skips the password step — no sensitive data involved.
 */
@HiltViewModel
class ExportNotesViewModel @Inject constructor(
    private val importExportManager: ImportExportManager,
    private val encryption: NoteEncryptionManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    // Kept so startExport() knows which format to write
    private var pendingFormat: ExportFormat? = null

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * User selected a format from the format picker.
     * SECURE_BACKUP requires password verification first.
     * PLAIN_TEXT_ZIP goes straight to ReadyToExport.
     */
    fun onFormatSelected(format: ExportFormat) {
        pendingFormat = format
        _exportState.value = when (format) {
            ExportFormat.SECURE_BACKUP  -> ExportState.ConfirmingPassword(format)
            ExportFormat.PLAIN_TEXT_ZIP -> ExportState.ReadyToExport(format)
        }
    }

    /** User dismissed or pressed back — reset to format selection. */
    fun onDismissPassword() {
        _exportState.value = ExportState.Idle
        pendingFormat = null
    }

    /**
     * User confirmed their vault password for secure backup.
     * Re-derives key from PBKDF2 and checks against the verification blob.
     * Does NOT modify the session key — pure verification.
     */
    fun onPasswordConfirmed(password: String) {
        val format = pendingFormat ?: return
        if (password.isBlank()) {
            _exportState.value = ExportState.PasswordError("Please enter your vault password")
            return
        }
        viewModelScope.launch {
            _exportState.value = ExportState.PasswordVerifying
            try {
                val salt = preferencesManager.vaultSaltFlow.first()
                val blob = preferencesManager.vaultVerificationBlobFlow.first()
                val ok = encryption.verifyPasswordAgainstBlob(password, salt, blob)
                _exportState.value = if (ok)
                    ExportState.ReadyToExport(format)
                else
                    ExportState.PasswordError("Incorrect vault password. Please try again.")
            } catch (e: Exception) {
                _exportState.value = ExportState.PasswordError("Verification failed: ${e.message}")
            }
        }
    }

    /**
     * Called by the screen after the file picker returns a URI.
     * Writes the ZIP to disk and transitions to Success or Error.
     */
    fun startExport(contentResolver: ContentResolver, uri: Uri) {
        val format = pendingFormat ?: return
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            try {
                val count = when (format) {
                    ExportFormat.SECURE_BACKUP  ->
                        importExportManager.exportSecureBackup(contentResolver, uri)
                    ExportFormat.PLAIN_TEXT_ZIP ->
                        importExportManager.exportPlainTextZip(contentResolver, uri)
                }
                _exportState.value = ExportState.ExportSuccess(count, format)
            } catch (e: Exception) {
                android.util.Log.e("ExportNotes", "Export failed", e)
                _exportState.value = ExportState.ExportError(
                    e.message ?: "Export failed. Please try again."
                )
            }
        }
    }

    /** Called after the user has seen the success/error result. */
    fun reset() {
        _exportState.value = ExportState.Idle
        pendingFormat = null
    }

    // ── Filename helpers ──────────────────────────────────────────────────────

    fun secureBackupFilename(): String = importExportManager.generateSecureBackupFilename()
    fun plainTextFilename(): String    = importExportManager.generatePlainTextFilename()
}