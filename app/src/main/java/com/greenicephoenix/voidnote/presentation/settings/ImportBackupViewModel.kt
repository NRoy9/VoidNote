package com.greenicephoenix.voidnote.presentation.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.manager.ImportExportManager
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ImportBackupViewModel — Flow B: import a .vnbackup into an already-unlocked vault.
 *
 * ─── HOW THIS DIFFERS FROM RestoreBackupViewModel (Flow A) ───────────────────
 *
 * Flow A (RestoreBackupScreen):
 *   • Used on a fresh install — no vault exists yet.
 *   • The backup's salt and key BECOME the device's salt and session key.
 *   • Notes are inserted as-is (no re-encryption needed).
 *   • Calls: importSecureBackup()
 *
 * Flow B (this ViewModel + ImportBackupScreen):
 *   • Used from Settings — vault is already set up and session key K1 is active.
 *   • We derive K2 from the backup password + backup salt (pure, in-memory).
 *   • Notes are decrypted with K2 then re-encrypted with K1 before inserting.
 *   • K1 is NEVER replaced. Only ImportExportManager sees K2, transiently.
 *   • Calls: importIntoExistingVault()
 *
 * ─── UI FLOW ─────────────────────────────────────────────────────────────────
 *
 * 1. Screen opens → user taps "Choose Backup File"
 * 2. onFileSelected() reads the header (salt + blob + counts) — fast, no note parsing
 * 3. Backup info card appears showing note/folder counts
 * 4. User types the backup's vault password
 * 5. confirmImport() runs:
 *    a) verifyPasswordAgainstBlob(password, cachedSalt, cachedBlob)
 *       → false: show "Wrong password" — DB completely untouched
 *       → true: proceed
 *    b) importIntoExistingVault() merges notes with three-way logic
 *    c) onSuccess() → NavGraph pops back to Settings
 *
 * ─── SEALED STATE CLASS ──────────────────────────────────────────────────────
 *
 * Why use a sealed class instead of multiple Boolean flags?
 * The same reasoning as ExportState in SettingsViewModel: one active state
 * at a time, impossible states can't be represented, UI renders per-state.
 *
 *   Idle             → initial, no file chosen
 *   ReadingHeader    → header is being read from the ZIP (spinner)
 *   FileReady        → header loaded, info card + password field visible
 *   Importing        → importIntoExistingVault() is running (progress dialog)
 *   Success          → shows result summary (notes/folders imported, skipped)
 *   Error            → shows error message, allows retry
 */
sealed class ImportBackupUiState {

    /** Initial state — no file chosen yet. */
    object Idle : ImportBackupUiState()

    /** Reading backup header from the ZIP (fast, ~50ms). Show spinner. */
    object ReadingHeader : ImportBackupUiState()

    /**
     * Header successfully read — show the file info card and password field.
     * Only used for .vnbackup files (which are encrypted and need a password).
     */
    data class FileReady(
        val fileName: String,
        val noteCount: Int,
        val folderCount: Int,
        val appVersion: String,
        val errorMessage: String? = null,
        val isVerifying: Boolean = false
    ) : ImportBackupUiState()

    /**
     * SPRINT 10 — A .md or .zip of .md files was selected.
     *
     * Unlike .vnbackup files, Markdown imports are NOT encrypted at rest — they are
     * plain text files. No password is required. We just show a preview card with the
     * note count and a single "Import" button.
     *
     * @param fileName   Display name of the chosen file
     * @param noteCount  Number of .md files found (preview — 0 if counting failed)
     */
    data class MarkdownReady(
        val fileName:  String,
        val noteCount: Int
    ) : ImportBackupUiState()

    /**
     * importIntoExistingVault() or importMarkdown() is running.
     * Non-dismissible progress dialog — user must not close the app.
     */
    object Importing : ImportBackupUiState()

    /**
     * Import completed successfully.
     */
    data class Success(
        val notesImported: Int,
        val foldersImported: Int,
        val skippedDuplicates: Int
    ) : ImportBackupUiState()

    /** A non-recoverable error occurred. */
    data class Error(val message: String) : ImportBackupUiState()
}

@HiltViewModel
class ImportBackupViewModel @Inject constructor(
    private val encryption: NoteEncryptionManager,
    private val importExportManager: ImportExportManager
) : ViewModel() {

    // ── UI state ──────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<ImportBackupUiState>(ImportBackupUiState.Idle)
    val uiState: StateFlow<ImportBackupUiState> = _uiState.asStateFlow()

    // ── Password field state ──────────────────────────────────────────────────

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _showPassword = MutableStateFlow(false)
    val showPassword: StateFlow<Boolean> = _showPassword.asStateFlow()

    // ── Internal — cached from backup header, used at confirm time ────────────

    private var selectedUri: Uri? = null
    private var cachedSalt: String = ""
    private var cachedBlob: String = ""

    // ── Input handlers ────────────────────────────────────────────────────────

    fun onPasswordChange(value: String) {
        _password.value = value
        // Clear any inline password error when the user starts typing again
        val current = _uiState.value
        if (current is ImportBackupUiState.FileReady && current.errorMessage != null) {
            _uiState.value = current.copy(errorMessage = null)
        }
    }

    fun toggleShowPassword() {
        _showPassword.value = !_showPassword.value
    }

    // ── File selection ────────────────────────────────────────────────────────

    /**
     * Called when the user picks a .vnbackup file from the OpenDocument launcher.
     *
     * Only reads backup.json header (salt, blob, counts) — does not parse notes
     * or extract media. Fast even for large backups.
     *
     * On success: transitions to FileReady with info card data.
     * On failure: transitions to Error with a human-readable message.
     *
     * @param uri              URI from the file picker result
     * @param contentResolver  From LocalContext.current.contentResolver
     * @param displayName      File display name (nullable — can come from DocumentFile)
     */
    fun onFileSelected(
        uri: Uri,
        contentResolver: ContentResolver,
        displayName: String?
    ) {
        viewModelScope.launch {
            _uiState.value = ImportBackupUiState.ReadingHeader
            _password.value = ""

            val fileName = displayName ?: uri.lastPathSegment ?: "file"

            // ── Route by file type ────────────────────────────────────────────
            // .vnbackup → encrypted vault backup, needs password → FileReady
            // .md / .zip → plain text markdown → MarkdownReady (no password)
            val lower = fileName.lowercase()
            when {
                lower.endsWith(".md") || lower.endsWith(".zip") -> {
                    // Count how many notes we'll import — shows in the preview card.
                    // countMarkdownNotes() is fast (no decryption, just walks entries).
                    val count = try {
                        importExportManager.countMarkdownNotes(contentResolver, uri)
                    } catch (e: Exception) { 0 }

                    selectedUri = uri
                    _uiState.value = ImportBackupUiState.MarkdownReady(
                        fileName  = fileName,
                        noteCount = count
                    )
                }

                else -> {
                    // Treat as .vnbackup (encrypted backup — requires password)
                    try {
                        val header = importExportManager.readBackupHeader(contentResolver, uri)

                        if (header.salt.isEmpty()) {
                            _uiState.value = ImportBackupUiState.Error(
                                "This backup was created by an older version of Void Note " +
                                        "and cannot be imported here. Use the plain text export " +
                                        "from that device to recover your notes manually."
                            )
                            return@launch
                        }

                        cachedSalt  = header.salt
                        cachedBlob  = header.verificationBlob
                        selectedUri = uri

                        _uiState.value = ImportBackupUiState.FileReady(
                            fileName    = fileName,
                            noteCount   = header.noteCount,
                            folderCount = header.folderCount,
                            appVersion  = header.appVersion
                        )

                    } catch (e: Exception) {
                        _uiState.value = ImportBackupUiState.Error(
                            "Could not read backup file. Make sure it is a valid .vnbackup file.\n" +
                                    "(${e.message})"
                        )
                    }
                }
            }
        }
    }

    // ── Markdown import ───────────────────────────────────────────────────────

    /**
     * Called when the user taps "Import" on the MarkdownReady card.
     *
     * No password step — markdown files are plain text. We just parse, encrypt,
     * and insert. Each note gets a fresh UUID (no dedup by ID).
     *
     * NOTE: Importing the same file twice WILL create duplicate notes.
     * This is documented in the UI.
     */
    fun confirmMarkdownImport(contentResolver: ContentResolver) {
        val uri = selectedUri ?: return
        viewModelScope.launch {
            _uiState.value = ImportBackupUiState.Importing

            val result = try {
                importExportManager.importMarkdown(contentResolver, uri)
            } catch (e: Exception) {
                _uiState.value = ImportBackupUiState.Error(
                    "Markdown import failed: ${e.message}"
                )
                return@launch
            }

            if (result.isSuccess) {
                _uiState.value = ImportBackupUiState.Success(
                    notesImported     = result.notesImported,
                    foldersImported   = result.foldersImported,
                    skippedDuplicates = 0   // markdown import never skips — always fresh UUIDs
                )
            } else {
                _uiState.value = ImportBackupUiState.Error(
                    result.error ?: "Import failed for an unknown reason."
                )
            }
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Called when user taps the "Import" button.
     *
     * Steps:
     *   1. Verify password against the backup's verification blob (pure check, DB untouched)
     *   2. If correct: call importIntoExistingVault() — merges notes with K1/K2 re-encryption
     *   3. On success: transition to Success state → screen calls onSuccess()
     *   4. On failure: show inline error in FileReady state — user can retry
     *
     * @param contentResolver  From LocalContext.current.contentResolver
     */
    fun confirmImport(contentResolver: ContentResolver) {
        val uri      = selectedUri ?: return
        val current  = _uiState.value as? ImportBackupUiState.FileReady ?: return
        val password = _password.value

        if (password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Please enter the backup's vault password")
            return
        }

        viewModelScope.launch {
            // ── Step 1: Verify password ───────────────────────────────────────
            // Show spinner in the FileReady card while PBKDF2 (~300ms) runs.
            _uiState.value = current.copy(isVerifying = true, errorMessage = null)

            val isCorrect = try {
                encryption.verifyPasswordAgainstBlob(
                    password   = password,
                    saltBase64 = cachedSalt,
                    blobBase64 = cachedBlob
                )
            } catch (e: Exception) {
                _uiState.value = current.copy(
                    isVerifying  = false,
                    errorMessage = "Verification failed: ${e.message}"
                )
                return@launch
            }

            if (!isCorrect) {
                _uiState.value = current.copy(
                    isVerifying  = false,
                    errorMessage = "Wrong password. Please try again."
                )
                return@launch
            }

            // ── Step 2: Run the import ────────────────────────────────────────
            // Show the non-dismissible progress dialog.
            // Re-encryption loop can take a few seconds for large vaults.
            _uiState.value = ImportBackupUiState.Importing

            val result = try {
                importExportManager.importIntoExistingVault(
                    contentResolver = contentResolver,
                    uri             = uri,
                    enteredPassword = password
                )
            } catch (e: Exception) {
                _uiState.value = ImportBackupUiState.Error(
                    "Import failed unexpectedly: ${e.message}"
                )
                return@launch
            }

            // ── Step 3: Handle result ─────────────────────────────────────────
            if (result.isSuccess) {
                _uiState.value = ImportBackupUiState.Success(
                    notesImported      = result.notesImported,
                    foldersImported    = result.foldersImported,
                    skippedDuplicates  = result.skippedDuplicates
                )
            } else {
                _uiState.value = ImportBackupUiState.Error(
                    result.error ?: "Import failed for an unknown reason."
                )
            }
        }
    }

    /** User taps "Try Another File" on the error screen. */
    fun resetToIdle() {
        _uiState.value = ImportBackupUiState.Idle
        _password.value = ""
        selectedUri = null
        cachedSalt  = ""
        cachedBlob  = ""
    }
}