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
import java.util.UUID

/**
 * ViewModel for Notes List Screen
 *
 * NOW SHOWS BOTH FOLDERS AND NOTES!
 *
 * @param noteRepository Repository for notes
 * @param folderRepository Repository for folders
 */
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName.asStateFlow()

    // Combine notes, folders, and counts
    val uiState: StateFlow<NotesListUiState> = combine(
        noteRepository.getAllNotes(),
        folderRepository.getAllFolders(),
        _searchQuery,
        noteRepository.getNoteCount(),
        folderRepository.getFolderCount()
    ) { notes, folders, query, noteCount, folderCount ->

        // Filter notes without folders (root level notes)
        val rootNotes = notes.filter { it.folderId == null }

        // Calculate note counts for each folder
        val folderNoteCounts = getNoteCounts(notes, folders)

        // Apply search filter if query exists
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
            folders.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }

        NotesListUiState(
            notes = filteredNotes,
            folders = filteredFolders,
            folderNoteCounts = folderNoteCounts, // Add this
            isLoading = false,
            searchQuery = query,
            totalNoteCount = noteCount,
            totalFolderCount = folderCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesListUiState(isLoading = true)
    )

    /**
     * Get note count for a specific folder
     */
    fun getNotesCountInFolder(folderId: String): Int {
        // TODO: Implement when we add folderId to notes
        return 0
    }

    /**
     * Helper function to get all note IDs that are in folders
     * TODO: Update when we implement folder assignment
     */
    private fun getNotesInFolders(notes: List<Note>): Set<String> {
        // For now, return empty set (all notes are root level)
        // We'll update this when we implement folder assignment
        return emptySet()
    }

    /**
     * Handle search query changes
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * Toggle pin status for a note
     */
    fun onTogglePin(noteId: String) {
        viewModelScope.launch {
            noteRepository.togglePin(noteId)
        }
    }

    /**
     * Move note to trash
     */
    fun onDeleteNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.moveToTrash(noteId)
        }
    }

    /**
     * Archive note
     */
    fun onArchiveNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.toggleArchive(noteId)
        }
    }

    /**
     * Show create folder dialog
     */
    fun showCreateFolderDialog() {
        _newFolderName.value = ""
        _showCreateFolderDialog.value = true
    }

    /**
     * Hide create folder dialog
     */
    fun hideCreateFolderDialog() {
        _showCreateFolderDialog.value = false
        _newFolderName.value = ""
    }

    /**
     * Update new folder name
     */
    fun onNewFolderNameChange(name: String) {
        _newFolderName.value = name
    }

    /**
     * Create new folder
     */
    fun createFolder() {
        val name = _newFolderName.value.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val folder = com.greenicephoenix.voidnote.domain.model.Folder(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis()
            )
            folderRepository.createFolder(folder)
            hideCreateFolderDialog()
        }
    }

    /**
     * Get note count for each folder
     */
    private fun getNoteCounts(notes: List<Note>, folders: List<Folder>): Map<String, Int> {
        return folders.associate { folder ->
            folder.id to notes.count { note -> note.folderId == folder.id }
        }
    }
}

/**
 * UI State for Notes List Screen
 * NOW INCLUDES FOLDERS!
 */
data class NotesListUiState(
    val notes: List<Note> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val folderNoteCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val totalNoteCount: Int = 0,
    val totalFolderCount: Int = 0
)