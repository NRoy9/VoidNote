package com.greenicephoenix.voidnote.presentation.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * FolderNotesViewModel — manages all state for the folder notes screen.
 *
 * SPRINT 3 FIX #1 — Live rename:
 * Previously loadFolder() called getFolderById() once (suspend, one-shot) and
 * then collected the notes flow. The folder name was captured once and frozen —
 * renaming wrote to the DB but the UI never saw it.
 *
 * Fix: use observeFolder() (a Flow) and combine() it with the notes Flow.
 * combine() merges two flows into one: whenever EITHER the folder OR the notes
 * change, a new FolderNotesUiState is emitted. So a rename triggers a new
 * emission from the folder flow → combine() runs → folderName in UI updates
 * immediately, with no navigation required.
 *
 * SPRINT 3 FIX #2 — Delete with choice:
 * confirmDelete() now takes a Boolean: deleteNotes.
 * - deleteNotes = false → move notes to root (folderId = null), delete folder
 * - deleteNotes = true  → permanently delete all notes, delete folder
 * The dialog drives this choice via a checkbox (see FolderNotesScreen).
 */
@HiltViewModel
class FolderNotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    // ── Primary UI state ──────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(FolderNotesUiState())
    val uiState: StateFlow<FolderNotesUiState> = _uiState.asStateFlow()

    // ── Rename dialog ─────────────────────────────────────────────────────
    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()

    private val _renameText = MutableStateFlow("")
    val renameText: StateFlow<String> = _renameText.asStateFlow()

    // ── Delete dialog ─────────────────────────────────────────────────────
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    // Tracks the current folder ID for rename/delete operations
    private var currentFolderId: String = ""

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD — combine folder + notes into a single reactive state
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start observing the folder and its notes reactively.
     *
     * HOW combine() WORKS:
     * combine(flowA, flowB) { a, b -> result } creates a new Flow that emits
     * a result whenever EITHER flowA OR flowB emits. Both must have emitted
     * at least once before combine() can produce its first value.
     *
     * Here:
     *   flowA = observeFolder(folderId) — re-emits on rename or delete
     *   flowB = getNotesByFolder(folderId) — re-emits when notes are added/removed
     *
     * Result: any change to the folder name OR the note list updates the UI
     * instantly and automatically.
     *
     * HANDLING folder = null:
     * If the folder is deleted while this screen is open, observeFolder()
     * emits null. We don't crash — we just keep the last known name and let
     * the delete flow (confirmDelete → onNavigateBack) handle the navigation.
     */
    fun loadFolder(folderId: String) {
        currentFolderId = folderId

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            combine(
                folderRepository.observeFolder(folderId),   // Flow<Folder?>
                noteRepository.getNotesByFolder(folderId)   // Flow<List<Note>>
            ) { folder, notes ->
                // This lambda runs every time either flow emits.
                // folder can be null if deleted — keep the last name in that case.
                FolderNotesUiState(
                    folderName = folder?.name ?: _uiState.value.folderName,
                    notes = notes,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE NOTE
    // ─────────────────────────────────────────────────────────────────────────

    fun createNoteInFolder(folderId: String, onNavigateToEditor: (String) -> Unit) {
        viewModelScope.launch {
            val noteId = UUID.randomUUID().toString()
            val note = Note(
                id = noteId,
                title = "",
                content = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                folderId = folderId
            )
            noteRepository.insertNote(note, folderId = folderId)
            onNavigateToEditor(noteId)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENAME
    // ─────────────────────────────────────────────────────────────────────────

    fun openRenameDialog() {
        _renameText.value = _uiState.value.folderName
        _showRenameDialog.value = true
    }

    fun onRenameTextChange(text: String) {
        _renameText.value = text
    }

    fun dismissRenameDialog() {
        _showRenameDialog.value = false
        _renameText.value = ""
    }

    /**
     * Write the new name to the database.
     *
     * Because loadFolder() is now using observeFolder() via combine(), the
     * updated name flows back automatically once Room commits the write.
     * The top bar title updates without any manual state assignment here.
     */
    fun confirmRename() {
        val newName = _renameText.value.trim()
        if (newName.isBlank()) return

        viewModelScope.launch {
            val folder = folderRepository.getFolderById(currentFolderId) ?: return@launch
            folderRepository.updateFolder(
                folder.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis()
                )
            )
            // No manual UI update needed — observeFolder() emits the new
            // folder automatically, combine() picks it up, uiState updates.
        }
        dismissRenameDialog()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────

    fun openDeleteDialog() {
        _showDeleteDialog.value = true
    }

    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }

    /**
     * Delete the folder. The user chooses what happens to notes inside.
     *
     * @param deleteNotes
     *   true  = permanently delete all notes in the folder (user explicitly chose this)
     *   false = move notes to root (folderId = null) — safe default, nothing lost
     *
     * @param onNavigateBack
     *   Called after all DB operations complete. The screen navigates away
     *   because the folder it was showing no longer exists.
     *
     * STEP BY STEP:
     * 1. Fetch current notes in this folder (one snapshot via .first())
     * 2a. If deleteNotes=true: permanently delete each note from the DB
     * 2b. If deleteNotes=false: set folderId=null on each note (move to root)
     * 3. Delete the folder row
     * 4. Navigate back
     *
     * WHY .first() INSTEAD OF COLLECTING THE FLOW?
     * We only need one snapshot to act on — not a live stream. .first()
     * takes the current value and cancels the collection immediately.
     * This is safe because we're about to delete the folder anyway.
     */
    fun confirmDelete(deleteNotes: Boolean, onNavigateBack: () -> Unit) {
        viewModelScope.launch {
            // Step 1: snapshot of current notes
            val notesInFolder = noteRepository.getNotesByFolder(currentFolderId).first()

            if (deleteNotes) {
                // Step 2a: permanently delete every note
                notesInFolder.forEach { note ->
                    noteRepository.deleteNotePermanently(note.id)
                }
            } else {
                // Step 2b: move every note to root level (folderId = null)
                notesInFolder.forEach { note ->
                    noteRepository.moveNoteToFolder(note.id, null)
                }
            }

            // Step 3: delete the folder
            folderRepository.deleteFolder(currentFolderId)

            // Step 4: leave the screen
            onNavigateBack()
        }
        dismissDeleteDialog()
    }
}

/**
 * UI state for FolderNotesScreen.
 */
data class FolderNotesUiState(
    val folderName: String = "",
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true
)