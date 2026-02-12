package com.greenicephoenix.voidnote.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.presentation.settings.AppTheme

/**
 * Remember and observe theme preference
 */
@Composable
fun rememberThemeState(): State<AppTheme> {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    return preferencesManager.themeFlow.collectAsState(initial = AppTheme.DARK)
}