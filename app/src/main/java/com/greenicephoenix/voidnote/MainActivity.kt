package com.greenicephoenix.voidnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.greenicephoenix.voidnote.presentation.navigation.SetupNavGraph
import com.greenicephoenix.voidnote.presentation.settings.AppTheme
import com.greenicephoenix.voidnote.presentation.theme.VoidNoteTheme
import com.greenicephoenix.voidnote.presentation.theme.rememberThemeState
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.setValue
import com.greenicephoenix.voidnote.presentation.theme.rememberThemeState

/**
 * MainActivity - Entry point of the app
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            // Observe theme preference from DataStore
            val currentTheme by rememberThemeState()

            // Determine dark theme based on user preference
            val isDarkTheme = when (currentTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.EXTRA_DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            // Determine if extra dark (OLED)
            val isExtraDark = currentTheme == AppTheme.EXTRA_DARK

            VoidNoteTheme(darkTheme = isDarkTheme, extraDark = isExtraDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    SetupNavGraph(navController = navController)
                }
            }
        }
    }
}