package com.greenicephoenix.voidnote.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.greenicephoenix.voidnote.presentation.archive.ArchiveScreen
import com.greenicephoenix.voidnote.presentation.changelog.ChangelogScreen
import com.greenicephoenix.voidnote.presentation.editor.NoteEditorScreen
import com.greenicephoenix.voidnote.presentation.folders.FolderNotesScreen
import com.greenicephoenix.voidnote.presentation.folders.FoldersScreen
import com.greenicephoenix.voidnote.presentation.notes.NotesListScreen
import com.greenicephoenix.voidnote.presentation.onboarding.OnboardingScreen
import com.greenicephoenix.voidnote.presentation.search.SearchScreen
import com.greenicephoenix.voidnote.presentation.settings.SettingsScreen
import com.greenicephoenix.voidnote.presentation.splash.SplashScreen
import com.greenicephoenix.voidnote.presentation.trash.TrashScreen

/**
 * SetupNavGraph — The routing table for the entire application.
 *
 * WHAT IS A NAV GRAPH?
 * Think of it like a map. Each `composable()` block is a "room" on that map,
 * identified by its route string. NavController is the GPS — you tell it where
 * to go and it handles the journey (back stack, animations, arguments).
 *
 * WHY LAMBDAS INSTEAD OF PASSING NAVCONTROLLER?
 * If we passed NavController directly into each screen, the screens would be
 * tightly coupled to navigation. A screen shouldn't need to know the entire
 * app map to navigate. Instead:
 * - NavGraph knows all routes and wires them up here
 * - Each screen gets simple lambdas: "call this when you want to go somewhere"
 * - Screens are independently testable — no NavController dependency needed
 *
 * SPRINT 3 CHANGES:
 * 1. SplashScreen now has two navigation callbacks (onboarding vs notes list)
 * 2. OnboardingScreen destination added — navigates to NotesList on complete
 * 3. Both splash and onboarding use popUpTo to clear themselves from back stack
 *
 * BACK STACK MANAGEMENT (popUpTo):
 * When navigating from splash → notes, we remove splash from the back stack.
 * Without this, pressing Back from NotesList would return to the splash screen.
 * Same for onboarding → notes.
 *
 * inclusive = true: also removes the destination we're popping to (splash/onboarding)
 * inclusive = false: keeps the destination we're popping to (used when we want
 *                    to go back to a hub screen like NotesList)
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
        // Start destination. Animates for 2.5s, then SplashViewModel decides:
        // first launch → Onboarding, returning user → NotesList.
        composable(route = Screen.Splash.route) {
            SplashScreen(
                onNavigateToNotes = {
                    navController.navigate(Screen.NotesList.route) {
                        // Remove splash from back stack so Back doesn't return here
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        // Remove splash from back stack — onboarding is the new root
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Onboarding ────────────────────────────────────────────────────
        // SPRINT 3 — new. Shown once, on first launch only.
        // After "Get Started" or "Skip", navigates to NotesList and removes
        // itself from the back stack (user can't go back to onboarding).
        composable(route = Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.NotesList.route) {
                        // Remove onboarding from back stack.
                        // After completing onboarding, pressing Back exits the app,
                        // not returning to the onboarding flow.
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Notes List ────────────────────────────────────────────────────
        // Home screen. Hub of the app. All primary navigation radiates from here.
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
        // noteId argument: "new" = create new note, UUID = open existing note.
        // The ViewModel reads noteId from SavedStateHandle to load the note.
        composable(
            route = Screen.NoteEditor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            NoteEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Folder Notes ──────────────────────────────────────────────────
        // Shows all notes belonging to a specific folder.
        // folderId is read by FolderNotesViewModel via SavedStateHandle.
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
        // Sprint 4 — tagging UI will be added here
        composable(route = Screen.Tags.route) {
            // TODO: TagsScreen — Sprint 4
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
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                }
            )
        }

        // ── Changelog ─────────────────────────────────────────────────────
        // Full version history. Accessed from Settings → About → "What's New"
        composable(route = Screen.Changelog.route) {
            ChangelogScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}