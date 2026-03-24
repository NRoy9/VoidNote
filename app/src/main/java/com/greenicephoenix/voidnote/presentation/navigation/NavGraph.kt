package com.greenicephoenix.voidnote.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.greenicephoenix.voidnote.presentation.archive.ArchiveScreen
import com.greenicephoenix.voidnote.presentation.changelog.ChangelogScreen
import com.greenicephoenix.voidnote.presentation.editor.NoteEditorScreen
import com.greenicephoenix.voidnote.presentation.folders.FolderNotesScreen
import com.greenicephoenix.voidnote.presentation.folders.FoldersScreen
import com.greenicephoenix.voidnote.presentation.notes.NotesListScreen
import com.greenicephoenix.voidnote.presentation.search.SearchScreen
import com.greenicephoenix.voidnote.presentation.settings.ChangeVaultPasswordScreen
import com.greenicephoenix.voidnote.presentation.settings.ImportBackupScreen
import com.greenicephoenix.voidnote.presentation.settings.SettingsScreen
import com.greenicephoenix.voidnote.presentation.splash.SplashScreen
import com.greenicephoenix.voidnote.presentation.trash.TrashScreen
import com.greenicephoenix.voidnote.presentation.vault.VaultSetupScreen
import com.greenicephoenix.voidnote.presentation.vault.VaultUnlockScreen
import com.greenicephoenix.voidnote.presentation.vault.RestoreBackupScreen
import com.greenicephoenix.voidnote.presentation.tags.TagsScreen
import com.greenicephoenix.voidnote.presentation.settings.ExportNotesScreen
import com.greenicephoenix.voidnote.presentation.settings.SupportScreen
import com.greenicephoenix.voidnote.presentation.diary.DiaryScreen
import com.greenicephoenix.voidnote.presentation.settings.MigratorScreen

/**
 * SetupNavGraph — the complete navigation map for Void Note.
 *
 * Sprint 12 adds a bottom navigation bar with 4 items:
 *   Notes · Search · Journal · Settings
 *
 * The bar is shown ONLY on the four primary destinations.
 * All other screens (editor, trash, folders, etc.) are full-screen
 * with their own TopBar and a back arrow — no bottom nav.
 *
 * The bar is implemented here at the NavGraph level using a Scaffold
 * that wraps the NavHost. currentBackStackEntryAsState() gives us the
 * current route so we can decide whether to show the bar and which
 * item to highlight.
 */

// Routes where the bottom nav bar should be visible
private val bottomNavRoutes = setOf(
    Screen.NotesList.route,
    Screen.Search.route,
    Screen.Diary.route,
    Screen.Settings.route
)

@Composable
fun SetupNavGraph(navController: NavHostController) {

    // Observe the current back stack entry so we can react to route changes
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route
    val showBottomNav     = currentRoute in bottomNavRoutes

    Scaffold(
        // Critical: don't consume window insets at the NavGraph level.
        // Each screen handles its own insets. Without this, the outer Scaffold
        // consumes the system nav bar inset, causing navigationBarsPadding()
        // inside the NavHost to return 0 — producing the black gap below the
        // editor's formatting toolbar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        bottomBar = {
            if (showBottomNav) {
                VoidNoteBottomNav(
                    currentRoute = currentRoute,
                    onNavigate   = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.NotesList.route) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->

        NavHost(
            navController    = navController,
            startDestination = Screen.Splash.route,
            modifier         = androidx.compose.ui.Modifier.padding(innerPadding)
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
            // CHANGE VaultSetup composable to pass onNavigateToImport:
            composable(Screen.VaultSetup.route) {
                VaultSetupScreen(
                    onVaultCreated     = {
                        navController.navigate(Screen.NotesList.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToImport = {                           // ← ADD
                        navController.navigate(Screen.RestoreBackup.route)
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

            // ADD new composable after VaultUnlock:
            composable(Screen.RestoreBackup.route) {
                RestoreBackupScreen(
                    onNavigateBack    = { navController.popBackStack() },
                    onRestoreComplete = {
                        navController.navigate(Screen.NotesList.route) {
                            popUpTo(0) { inclusive = true }
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
                    onNavigateToFolders = { navController.navigate(Screen.Folders.route) },
                    onNavigateToFolderNotes = { folderId ->
                        navController.navigate(Screen.FolderNotes.createRoute(folderId))
                    },
                    onNavigateToTags = { navController.navigate(Screen.Tags.route) }
                )
            }

            // ── Note Editor ───────────────────────────────────────────────────────
            composable(
                route     = Screen.NoteEditor.route,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) {
                NoteEditorScreen(
                    onNavigateBack     = { navController.popBackStack() },
                    // Linked note taps — push onto stack so back returns here
                    onNavigateToEditor = { noteId ->
                        navController.navigate(Screen.NoteEditor.createRoute(noteId))
                    },
                    // Diary prev/next — replace current editor so back returns to calendar
                    onNavigateToDiaryEntry = { noteId ->
                        navController.navigate(Screen.NoteEditor.createRoute(noteId)) {
                            popUpTo(Screen.NoteEditor.route) { inclusive = true }
                        }
                    }
                )
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
                    onNavigateBack            = { navController.popBackStack() },
                    onNavigateToTrash         = { navController.navigate(Screen.Trash.route) },
                    onNavigateToArchive       = { navController.navigate(Screen.Archive.route) },
                    onNavigateToChangelog     = { navController.navigate(Screen.Changelog.route) },
                    onNavigateToExport         = { navController.navigate(Screen.ExportNotes.route) },
                    onNavigateToImport        = { navController.navigate(Screen.ImportBackup.route) },
                    onNavigateToChangePassword = { navController.navigate(Screen.ChangeVaultPassword.route) },
                    onNavigateToSupport       = { navController.navigate(Screen.Support.route) },
                    onNavigateToMigrator       = { navController.navigate(Screen.Migrator.route) }
                )
            }

            // ── Support the Developer ─────────────────────────────────────────────
            composable(Screen.Support.route) {
                SupportScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Export Notes (Settings → Data Management → Export Notes) ──────────
            composable(Screen.ExportNotes.route) {
                ExportNotesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Import Backup (Flow B — Settings → Data Management) ───────────────
            composable(Screen.ImportBackup.route) {
                ImportBackupScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Migrator.route) {
                MigratorScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Change Vault Password (Settings → Security) ───────────────────────
            composable(Screen.ChangeVaultPassword.route) {
                ChangeVaultPasswordScreen(
                    onNavigateBack = { navController.popBackStack() }
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
                TagsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNoteClick    = { noteId ->
                        navController.navigate(Screen.NoteEditor.createRoute(noteId))
                    }
                )
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

            // ── Journal / Diary (Sprint 12) ───────────────────────────────────────
            composable(Screen.Diary.route) {
                DiaryScreen(
                    onNavigateToEditor = { noteId ->
                        navController.navigate(Screen.NoteEditor.createRoute(noteId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

        } // end NavHost
    }     // end Scaffold content
}         // end SetupNavGraph