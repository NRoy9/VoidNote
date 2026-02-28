package com.greenicephoenix.voidnote.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.InlineBlockRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * ViewModel for Search Screen.
 *
 * WHAT IT SEARCHES:
 * 1. Note title (plain text — always clean)
 * 2. Note logical content (markers stripped via getContentPreview)
 * 3. Note tags
 * 4. Checklist item text stored in the inline_blocks table
 *
 * CHECKLIST SEARCH ARCHITECTURE:
 * Checklist item text lives in the inline_blocks table, not in notes.content.
 * We run two parallel searches and union the results:
 *
 *   Search A: notes table         → title / content / tags
 *   Search B: inline_blocks table → payload JSON LIKE query (item text)
 *
 * combine() with three Flows merges all three sources reactively.
 * If a note matches either A or B, it surfaces in results.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val inlineBlockRepository: InlineBlockRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    // In-memory recent searches — survives config changes, clears on app restart.
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    val folders: StateFlow<List<Folder>> = folderRepository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search results — debounced 300ms, combined with folder filter
    val searchResults: StateFlow<SearchUiState> = _searchQuery
        .debounce(300)
        .combine(_selectedFolderId) { query, folderId -> Pair(query, folderId) }
        .flatMapLatest { (query, folderId) ->
            if (query.isBlank()) {
                flowOf(SearchUiState(isSearching = false, showRecentSearches = true))
            } else {
                performSearch(query, folderId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState(isSearching = false, showRecentSearches = true)
        )

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onRecentSearchClick(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }
    fun clearRecentSearches() { _recentSearches.value = emptyList() }
    fun selectFolder(folderId: String?) { _selectedFolderId.value = folderId }

    /**
     * Three-source search — union of text match + checklist item match.
     *
     * Content uses getContentPreview(Int.MAX_VALUE) — full logical text,
     * all block markers stripped — prevents false positives from UUID
     * fragments or the literal string "TODO".
     */
    private fun performSearch(query: String, folderId: String?): Flow<SearchUiState> {
        return combine(
            noteRepository.getAllNotes(),
            folderRepository.getAllFolders(),
            inlineBlockRepository.searchNoteIdsByBlockContent(query)
        ) { notes, folders, blockMatchNoteIds ->

            // Save to recent searches (newest first, max 10, no duplicates)
            if (query.isNotBlank() && query !in _recentSearches.value) {
                _recentSearches.value = (listOf(query) + _recentSearches.value).take(10)
            }

            // O(1) lookup — convert list to Set once before the filter loop
            val blockMatchSet: Set<String> = blockMatchNoteIds.toHashSet()

            val matchingNotes = notes.filter { note ->
                if (note.isTrashed) return@filter false

                val matchesFolder = folderId == null || note.folderId == folderId
                if (!matchesFolder) return@filter false

                // Search A: title / content / tags
                val titleMatch   = note.title.contains(query, ignoreCase = true)
                val contentMatch = note.getContentPreview(Int.MAX_VALUE).contains(query, ignoreCase = true)
                val tagMatch     = note.tags.any { it.contains(query, ignoreCase = true) }

                // Search B: checklist item text (from inline_blocks table)
                val blockMatch   = note.id in blockMatchSet

                titleMatch || contentMatch || tagMatch || blockMatch
            }

            val matchingFolders = folders.filter { folder ->
                folder.name.contains(query, ignoreCase = true)
            }

            SearchUiState(
                notes = matchingNotes,
                folders = matchingFolders,
                isSearching = false,
                showRecentSearches = false,
                query = query
            )
        }
    }
}

/**
 * UI state for Search Screen.
 */
data class SearchUiState(
    val notes: List<Note> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val isSearching: Boolean = true,
    val showRecentSearches: Boolean = false,
    val query: String = ""
) {
    val hasResults: Boolean = notes.isNotEmpty() || folders.isNotEmpty()
}