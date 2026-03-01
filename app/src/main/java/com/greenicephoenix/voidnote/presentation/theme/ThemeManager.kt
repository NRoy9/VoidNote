package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.presentation.settings.AppTheme

/**
 * Provides the current theme preference as a plain value (not State).
 *
 * Used in places that need a direct AppTheme value rather than a State<AppTheme>.
 *
 * Initial value changed to AppTheme.SYSTEM to match rememberThemeState()
 * and PreferencesManager — all three must agree on the default to avoid
 * inconsistency during the async DataStore load window.
 */
@Composable
fun rememberThemePreference(): AppTheme {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val theme by preferencesManager.themeFlow.collectAsState(initial = AppTheme.SYSTEM) // ← was DARK
    return theme
}