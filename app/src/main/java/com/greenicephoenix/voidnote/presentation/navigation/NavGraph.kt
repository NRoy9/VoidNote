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
import com.greenicephoenix.voidnote.presentation.search.SearchScreen
import com.greenicephoenix.voidnote.presentation.settings.SettingsScreen
import com.greenicephoenix.voidnote.presentation.splash.SplashScreen
import com.greenicephoenix.voidnote.presentation.trash.TrashScreen
import com.greenicephoenix.voidnote.presentation.vault.VaultSetupScreen
import com.greenicephoenix.voidnote.presentation.vault.VaultUnlockScreen

/**
 * SetupNavGraph — the complete navigation map for Void Note.
 *
 * ─── THE BACK STACK BUG AND FIX ───────────────────────────────────────────────
 *
 * WHAT WAS WRONG:
 * Every place that navigated to NotesList from the onboarding/vault flow used:
 *
 *   popUpTo(Screen.Splash.route) { inclusive = true }
 *
 * This worked only for the Splash → NotesList path (returning users).
 * For first-launch users, the flow was:
 *
 *   Splash ──[popUpTo(Splash)]──► Onboarding
 *                                      │
 *                          [popUpTo(Onboarding)]
 *                                      ▼
 *                                 VaultSetup
 *                                      │
 *                      [popUpTo(Splash) ← BUG! Splash is already gone]
 *                                      ▼
 *                                 NotesList
 *
 * Because Splash was already cleared from the stack before VaultSetup ran,
 * popUpTo(Splash) was a NO-OP. NotesList was pushed on top of VaultSetup,
 * leaving the stack as: [VaultSetup, NotesList].
 *
 * Pressing back from NotesList popped it, revealing VaultSetup. ✗
 *
 * THE FIX:
 * Use popUpTo(0) { inclusive = true } everywhere that navigates to NotesList
 * as a final destination.
 *
 *   popUpTo(0) means "pop all the way to the root of the NavHost graph".
 *
 * Unlike a named route, `0` always refers to the graph root itself — it
 * works regardless of which screens are currently in the stack. Every screen
 * that was pushed since app launch is cleared, and NotesList becomes the
 * only entry in the back stack.
 *
 * Result: pressing back from NotesList has nowhere to go → moveTaskToBack()
 * in NotesListScreen's BackHandler sends the app to the background. ✓
 *
 * ─── ONE-WAY GATES ────────────────────────────────────────────────────────────
 *
 * Splash, Onboarding, VaultSetup, VaultUnlock are one-way gates.
 * Once the user passes through, they cannot back-navigate into them.
 * NotesList is the permanent root after first launch.
 */
@Composable
fun SetupNavGraph(navController: NavHostController) {

    NavHost(
        navController    = navController,
        startDestination = Screen.Splash.route
    ) {

        // ── Splash ────────────────────────────────────────────────────────────
        //
        // Three exit paths:
        //   onNavigateToNotes     — returning user, vault already set up
        //   onNavigateToOnboarding — first launch, no onboarding done yet
        //   onNavigateToVaultUnlock — reinstall/factory reset, key is gone
        //
        // All three use popUpTo(0) so Splash is never in the back stack.
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToNotes = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToVaultUnlock = {
                    navController.navigate(Screen.VaultUnlock.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Onboarding ────────────────────────────────────────────────────────
        //
        // Shown once on first launch. After completion → VaultSetup.
        // popUpTo(0) clears Onboarding from the stack so it can't be
        // navigated back to after the user reaches VaultSetup.
        composable(Screen.Onboarding.route) {
            com.greenicephoenix.voidnote.presentation.onboarding.OnboardingScreen(
                onCompleted = {
                    navController.navigate(Screen.VaultSetup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Vault Setup ───────────────────────────────────────────────────────
        //
        // Cannot be skipped. User MUST create a vault password to proceed.
        //
        // FIX APPLIED HERE: popUpTo(0) instead of popUpTo(Screen.Splash.route).
        //
        // WHY popUpTo(0)?
        // By the time VaultSetup's onVaultCreated fires, the back stack is:
        //   [VaultSetup]   (Splash and Onboarding are already gone)
        //
        // popUpTo(Screen.Splash.route) would be a NO-OP because Splash is not
        // in the stack. NotesList would be pushed on top of VaultSetup, leaving
        // [VaultSetup, NotesList] — pressing back would reveal VaultSetup. ✗
        //
        // popUpTo(0) always pops everything to the graph root, regardless of
        // what names are or aren't in the stack. NotesList becomes the only
        // entry. Pressing back calls moveTaskToBack() via BackHandler. ✓
        composable(Screen.VaultSetup.route) {
            VaultSetupScreen(
                onVaultCreated = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(0) { inclusive = true }   // ← THE FIX
                    }
                }
            )
        }

        // ── Vault Unlock ──────────────────────────────────────────────────────
        //
        // Shown on reinstall or factory reset when the Keystore key is gone.
        // Same fix applied here — same reasoning as VaultSetup above.
        composable(Screen.VaultUnlock.route) {
            VaultUnlockScreen(
                onUnlocked = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(0) { inclusive = true }   // ← THE FIX
                    }
                }
            )
        }

        // ── Notes List ────────────────────────────────────────────────────────
        //
        // Home screen. Permanent root after first launch.
        // BackHandler inside NotesListScreen calls moveTaskToBack(true) on
        // system back press — sends the app to background rather than finishing.
        composable(Screen.NotesList.route) {
            NotesListScreen(
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                },
                onNavigateToSearch      = { navController.navigate(Screen.Search.route) },
                onNavigateToSettings    = { navController.navigate(Screen.Settings.route) },
                onNavigateToFolders     = { navController.navigate(Screen.Folders.route) },
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
                folderId          = folderId,
                onNavigateBack    = { navController.popBackStack() },
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                }
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack        = { navController.popBackStack() },
                onNavigateToTrash     = { navController.navigate(Screen.Trash.route) },
                onNavigateToArchive   = { navController.navigate(Screen.Archive.route) },
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
            // TODO: TagsScreen — future sprint
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