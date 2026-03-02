package com.greenicephoenix.voidnote.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Notes List Screen.
 *
 * ROOT NOTES FILTER — WHY WE EXCLUDE ARCHIVED:
 * NoteDao.getAllNotes() runs: WHERE isTrashed = 0
 * This intentionally INCLUDES archived notes (they're not trashed, just hidden).
 * The archive is a separate section, not a deletion.
 *
 * However, archived notes must NOT appear on the main list. Before this fix,
 * rootNotes included archived notes, which caused two bugs:
 *
 * 1. EMPTY STATE BUG: If a user archived their only note, uiState.notes still
 *    contained 1 entry (the archived note), so the empty state never showed.
 *    The note was filtered again in NotesAndFoldersContent, leaving a blank
 *    screen with no feedback.
 *
 * 2. VISUAL LEAK: Archived notes could theoretically flicker onto the main list
 *    for one frame during recomposition before the UI filter caught them.
 *
 * FIX: Filter archived notes at the ViewModel level — the single source of truth.
 *      rootNotes = notes where folderId == null AND isArchived == false
 */
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName.asStateFlow()

    val uiState: StateFlow<NotesListUiState> = combine(
        noteRepository.getAllNotes(),
        folderRepository.getAllFolders(),
        _searchQuery,
        noteRepository.getNoteCount(),
        folderRepository.getFolderCount()
    ) { notes, folders, query, noteCount, folderCount ->

        // ── CRITICAL FIX ──────────────────────────────────────────────────────
        // Exclude archived notes from the main list.
        // getAllNotes() returns isTrashed=0 (includes archived).
        // We filter archived here so:
        //   a) Empty state triggers correctly when all notes are archived
        //   b) Archived notes never appear on the home screen
        val rootNotes = notes.filter { it.folderId == null && !it.isArchived }

        val folderNoteCounts = getNoteCounts(notes, folders)

        val filteredNotes = if (query.isBlank()) {
            rootNotes
        } else {
            rootNotes.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        }

        val filteredFolders = if (query.isBlank()) {
            folders
        } else {
            folders.filter { it.name.contains(query, ignoreCase = true) }
        }

        NotesListUiState(
            notes            = filteredNotes,
            folders          = filteredFolders,
            folderNoteCounts = folderNoteCounts,
            isLoading        = false,
            searchQuery      = query,
            totalNoteCount   = noteCount,
            totalFolderCount = folderCount
        )
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5000),
        initialValue   = NotesListUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String)  { _searchQuery.value = query }
    fun onTogglePin(noteId: String)          { viewModelScope.launch { noteRepository.togglePin(noteId) } }
    fun onDeleteNote(noteId: String)         { viewModelScope.launch { noteRepository.moveToTrash(noteId) } }
    fun onArchiveNote(noteId: String)        { viewModelScope.launch { noteRepository.toggleArchive(noteId) } }
    fun showCreateFolderDialog()             { _newFolderName.value = ""; _showCreateFolderDialog.value = true }
    fun hideCreateFolderDialog()             { _showCreateFolderDialog.value = false; _newFolderName.value = "" }
    fun onNewFolderNameChange(name: String)  { _newFolderName.value = name }

    fun createFolder() {
        val name = _newFolderName.value.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val folder = com.greenicephoenix.voidnote.domain.model.Folder(
                id        = java.util.UUID.randomUUID().toString(),
                name      = name,
                createdAt = System.currentTimeMillis()
            )
            folderRepository.createFolder(folder)
            hideCreateFolderDialog()
        }
    }

    // Unused but kept for compatibility
    fun getNotesCountInFolder(folderId: String): Int = 0

    private fun getNotesInFolders(notes: List<Note>): Set<String> = emptySet()

    private fun getNoteCounts(notes: List<Note>, folders: List<Folder>): Map<String, Int> =
        folders.associate { folder -> folder.id to notes.count { it.folderId == folder.id } }
}

data class NotesListUiState(
    val notes: List<Note>                = emptyList(),
    val folders: List<Folder>            = emptyList(),
    val folderNoteCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean               = true,
    val searchQuery: String              = "",
    val errorMessage: String?            = null,
    val totalNoteCount: Int              = 0,
    val totalFolderCount: Int            = 0
)