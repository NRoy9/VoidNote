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
import com.greenicephoenix.voidnote.presentation.vault.VaultSetupScreen
import com.greenicephoenix.voidnote.presentation.vault.VaultUnlockScreen

/**
 * SetupNavGraph — complete navigation map for Void Note.
 *
 * FIRST INSTALL FLOW:
 *   Splash → Onboarding → VaultSetup → NotesList
 *
 * NORMAL LAUNCH FLOW:
 *   Splash → NotesList   (Keystore unwraps key silently, no screens shown)
 *
 * REINSTALL / FACTORY RESET FLOW:
 *   Splash → VaultUnlock → NotesList
 *
 * ALL gateway screens (Splash, Onboarding, VaultSetup, VaultUnlock) clear
 * the back stack when navigating forward. The user cannot press Back into them.
 * NotesList is always the permanent root once reached.
 */
@Composable
fun SetupNavGraph(navController: NavHostController) {

    NavHost(
        navController    = navController,
        startDestination = Screen.Splash.route
    ) {

        // ── Splash ────────────────────────────────────────────────────────────
        // Reads DataStore + Keystore, decides destination.
        // Animation plays for minimum 2 seconds while decision is made.
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToNotes = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToVaultUnlock = {
                    navController.navigate(Screen.VaultUnlock.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Onboarding ────────────────────────────────────────────────────────
        // 3-page introduction. Skip or Continue on final page → VaultSetup.
        // OnboardingViewModel marks onboardingComplete and lastSeenVersion here,
        // so What's New dialog doesn't fire immediately after onboarding.
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onCompleted = {
                    navController.navigate(Screen.VaultSetup.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Vault Setup ───────────────────────────────────────────────────────
        // Cannot be skipped. Creates the vault password.
        // After creation, routes to NotesList — clears entire back stack.
        composable(Screen.VaultSetup.route) {
            VaultSetupScreen(
                onVaultCreated = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Vault Unlock ──────────────────────────────────────────────────────
        // Shown only when Keystore key is gone (reinstall/factory reset).
        // User enters vault password → key re-derived → NotesList.
        composable(Screen.VaultUnlock.route) {
            VaultUnlockScreen(
                onUnlocked = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Notes List ────────────────────────────────────────────────────────
        composable(Screen.NotesList.route) {
            NotesListScreen(
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                },
                onNavigateToSearch    = { navController.navigate(Screen.Search.route) },
                onNavigateToSettings  = { navController.navigate(Screen.Settings.route) },
                onNavigateToFolders   = { navController.navigate(Screen.Folders.route) },
                onNavigateToFolderNotes = { folderId ->
                    navController.navigate(Screen.FolderNotes.createRoute(folderId))
                }
            )
        }

        // ── Note Editor ───────────────────────────────────────────────────────
        composable(
            route     = Screen.NoteEditor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            NoteEditorScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Folder Notes ──────────────────────────────────────────────────────
        composable(
            route     = Screen.FolderNotes.route,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
            FolderNotesScreen(
                folderId        = folderId,
                onNavigateBack  = { navController.popBackStack() },
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                }
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack      = { navController.popBackStack() },
                onNavigateToTrash   = { navController.navigate(Screen.Trash.route) },
                onNavigateToArchive = { navController.navigate(Screen.Archive.route) },
                onNavigateToChangelog = { navController.navigate(Screen.Changelog.route) }
            )
        }

        // ── Search ────────────────────────────────────────────────────────────
        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNoteClick    = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                },
                onFolderClick  = { folderId ->
                    navController.navigate(Screen.FolderNotes.createRoute(folderId))
                }
            )
        }

        // ── Folders ───────────────────────────────────────────────────────────
        composable(Screen.Folders.route) {
            FoldersScreen(
                onNavigateBack = { navController.popBackStack() },
                onFolderClick  = { folderId ->
                    navController.navigate(Screen.FolderNotes.createRoute(folderId))
                }
            )
        }

        // ── Tags ──────────────────────────────────────────────────────────────
        composable(Screen.Tags.route) {
            // TODO: TagsScreen — Sprint 5
        }

        // ── Trash ─────────────────────────────────────────────────────────────
        composable(Screen.Trash.route) {
            TrashScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Archive ───────────────────────────────────────────────────────────
        composable(Screen.Archive.route) {
            ArchiveScreen(
                onNavigateBack     = { navController.popBackStack() },
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                }
            )
        }

        // ── Changelog ─────────────────────────────────────────────────────────
        composable(Screen.Changelog.route) {
            ChangelogScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}