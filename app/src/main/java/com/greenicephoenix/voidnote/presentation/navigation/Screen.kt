package com.greenicephoenix.voidnote.presentation.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 *
 * A sealed class means only the subclasses defined here can exist —
 * the compiler can exhaustively check all cases in when expressions.
 *
 * Each object has a route string used by NavHost to identify the destination.
 */
sealed class Screen(val route: String) {

    /** Splash Screen — shown on launch, animates then auto-navigates */
    data object Splash : Screen("splash")

    /** Notes List — main home screen */
    data object NotesList : Screen("notes_list")

    /** Note Editor — create or edit a specific note. Requires noteId arg. */
    data object NoteEditor : Screen("note_editor/{noteId}") {
        fun createRoute(noteId: String = "new") = "note_editor/$noteId"
    }

    /** Folder Notes — all notes inside a specific folder. Requires folderId arg. */
    data object FolderNotes : Screen("folder_notes/{folderId}") {
        fun createRoute(folderId: String) = "folder_notes/$folderId"
    }

    /** Settings — theme, data management, export, about */
    data object Settings : Screen("settings")

    /** Search — full-text search across notes, tags, and folders */
    data object Search : Screen("search")

    /** Folders — folder management screen */
    data object Folders : Screen("folders")

    /** Tags — tag management screen (future) */
    data object Tags : Screen("tags")

    /** Trash — deleted notes, 30-day auto-delete */
    data object Trash : Screen("trash")

    /** Archive — kept notes hidden from main list, never auto-deleted */
    data object Archive : Screen("archive")

    /** Changelog — full version history, accessed from Settings → About */
    data object Changelog : Screen("changelog")
}