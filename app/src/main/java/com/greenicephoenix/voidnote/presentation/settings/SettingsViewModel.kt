package com.greenicephoenix.voidnote.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Settings Screen
 *
 * NOW WITH PERSISTENT THEME STORAGE!
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    // Current theme from DataStore
    val currentTheme: StateFlow<AppTheme> = preferencesManager.themeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.DARK
        )

    // UI State
    val uiState: StateFlow<SettingsUiState> = combine(
        noteRepository.getNoteCount(),
        folderRepository.getFolderCount(),
        currentTheme
    ) { noteCount, folderCount, theme ->
        SettingsUiState(
            noteCount = noteCount,
            folderCount = folderCount,
            currentTheme = theme,
            appVersion = getAppVersion()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    /**
     * Change app theme (now persists!)
     */
    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            preferencesManager.setTheme(theme)
        }
    }

    /**
     * Clear all notes (with confirmation)
     */
    fun clearAllNotes() {
        viewModelScope.launch {
            // TODO: Implement - delete all notes from database
            android.util.Log.d("Settings", "Clear all notes requested")
        }
    }

    /**
     * Export all notes
     */
    fun exportNotes() {
        viewModelScope.launch {
            // TODO: Implement - export to JSON or TXT
            android.util.Log.d("Settings", "Export notes requested")
        }
    }

    /**
     * Get app version from BuildConfig
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}