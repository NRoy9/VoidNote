package com.greenicephoenix.voidnote.presentation.navigation

/**
 * Sealed class representing all screens/destinations in the app
 *
 * Sealed classes are perfect for navigation because:
 * - Compiler knows all possible screens (exhaustive when statements)
 * - Type-safe navigation
 * - Easy to add new screens
 *
 * Each screen has a route (unique identifier for navigation)
 */
sealed class Screen(val route: String) {

    /**
     * Splash Screen - First screen shown when app launches
     * Shows Nothing-inspired animation
     */
    data object Splash : Screen("splash")

    /**
     * Notes List Screen - Main screen showing all notes
     * This is the "home" screen after splash
     */
    data object NotesList : Screen("notes_list")

    /**
     * Folder Notes Screen - View notes in a specific folder
     * Takes a folderId parameter
     */
    data object FolderNotes : Screen("folder_notes/{folderId}") {
        fun createRoute(folderId: String) = "folder_notes/$folderId"
    }

    /**
     * Note Editor Screen - Create/edit a note
     * Takes a noteId parameter (null for new notes)
     */
    data object NoteEditor : Screen("note_editor/{noteId}") {
        /**
         * Create route with actual noteId
         * Example: note_editor/123 or note_editor/new
         */
        fun createRoute(noteId: String = "new") = "note_editor/$noteId"
    }

    /**
     * Settings Screen - App settings and preferences
     */
    data object Settings : Screen("settings")

    /**
     * Search Screen - Search through notes
     */
    data object Search : Screen("search")

    /**
     * Folders Screen - Manage folders
     */
    data object Folders : Screen("folders")

    /**
     * Tags Screen - Manage tags
     */
    data object Tags : Screen("tags")

    /**
     * Trash Screen - Deleted notes
     */
    data object Trash : Screen("trash")
}