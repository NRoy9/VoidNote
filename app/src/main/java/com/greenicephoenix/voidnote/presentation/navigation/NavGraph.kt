package com.greenicephoenix.voidnote.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.greenicephoenix.voidnote.presentation.editor.NoteEditorScreen
import com.greenicephoenix.voidnote.presentation.folders.FoldersScreen
import com.greenicephoenix.voidnote.presentation.notes.NotesListScreen
import com.greenicephoenix.voidnote.presentation.splash.SplashScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.greenicephoenix.voidnote.presentation.folders.FolderNotesScreen
import com.greenicephoenix.voidnote.presentation.search.SearchScreen
import com.greenicephoenix.voidnote.presentation.settings.SettingsScreen
import com.greenicephoenix.voidnote.presentation.trash.TrashScreen

/**
 * Navigation Graph - Defines all navigation routes and transitions
 *
 * This is the "map" of our app - it connects all screens together
 *
 * @param navController Controls navigation between screens
 */
@Composable
fun SetupNavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route // App starts at splash screen
    ) {

        // Splash Screen - Entry point
        composable(route = Screen.Splash.route) {
            SplashScreen(
                onNavigateToNotes = {
                    // Navigate to notes list and remove splash from back stack
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Notes List Screen - Main screen
        composable(route = Screen.NotesList.route) {
            NotesListScreen(
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToFolders = {
                    navController.navigate(Screen.Folders.route)
                },
                onNavigateToFolderNotes = { folderId ->
                    navController.navigate(Screen.FolderNotes.createRoute(folderId))
                }
            )
        }

        // Note Editor Screen
        composable(
            route = Screen.NoteEditor.route
        ) { backStackEntry ->
            NoteEditorScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Folder Notes Screen - View notes in a folder
        composable(
            route = Screen.FolderNotes.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable

            FolderNotesScreen(
                folderId = folderId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                }
            )
        }

        // Settings Screen
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTrash = {  // ✅ ADD THIS
                    navController.navigate(Screen.Trash.route)
                }
            )
        }

        // Search Screen
        composable(route = Screen.Search.route) {
            SearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                },
                onFolderClick = { folderId ->
                    navController.navigate(Screen.FolderNotes.createRoute(folderId))
                }
            )
        }

        // Folders Screen
        composable(route = Screen.Folders.route) {
            FoldersScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onFolderClick = { folderId ->
                    // TODO: Navigate to folder notes view (we'll implement this next)
                    // For now, just go back
                    navController.popBackStack()
                }
            )
        }

        // Tags Screen (we'll implement this later)
        composable(route = Screen.Tags.route) {
            // TODO: TagsScreen()
        }

        // NEW: Trash Screen
        composable(route = Screen.Trash.route) {
            TrashScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}