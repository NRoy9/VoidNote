package com.greenicephoenix.voidnote.presentation.navigation

/**
 * Screen — sealed class of all navigation destinations.
 *
 * SPRINT 4 ADDITIONS:
 * - VaultSetup  — shown after onboarding, once, to create the vault password
 * - VaultUnlock — shown on reinstall/factory reset to re-derive the key
 */
sealed class Screen(val route: String) {

    data object Splash      : Screen("splash")
    data object Onboarding  : Screen("onboarding")
    data object VaultSetup  : Screen("vault_setup")    // ← NEW
    data object VaultUnlock : Screen("vault_unlock")   // ← NEW
    data object NotesList   : Screen("notes_list")
    data object RestoreBackup  : Screen("restore_backup")

    data object NoteEditor  : Screen("note_editor/{noteId}") {
        fun createRoute(noteId: String = "new") = "note_editor/$noteId"
    }

    data object FolderNotes : Screen("folder_notes/{folderId}") {
        fun createRoute(folderId: String) = "folder_notes/$folderId"
    }

    data object Settings            : Screen("settings")
    data object ExportNotes         : Screen("export_notes")
    data object ImportBackup        : Screen("import_backup")       // Settings → Data Management → Import Backup
    data object ChangeVaultPassword : Screen("change_vault_password")   // Settings → Security → Change Password
    data object Search      : Screen("search")
    data object Folders     : Screen("folders")
    data object Tags        : Screen("tags")
    data object Trash       : Screen("trash")
    data object Archive     : Screen("archive")
    data object Changelog   : Screen("changelog")
}