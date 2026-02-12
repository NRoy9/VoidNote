package com.greenicephoenix.voidnote.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Search Screen
 *
 * Features:
 * - Real-time search with debouncing
 * - Search notes and folders
 * - Recent searches
 * - Filter by folder
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected folder filter (null = all folders)
    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    // Recent searches (stored in memory for now)
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // All folders for filter
    val folders: StateFlow<List<Folder>> = folderRepository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search results with debouncing
    val searchResults: StateFlow<SearchUiState> = _searchQuery
        .debounce(300) // Wait 300ms after user stops typing
        .combine(_selectedFolderId) { query, folderId -> Pair(query, folderId) }
        .flatMapLatest { (query, folderId) ->
            if (query.isBlank()) {
                // Empty query = show recent searches
                flowOf(SearchUiState(
                    isSearching = false,
                    showRecentSearches = true
                ))
            } else {
                // Perform search
                performSearch(query, folderId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState(isSearching = false, showRecentSearches = true)
        )

    /**
     * Update search query
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * Select a recent search
     */
    fun onRecentSearchClick(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clear search query
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /**
     * Clear recent searches
     */
    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
    }

    /**
     * Select folder filter
     */
    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
    }

    /**
     * Perform search and return results
     */
    private fun performSearch(query: String, folderId: String?): Flow<SearchUiState> {
        return combine(
            noteRepository.getAllNotes(),
            folderRepository.getAllFolders()
        ) { notes, folders ->

            // Save to recent searches if not already there
            if (query.isNotBlank() && query !in _recentSearches.value) {
                val updated = listOf(query) + _recentSearches.value.take(9) // Keep last 10
                _recentSearches.value = updated
            }

            // Filter notes by query
            val matchingNotes = notes.filter { note ->
                val matchesQuery = note.title.contains(query, ignoreCase = true) ||
                        note.content.contains(query, ignoreCase = true) ||
                        note.tags.any { it.contains(query, ignoreCase = true) }

                val matchesFolder = folderId == null || note.folderId == folderId

                val notTrashed = !note.isTrashed

                matchesQuery && matchesFolder && notTrashed
            }

            // Filter folders by query
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
 * UI State for Search Screen
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