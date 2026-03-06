package com.greenicephoenix.voidnote.domain.model

/**
 * NoteSort — The available sort orders for the Notes List.
 *
 * STORAGE:
 * The selected sort is stored as a String (enum name) in DataStore via
 * PreferencesManager.noteSortFlow. Default is UPDATED_DESC (most recent first).
 *
 * HOW IT WORKS:
 * The DAO always returns notes in the default DB order (isPinned DESC, updatedAt DESC).
 * NotesListViewModel re-sorts the list in memory after collecting from the DB.
 * This avoids needing multiple DAO queries with different ORDER BY clauses.
 *
 * WHY IN-MEMORY SORT?
 * The note list is typically < 500 items for a personal notes app. In-memory
 * sort using Kotlin's sortedBy/sortedByDescending is negligible — far cheaper
 * than the database read + decryption cost already happening.
 *
 * PINNED NOTES ALWAYS FIRST:
 * Regardless of the selected sort, pinned notes always appear before unpinned.
 * This is enforced in NotesListViewModel by sorting in two groups:
 *   pinned notes (in selected order) + unpinned notes (in selected order)
 *
 * @param label  Display label shown in the sort menu.
 */
enum class NoteSort(val label: String) {
    UPDATED_DESC("Last modified"),   // Default — most recently edited first
    CREATED_DESC("Date created"),    // Most recently created first
    TITLE_ASC("Title A → Z"),        // Alphabetical ascending
    TITLE_DESC("Title Z → A");       // Alphabetical descending

    companion object {
        /** Safely parse a stored string back to NoteSort. Defaults to UPDATED_DESC. */
        fun fromString(value: String?): NoteSort {
            return entries.find { it.name == value } ?: UPDATED_DESC
        }
    }
}