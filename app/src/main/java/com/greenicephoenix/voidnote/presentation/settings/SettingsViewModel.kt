package com.greenicephoenix.voidnote.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import com.greenicephoenix.voidnote.security.BiometricLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    // ── Private ───────────────────────────────────────────────────────────────

    private fun getAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) { "1.0.0" }
}