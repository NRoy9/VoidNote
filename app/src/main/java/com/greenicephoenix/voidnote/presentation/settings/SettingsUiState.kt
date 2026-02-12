package com.greenicephoenix.voidnote.presentation.settings

/**
 * UI State for Settings Screen
 */
data class SettingsUiState(
    val noteCount: Int = 0,
    val folderCount: Int = 0,
    val currentTheme: AppTheme = AppTheme.DARK,
    val appVersion: String = "1.0.0"
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