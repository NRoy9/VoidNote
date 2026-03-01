package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.presentation.settings.AppTheme

/**
 * Remember and observe theme preference as reactive State.
 *
 * Used by MainActivity to drive VoidNoteTheme(darkTheme = ...).
 *
 * WHY SYSTEM AS INITIAL VALUE?
 * DataStore is asynchronous — before the first emission there is a brief
 * window where Compose needs an initial value to render.
 *
 * Previously this was AppTheme.DARK, which caused a visible flash on light-mode
 * devices: the app would render dark for one frame then snap to light.
 *
 * With AppTheme.SYSTEM as initial:
 * - Light-mode OS → initial renders light → DataStore emits SYSTEM → stays light ✓
 * - Dark-mode OS  → initial renders dark  → DataStore emits SYSTEM → stays dark  ✓
 * - No flash in either direction.
 */
@Composable
fun rememberThemeState(): State<AppTheme> {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    return preferencesManager.themeFlow.collectAsState(initial = AppTheme.SYSTEM) // ← was DARK
}