package com.greenicephoenix.voidnote.presentation.settings

/**
 * UI State for Settings Screen
 */
data class SettingsUiState(
    val noteCount: Int = 0,
    val folderCount: Int = 0,
    val archiveCount: Int = 0,   // How many notes are currently archived
    val trashCount: Int = 0,     // How many notes are currently in the trash
    val currentTheme: AppTheme = AppTheme.DARK,
    val appVersion: String = "1.0.0",
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle
)

/**
 * App Theme Options
 */
enum class AppTheme(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    EXTRA_DARK("Extra Dark (OLED)"),
    SYSTEM("System Default")
}

/**
 * State machine for the "Check for Updates" row.
 *
 * Idle      — initial state, row shows "Tap to check"
 * Checking  — network request in flight, row shows spinner
 * UpToDate  — GitHub confirms the installed version is current
 * Available — a newer version exists on GitHub; carries version string + download URL
 * Error     — network or parse failure (non-critical; user can retry)
 */
sealed class UpdateCheckState {
    object Idle      : UpdateCheckState()
    object Checking  : UpdateCheckState()
    object UpToDate  : UpdateCheckState()
    data class Available(val version: String, val downloadUrl: String) : UpdateCheckState()
    object Error     : UpdateCheckState()
}