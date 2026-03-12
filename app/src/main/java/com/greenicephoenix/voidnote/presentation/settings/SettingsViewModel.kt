package com.greenicephoenix.voidnote.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import com.greenicephoenix.voidnote.security.BiometricLockManager
import com.greenicephoenix.voidnote.util.UpdateCheckerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

/**
 * SettingsViewModel — settings screen state and actions.
 *
 * Export logic has been moved to ExportNotesViewModel so it lives
 * on its own screen, consistent with ImportBackupScreen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val preferencesManager: PreferencesManager,
    private val biometricLockManager: BiometricLockManager,
    private val updateChecker: UpdateCheckerManager,
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

    // Separate MutableStateFlow for update check — it changes on user tap, not on
    // DB emissions, so it doesn't belong inside the combine() below.
    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        // Inner combine: the four DB count streams → one data class to keep
        // the outer combine under the 5-parameter limit.
        combine(
            noteRepository.getNoteCount(),
            folderRepository.getFolderCount(),
            noteRepository.getArchivedNoteCount(),
            noteRepository.getTrashedNoteCount()
        ) { noteCount, folderCount, archiveCount, trashCount ->
            // Pack all four counts into a simple array so the outer combine
            // receives a single value instead of four separate parameters.
            intArrayOf(noteCount, folderCount, archiveCount, trashCount)
        },
        currentTheme,
        _updateCheckState
    ) { counts, theme, updateState ->
        SettingsUiState(
            noteCount        = counts[0],
            folderCount      = counts[1],
            archiveCount     = counts[2],
            trashCount       = counts[3],
            currentTheme     = theme,
            appVersion       = getAppVersion(),
            updateCheckState = updateState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    // ── Check for updates ────────────────────────────────────────────────────

    /**
     * Checks GitHub Releases for a newer version.
     *
     * Transitions: Idle/UpToDate/Error/Available → Checking → result
     * Guard: if already Checking, ignore the tap (no duplicate requests).
     * The state resets to Idle after the user dismisses the result dialog,
     * or stays at UpToDate/Available so the result persists until next tap.
     */
    fun checkForUpdates() {
        if (_updateCheckState.value == UpdateCheckState.Checking) return
        _updateCheckState.value = UpdateCheckState.Checking
        viewModelScope.launch {
            val result = updateChecker.checkForUpdate(getAppVersion())
            _updateCheckState.value = when {
                result == null -> UpdateCheckState.UpToDate
                else           -> UpdateCheckState.Available(result.latestVersion, result.downloadUrl)
            }
        }
    }

    /** Opens the GitHub download URL in the device browser. */
    fun openDownloadUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Resets the update state to Idle after the user dismisses the result. */
    fun dismissUpdateResult() {
        _updateCheckState.update { current ->
            current as? UpdateCheckState.Checking ?: UpdateCheckState.Idle
        }
    }

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

    // ── Private ───────────────────────────────────────────────────────────────

    private fun getAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) { "1.0.0" }
}