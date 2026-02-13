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
import androidx.core.view.WindowCompat  // ✅ ADD THIS IMPORT

/**
 * MainActivity - Entry point of the app
 *
 * Responsibilities:
 * - Theme management (observes user preference from DataStore)
 * - Status bar configuration (light/dark icons)
 * - Edge-to-edge display
 * - Navigation setup
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display (immersive UI)
        enableEdgeToEdge()

        setContent {
            // Observe theme preference from DataStore
            val currentTheme by rememberThemeState()

            // Determine if we should use dark theme
            val isDarkTheme = when (currentTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.EXTRA_DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            // Determine if we should use extra dark (OLED) theme
            val isExtraDark = currentTheme == AppTheme.EXTRA_DARK

            // ✅ FIX STATUS BAR ICONS (light icons for dark themes, dark icons for light theme)
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
            // Explanation:
            // - When isDarkTheme = true → isAppearanceLightStatusBars = false → WHITE icons
            // - When isDarkTheme = false → isAppearanceLightStatusBars = true → DARK icons

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