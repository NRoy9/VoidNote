package com.greenicephoenix.voidnote.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.manager.ImportExportManager
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import com.greenicephoenix.voidnote.security.BiometricLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Export flow state machine ─────────────────────────────────────────────────
//
// The export UI is driven by a state machine to avoid a tangle of Boolean flags.
// Each state maps to a distinct UI: no dialog, format picker, password entry, etc.
//
// Flow for SECURE BACKUP:
//   Idle
//     → user taps "Export Notes"
//   ChoosingFormat
//     → user picks "Secure Backup"
//   ConfirmingPassword(format = SECURE_BACKUP)
//     → user types password, taps "Confirm"
//   PasswordVerifying
//     → async: verifyPasswordAgainstBlob()
//   PasswordError(message)  OR  ReadyToExport(format = SECURE_BACKUP)
//     → ReadyToExport: screen launches file picker → user picks save location
//   Exporting
//     → async: exportSecureBackup() / exportPlainTextZip()
//   ExportSuccess(noteCount)  OR  ExportError(message)
//     → Idle (after user dismisses)
//
// Flow for PLAIN TEXT ZIP:
//   Idle → ChoosingFormat → ReadyToExport(PLAIN_TEXT_ZIP)
//   (no password step — plain text export is intentionally unencrypted)

sealed class ExportState {
    /** No export in progress. */
    object Idle : ExportState()

    /** Format selection dialog is open. */
    object ChoosingFormat : ExportState()

    /**
     * Password confirmation dialog is open.
     * [format] is always SECURE_BACKUP here — plain text needs no password.
     */
    data class ConfirmingPassword(val format: ExportFormat) : ExportState()

    /** PBKDF2 + blob verification is running (~300ms). Show spinner in dialog. */
    object PasswordVerifying : ExportState()

    /** Verification failed — show error inside the password dialog. */
    data class PasswordError(val message: String) : ExportState()

    /**
     * Password verified (or plain text chosen — no password needed).
     * The screen observes this state and launches the file picker.
     * After the picker returns a URI, call startExport().
     */
    data class ReadyToExport(val format: ExportFormat) : ExportState()

    /** ZIP is being written to disk. Show spinner. */
    object Exporting : ExportState()

    data class ExportSuccess(val noteCount: Int, val format: ExportFormat) : ExportState()
    data class ExportError(val message: String) : ExportState()
}

// Two export formats exposed to the UI
enum class ExportFormat {
    SECURE_BACKUP,    // .vnbackup — encrypted ZIP, importable
    PLAIN_TEXT_ZIP    // .zip — folder structure, human-readable, export-only
}

/**
 * SettingsViewModel — settings screen state and actions.
 *
 * ─── EXPORT ARCHITECTURE ──────────────────────────────────────────────────────
 *
 * WHY USE A STATE MACHINE?
 * Export involves multiple async steps plus UI dialogs. A flat set of Boolean
 * flags (showDialog1, showDialog2, isLoading, isError...) becomes impossible to
 * reason about — any combination of flags can be valid or invalid, and you need
 * to reset them all correctly on every path.
 *
 * A sealed class state machine has ONE active state at a time. Transitions are
 * explicit. The UI renders a specific layout per state. Impossible states are
 * impossible to represent.
 *
 * HOW THE FILE PICKER IS TRIGGERED FROM THE VIEWMODEL:
 * Android's ActivityResultLauncher (CreateDocument) must be called from a
 * Composable — it cannot be called from a ViewModel. So the ViewModel sets state
 * to ReadyToExport and the screen observes it:
 *
 *   LaunchedEffect(exportState) {
 *     if (exportState is ReadyToExport) {
 *       when (exportState.format) {
 *         SECURE_BACKUP   → secureBackupLauncher.launch(filename)
 *         PLAIN_TEXT_ZIP  → plainTextLauncher.launch(filename)
 *       }
 *     }
 *   }
 *
 * After the launcher returns a URI, the screen calls viewModel.startExport(uri).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val preferencesManager: PreferencesManager,
    private val biometricLockManager: BiometricLockManager,
    private val importExportManager: ImportExportManager,
    private val encryption: NoteEncryptionManager
) : ViewModel() {

    // ── Biometric ─────────────────────────────────────────────────────────────

    val isBiometricAvailable: Boolean = biometricLockManager.isAvailable()

    val biometricLockEnabled: StateFlow<Boolean> = preferencesManager.biometricLockFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setBiometricLock(enabled) }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    val currentTheme: StateFlow<AppTheme> = preferencesManager.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.DARK)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { preferencesManager.setTheme(theme) }
    }

    // ── UI state ──────────────────────────────────────────────────────────────

    val uiState: StateFlow<SettingsUiState> = combine(
        noteRepository.getNoteCount(),
        folderRepository.getFolderCount(),
        currentTheme
    ) { noteCount: Int, folderCount: Int, theme: AppTheme ->
        SettingsUiState(
            noteCount    = noteCount,
            folderCount  = folderCount,
            currentTheme = theme,
            appVersion   = getAppVersion()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    // ── Clear all data ────────────────────────────────────────────────────────

    fun clearAllNotes() {
        viewModelScope.launch {
            try {
                noteRepository.getAllNotes().first().forEach { note ->
                    noteRepository.deleteNotePermanently(note.id)
                }
                folderRepository.getAllFolders().first().forEach { folder ->
                    folderRepository.deleteFolder(folder.id)
                }
                noteRepository.emptyTrash()
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Failed to clear data", e)
            }
        }
    }

    // ── Export state machine ──────────────────────────────────────────────────

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    // The format that was chosen — kept so startExport() knows which path to take
    private var pendingExportFormat: ExportFormat? = null

    // ── Export actions (called by SettingsScreen) ─────────────────────────────

    /** User tapped "Export Notes" → open the format selection dialog. */
    fun onExportTapped() {
        _exportState.value = ExportState.ChoosingFormat
    }

    /** User dismissed any export dialog → return to idle. */
    fun onExportDismissed() {
        _exportState.value = ExportState.Idle
        pendingExportFormat = null
    }

    /**
     * User selected a format from the format picker.
     *
     * SECURE_BACKUP → show password confirmation dialog.
     * PLAIN_TEXT_ZIP → go straight to ReadyToExport (no password needed
     *                  because the output is intentionally unencrypted).
     */
    fun onFormatSelected(format: ExportFormat) {
        pendingExportFormat = format
        _exportState.value = when (format) {
            ExportFormat.SECURE_BACKUP   -> ExportState.ConfirmingPassword(format)
            ExportFormat.PLAIN_TEXT_ZIP  -> ExportState.ReadyToExport(format)
        }
    }

    /**
     * User tapped "Confirm" in the password dialog.
     *
     * Verifies the entered password against the verification blob.
     * On success → ReadyToExport (screen will launch the file picker).
     * On failure → PasswordError (dialog stays open, shows error message).
     *
     * WHY PBKDF2 HERE INSTEAD OF A SIMPLE STRING COMPARE?
     * The vault password is never stored — only the derived key is (wrapped in
     * Keystore). To verify the password, we re-run PBKDF2(password + stored
     * salt) and try to decrypt the verification blob with the resulting key.
     * If the GCM authentication tag passes, the password produced the same key,
     * which means it is the correct password.
     *
     * This takes ~300ms (same as vault setup). The spinner in the dialog
     * prevents impatient double-taps.
     */
    fun onExportPasswordConfirmed(password: String) {
        val format = pendingExportFormat ?: return
        if (password.isBlank()) {
            _exportState.value = ExportState.PasswordError("Please enter your vault password")
            return
        }

        viewModelScope.launch {
            _exportState.value = ExportState.PasswordVerifying

            try {
                val saltBase64 = preferencesManager.vaultSaltFlow.first()
                val blobBase64 = preferencesManager.vaultVerificationBlobFlow.first()

                // verifyPasswordAgainstBlob() is a pure function — does not
                // modify the session key or any other mutable state.
                val isCorrect = encryption.verifyPasswordAgainstBlob(
                    password   = password,
                    saltBase64 = saltBase64,
                    blobBase64 = blobBase64
                )

                _exportState.value = if (isCorrect) {
                    ExportState.ReadyToExport(format)
                } else {
                    ExportState.PasswordError("Incorrect vault password. Please try again.")
                }

            } catch (e: Exception) {
                _exportState.value = ExportState.PasswordError(
                    "Verification failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Called by the screen after the file picker returns a URI.
     *
     * This is the actual write step. State transitions:
     *   ReadyToExport → Exporting → ExportSuccess / ExportError
     */
    fun startExport(contentResolver: android.content.ContentResolver, uri: android.net.Uri) {
        val format = pendingExportFormat ?: return

        viewModelScope.launch {
            _exportState.value = ExportState.Exporting

            try {
                val noteCount = when (format) {
                    ExportFormat.SECURE_BACKUP  ->
                        importExportManager.exportSecureBackup(contentResolver, uri)
                    ExportFormat.PLAIN_TEXT_ZIP ->
                        importExportManager.exportPlainTextZip(contentResolver, uri)
                }

                _exportState.value = ExportState.ExportSuccess(noteCount, format)

            } catch (e: Exception) {
                android.util.Log.e("Settings", "Export failed", e)
                _exportState.value = ExportState.ExportError(
                    e.message ?: "Export failed. Please try again."
                )
            }
        }
    }

    /** Reset export state after the user has seen the success/error snackbar. */
    fun onExportResultAcknowledged() {
        _exportState.value = ExportState.Idle
        pendingExportFormat = null
    }

    // ── Filename helpers (used by the file picker launchers) ──────────────────

    fun secureBackupFilename(): String = importExportManager.generateSecureBackupFilename()
    fun plainTextFilename(): String    = importExportManager.generatePlainTextFilename()

    // ── Private ───────────────────────────────────────────────────────────────

    private fun getAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) { "1.0.0" }
}