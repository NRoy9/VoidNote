package com.greenicephoenix.voidnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.greenicephoenix.voidnote.presentation.navigation.SetupNavGraph
import com.greenicephoenix.voidnote.presentation.theme.VoidNoteTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity - Entry point of the app
 *
 * @AndroidEntryPoint enables Hilt dependency injection in this Activity
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display (content goes under system bars)
        enableEdgeToEdge()

        // Set the content of the activity using Jetpack Compose
        setContent {
            VoidNoteTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Create navigation controller
                    val navController = rememberNavController()

                    // Setup navigation graph
                    SetupNavGraph(navController = navController)
                }
            }
        }
    }
}