package com.greenicephoenix.voidnote.presentation.navigation

/**
 * Screen — Sealed class representing every navigation destination in the app.
 *
 * WHY A SEALED CLASS?
 * A sealed class is like an enum but richer — each subclass can have its own
 * properties and functions. The compiler knows all possible subclasses at
 * compile time, so when you write `when (screen) { ... }` you get an error if
 * you forget a case. This makes navigation refactoring safe.
 *
 * WHY DATA OBJECTS?
 * 'data object' (Kotlin 1.9+) gives us equals/hashCode/toString for free on
 * singleton objects. They're functionally identical to regular 'object' here
 * but more explicit.
 *
 * SPRINT 3 CHANGE: Added Screen.Onboarding.
 * The full navigation decision tree is:
 *   Cold start → Splash → [first launch?] Onboarding → NotesList
 *                        → [returning user] NotesList
 */
sealed class Screen(val route: String) {

    /** Splash Screen — shown on cold start, animates then decides where to go. */
    data object Splash : Screen("splash")

    /**
     * Onboarding — 3-page first-launch introduction.
     *
     * SPRINT 3 — new. Shown only once, ever. SplashViewModel reads
     * PreferencesManager.onboardingCompletedFlow to decide if this is needed.
     * After "Get Started" or "Skip", onboarding is marked complete and the
     * user navigates to NotesList. Never shown again on subsequent launches.
     */
    data object Onboarding : Screen("onboarding")

    /** Notes List — main home screen. The hub of the app. */
    data object NotesList : Screen("notes_list")

    /**
     * Note Editor — create or open a note.
     *
     * ROUTE ARGUMENT: noteId (String)
     * - "new" = create a brand-new note
     * - any UUID string = open an existing note
     *
     * createRoute() is a helper function so callers don't build the URL manually.
     * Usage: navController.navigate(Screen.NoteEditor.createRoute("new"))
     *        navController.navigate(Screen.NoteEditor.createRoute(note.id))
     */
    data object NoteEditor : Screen("note_editor/{noteId}") {
        fun createRoute(noteId: String = "new") = "note_editor/$noteId"
    }

    /**
     * Folder Notes — all notes inside a specific folder.
     *
     * ROUTE ARGUMENT: folderId (String UUID)
     * FolderNotesScreen reads this via savedStateHandle in its ViewModel.
     */
    data object FolderNotes : Screen("folder_notes/{folderId}") {
        fun createRoute(folderId: String) = "folder_notes/$folderId"
    }

    /** Settings — theme, security, data management, export, about. */
    data object Settings : Screen("settings")

    /** Search — full-text search across notes, tags, folder names, checklist text. */
    data object Search : Screen("search")

    /** Folders — manage and browse all folders. */
    data object Folders : Screen("folders")

    /** Tags — tag management (planned, placeholder until Sprint 4). */
    data object Tags : Screen("tags")

    /** Trash — soft-deleted notes. Auto-purged after 30 days. */
    data object Trash : Screen("trash")

    /** Archive — notes hidden from main list but never auto-deleted. */
    data object Archive : Screen("archive")

    /** Changelog — full version history, accessed from Settings → About. */
    data object Changelog : Screen("changelog")
}