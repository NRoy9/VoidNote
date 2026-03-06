package com.greenicephoenix.voidnote.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.BuildConfig
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.model.NoteSort
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import com.greenicephoenix.voidnote.util.UpdateCheckerManager
import com.greenicephoenix.voidnote.util.UpdateInfo
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
 * ─── ROOT NOTES FILTER ───────────────────────────────────────────────────────
 * NoteDao.getAllNotes() runs: WHERE isTrashed = 0
 * This intentionally INCLUDES archived notes. We filter them here so:
 *   a) Empty state triggers correctly when all notes are archived
 *   b) Archived notes never appear on the home screen
 *
 * ─── NOTE SORT (Sprint 6) ────────────────────────────────────────────────────
 * The selected sort is read from PreferencesManager.noteSortFlow and applied
 * in-memory after the DB read. Pinned notes always come before unpinned notes,
 * regardless of the chosen sort — this matches user expectation (pins should
 * stay at the top).
 *
 * Sort logic:
 *   1. Separate notes into pinned + unpinned groups.
 *   2. Sort each group independently with the chosen NoteSort.
 *   3. Concatenate: pinned (sorted) + unpinned (sorted).
 *
 * ─── UPDATE CHECKER (Sprint 6) ───────────────────────────────────────────────
 * On init, we launch a background coroutine that hits the GitHub Releases API.
 * If a newer version is found AND the user hasn't already dismissed it,
 * _updateInfo is set — the screen shows a dismissible banner.
 *
 * The dismiss action stores the dismissed version tag in PreferencesManager,
 * so the banner doesn't re-appear for that version across restarts.
 */
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val preferencesManager: PreferencesManager,
    private val updateChecker: UpdateCheckerManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName.asStateFlow()

    // ─── Sort state ───────────────────────────────────────────────────────────

    /** The currently selected sort, read from DataStore as a StateFlow. */
    val noteSort: StateFlow<NoteSort> = preferencesManager.noteSortFlow
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5000),
            initialValue = NoteSort.UPDATED_DESC
        )

    /** Called when the user picks a sort from the overflow menu. */
    fun onSortSelected(sort: NoteSort) {
        viewModelScope.launch {
            preferencesManager.setNoteSort(sort)
        }
    }

    // ─── Update checker ───────────────────────────────────────────────────────

    /**
     * Holds the update info if a newer version is available and not yet dismissed.
     * null = no update banner shown.
     */
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /**
     * Called when the user taps "Dismiss" on the update banner.
     * Stores the dismissed version so the banner won't show for it again.
     */
    fun onUpdateDismissed() {
        val info = _updateInfo.value ?: return
        _updateInfo.value = null
        viewModelScope.launch {
            preferencesManager.setDismissedUpdateVersion(info.tagName)
        }
    }

    // ─── Main UI state ────────────────────────────────────────────────────────

    /**
     * Combines notes, folders, search query, counts, and the selected sort
     * into a single immutable UI state object.
     *
     * WHY combine() WITH 5 SOURCES?
     * Kotlin's combine() supports up to 5 flows natively.
     * When ANY of the 5 changes, the lambda re-runs and a new state is emitted.
     * This is more efficient than nested flatMapLatest chains.
     *
     * NOTE: sort is applied inside the combine so changing the sort order
     * immediately re-sorts the existing notes without waiting for a new DB read.
     */
    val uiState: StateFlow<NotesListUiState> = combine(
        noteRepository.getAllNotes(),
        folderRepository.getAllFolders(),
        _searchQuery,
        noteRepository.getNoteCount(),
        noteSort
    ) { notes, folders, query, noteCount, sort ->

        // Exclude archived notes from the main list (see class KDoc above)
        val rootNotes = notes.filter { it.folderId == null && !it.isArchived }

        val folderNoteCounts = folders.associate { folder ->
            folder.id to notes.count { it.folderId == folder.id }
        }

        // Apply search filter
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

        // Apply sort — pinned notes always precede unpinned within each group
        val sortedNotes = sortNotes(filteredNotes, sort)

        NotesListUiState(
            notes            = sortedNotes,
            folders          = filteredFolders,
            folderNoteCounts = folderNoteCounts,
            isLoading        = false,
            searchQuery      = query,
            totalNoteCount   = noteCount,
            totalFolderCount = folderNoteCounts.values.sum()
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesListUiState(isLoading = true)
    )

    // ─── Sort logic ───────────────────────────────────────────────────────────

    /**
     * Sort a list of notes by the chosen [NoteSort], keeping pinned notes
     * always at the top of the result regardless of sort order.
     *
     * Step 1: Split notes into pinned / unpinned buckets.
     * Step 2: Apply the sort comparator to each bucket independently.
     * Step 3: Concatenate pinned + unpinned.
     */
    private fun sortNotes(notes: List<Note>, sort: NoteSort): List<Note> {
        val pinned   = notes.filter {  it.isPinned }
        val unpinned = notes.filter { !it.isPinned }

        fun applySortTo(list: List<Note>): List<Note> = when (sort) {
            NoteSort.UPDATED_DESC -> list.sortedByDescending { it.updatedAt }
            NoteSort.CREATED_DESC -> list.sortedByDescending { it.createdAt }
            NoteSort.TITLE_ASC    -> list.sortedBy        { it.title.lowercase() }
            NoteSort.TITLE_DESC   -> list.sortedByDescending { it.title.lowercase() }
        }

        return applySortTo(pinned) + applySortTo(unpinned)
    }

    // ─── Init — run update check on launch ───────────────────────────────────

    init {
        checkForUpdate()
    }

    /**
     * Run the GitHub update check in the background.
     *
     * Flow:
     * 1. Read the previously-dismissed version from DataStore (non-blocking).
     * 2. Call UpdateCheckerManager.checkForUpdate() on IO dispatcher.
     * 3. If an update is found AND it's not the dismissed version, expose it.
     *
     * If the check fails (no internet, GitHub down, etc.), nothing happens.
     * The UI state doesn't change, and no error is shown — update checks are
     * opportunistic and non-critical.
     */
    private fun checkForUpdate() {
        viewModelScope.launch {
            try {
                // Read the version the user last dismissed
                val dismissedVersion = preferencesManager.dismissedUpdateVersionFlow
                    .stateIn(viewModelScope).value

                val info = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)

                if (info != null && info.tagName != dismissedVersion) {
                    _updateInfo.value = info
                }
            } catch (_: Exception) {
                // Silently ignore — update check is non-critical
            }
        }
    }

    // ─── User actions ─────────────────────────────────────────────────────────

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

    // Kept for compatibility — was in original, not actively used
    fun getNotesCountInFolder(folderId: String): Int = 0
    private fun getNotesInFolders(notes: List<Note>): Set<String> = emptySet()
}

/**
 * Immutable snapshot of the notes list UI state.
 */
data class NotesListUiState(
    val notes: List<Note>                  = emptyList(),
    val folders: List<Folder>              = emptyList(),
    val folderNoteCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean                 = true,
    val searchQuery: String                = "",
    val errorMessage: String?              = null,
    val totalNoteCount: Int                = 0,
    val totalFolderCount: Int              = 0
)