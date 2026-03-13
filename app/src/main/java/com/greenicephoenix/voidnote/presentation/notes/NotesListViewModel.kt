package com.greenicephoenix.voidnote.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.BuildConfig
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.domain.model.InlineBlockType
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.model.NoteSort
import com.greenicephoenix.voidnote.domain.model.NoteTemplate
import com.greenicephoenix.voidnote.domain.model.TodoItem
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.InlineBlockRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import com.greenicephoenix.voidnote.presentation.editor.DocumentParser
import com.greenicephoenix.voidnote.util.UpdateCheckerManager
import com.greenicephoenix.voidnote.util.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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
    private val inlineBlockRepository: InlineBlockRepository,
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

    /**
     * Undo an archive action — toggles the note back to active.
     * Called when user taps "Undo" in the snackbar after a swipe-archive.
     * toggleArchive() handles both directions (archive ↔ restore).
     */
    fun undoArchive(noteId: String) {
        viewModelScope.launch { noteRepository.toggleArchive(noteId) }
    }
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

    // ─── Sprint 11: Templates + Daily Note ───────────────────────────────────

    /**
     * One-shot navigation events emitted after a note is created.
     *
     * WHY SharedFlow (not StateFlow)?
     * StateFlow keeps the last value — if we used it, rotating the device after
     * creating a note would re-navigate. SharedFlow with replay=0 fires once
     * and is forgotten. The screen collects it with LaunchedEffect and calls
     * onNavigateToEditor(noteId) exactly once.
     */
    private val _navigationEvent = MutableSharedFlow<String>(replay = 0)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    /**
     * Create a new note from [template], then navigate to it.
     *
     * CRITICAL INSERT ORDER:
     * inline_blocks has a FOREIGN KEY on noteId → notes.id.
     * We must insert the Note FIRST, then the InlineBlocks.
     * Inserting blocks before the note exists causes a FK constraint crash.
     *
     * For templates with todoSections:
     *   1. Build content string with section headers + marker tokens (in memory)
     *   2. Collect InlineBlock objects (in memory, not yet in DB)
     *   3. Insert the Note with the final content
     *   4. Insert all InlineBlocks (note now exists → FK satisfied)
     *
     * For plain-text templates: just insert the note with content as-is.
     */
    fun createNoteFromTemplate(template: NoteTemplate) {
        viewModelScope.launch {
            val noteId = UUID.randomUUID().toString()
            val now    = System.currentTimeMillis()

            val finalContent: String
            val blocksToInsert = mutableListOf<InlineBlock>()

            if (template.todoSections.isNotEmpty()) {
                // ── Step 1: build content + collect blocks IN MEMORY ──────────
                val contentBuilder = StringBuilder()

                template.todoSections.forEachIndexed { index, section ->
                    if (section.header.isNotBlank()) {
                        if (index > 0) contentBuilder.append("\n")
                        contentBuilder.append(section.header)
                    }

                    val blockId   = UUID.randomUUID().toString()
                    val todoItems = section.items.mapIndexed { i, text ->
                        TodoItem(
                            id        = UUID.randomUUID().toString(),
                            text      = text,
                            isChecked = false,
                            sortOrder = i
                        )
                    }
                    // Collect block — do NOT insert yet (note doesn't exist yet)
                    blocksToInsert.add(
                        InlineBlock(
                            id        = blockId,
                            noteId    = noteId,
                            type      = InlineBlockType.TODO,
                            payload   = InlineBlockPayload.Todo(items = todoItems),
                            createdAt = now
                        )
                    )
                    contentBuilder.append("\n")
                    contentBuilder.append(DocumentParser.createMarker(InlineBlockType.TODO, blockId))
                }
                finalContent = contentBuilder.toString()
            } else {
                finalContent = template.content
            }

            // ── Step 2: insert Note FIRST ─────────────────────────────────────
            noteRepository.insertNote(
                Note(
                    id        = noteId,
                    title     = template.titlePrefix,
                    content   = finalContent,
                    createdAt = now,
                    updatedAt = now
                )
            )

            // ── Step 3: now insert blocks (FK satisfied) ──────────────────────
            blocksToInsert.forEach { block ->
                inlineBlockRepository.insertBlock(block)
            }

            _navigationEvent.emit(noteId)
        }
    }

    /**
     * Open today's Daily Note, or create it if it doesn't exist yet.
     *
     * Title format: "📅 March 14, 2026"
     * Searches notes in-memory (titles are AES-256 ciphertext in SQLite —
     * SQL LIKE can't match plaintext). If found, navigate. If not, create.
     */
    fun openOrCreateDailyNote() {
        viewModelScope.launch {
            val today      = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
            val dailyTitle = "📅 $today"

            // Look for an existing daily note with today's exact title
            val existing = uiState.value.notes.firstOrNull { note ->
                note.title.trim() == dailyTitle && !note.isTrashed
            }

            if (existing != null) {
                _navigationEvent.emit(existing.id)
            } else {
                val noteId = UUID.randomUUID().toString()
                val now    = System.currentTimeMillis()

                // Starter content — same prompts as the Daily Journal template
                // but auto-titled with today's date
                val starterContent = """
— — —

How am I feeling right now?


What happened today worth remembering?


One thing I learned


What am I grateful for?


If I could redo one thing today, what would it be?


— — —
                """.trimIndent()

                noteRepository.insertNote(
                    Note(
                        id        = noteId,
                        title     = dailyTitle,
                        content   = starterContent,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                _navigationEvent.emit(noteId)
            }
        }
    }
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