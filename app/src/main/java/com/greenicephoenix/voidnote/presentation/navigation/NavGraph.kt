package com.greenicephoenix.voidnote.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.greenicephoenix.voidnote.presentation.archive.ArchiveScreen
import com.greenicephoenix.voidnote.presentation.editor.NoteEditorScreen
import com.greenicephoenix.voidnote.presentation.folders.FolderNotesScreen
import com.greenicephoenix.voidnote.presentation.folders.FoldersScreen
import com.greenicephoenix.voidnote.presentation.notes.NotesListScreen
import com.greenicephoenix.voidnote.presentation.search.SearchScreen
import com.greenicephoenix.voidnote.presentation.settings.SettingsScreen
import com.greenicephoenix.voidnote.presentation.splash.SplashScreen
import com.greenicephoenix.voidnote.presentation.changelog.ChangelogScreen
import com.greenicephoenix.voidnote.presentation.trash.TrashScreen

/**
 * Navigation Graph — the "map" connecting all screens.
 *
 * Each composable() block registers a destination with:
 * - A route string (from Screen sealed class — type safe)
 * - Optional arguments (e.g. noteId, folderId)
 * - The screen composable to render
 * - Navigation callbacks so screens don't depend on NavController directly
 *
 * WHY PASS LAMBDAS INSTEAD OF NAVCONTROLLER?
 * If we passed NavController into each screen, the screens would be tightly
 * coupled to navigation. By passing lambdas, each screen only knows "call
 * this when you want to go somewhere" — it doesn't know about the router.
 * This makes screens independently testable and reusable.
 */
@Composable
fun SetupNavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {

        // ── Splash ────────────────────────────────────────────────────────
        composable(route = Screen.Splash.route) {
            SplashScreen(
                onNavigateToNotes = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Notes List ────────────────────────────────────────────────────
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

        // ── Note Editor ───────────────────────────────────────────────────
        composable(
            route = Screen.NoteEditor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            NoteEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Folder Notes ──────────────────────────────────────────────────
        composable(
            route = Screen.FolderNotes.route,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
            FolderNotesScreen(
                folderId = folderId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                }
            )
        }

        // ── Settings ──────────────────────────────────────────────────────
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTrash = { navController.navigate(Screen.Trash.route) },
                onNavigateToArchive = { navController.navigate(Screen.Archive.route) },
                onNavigateToChangelog = { navController.navigate(Screen.Changelog.route) }
            )
        }

        // ── Search ────────────────────────────────────────────────────────
        composable(route = Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNoteClick = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                },
                onFolderClick = { folderId ->
                    navController.navigate(Screen.FolderNotes.createRoute(folderId))
                }
            )
        }

        // ── Folders ───────────────────────────────────────────────────────
        composable(route = Screen.Folders.route) {
            FoldersScreen(
                onNavigateBack = { navController.popBackStack() },
                onFolderClick = { folderId ->
                    navController.navigate(Screen.FolderNotes.createRoute(folderId))
                }
            )
        }

        // ── Tags (placeholder) ────────────────────────────────────────────
        composable(route = Screen.Tags.route) {
            // TODO: TagsScreen — Sprint 3
        }

        // ── Trash ─────────────────────────────────────────────────────────
        composable(route = Screen.Trash.route) {
            TrashScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Archive ───────────────────────────────────────────────────────
        composable(route = Screen.Archive.route) {
            ArchiveScreen(
                onNavigateBack = { navController.popBackStack() },
                // Tap a card → open it in the editor (fully readable and editable)
                // The editor's ⋮ menu has Unarchive, so the user can restore from there too.
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                }
            )
        }

        // ── Changelog ─────────────────────────────────────────────────────
        // Full version history — all releases, newest first.
        // Navigated to from Settings → About → "What's New"
        composable(route = Screen.Changelog.route) {
            ChangelogScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}