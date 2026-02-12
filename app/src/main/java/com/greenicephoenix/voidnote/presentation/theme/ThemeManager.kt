package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.presentation.settings.AppTheme

/**
 * Theme Manager - Provides current theme to the app
 *
 * This allows the entire app to react to theme changes
 */
@Composable
fun rememberThemePreference(): AppTheme {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val theme by preferencesManager.themeFlow.collectAsState(initial = AppTheme.DARK)
    return theme
}