package com.greenicephoenix.voidnote.presentation.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * DiaryViewModel — drives the Journal / Diary calendar screen.
 *
 * ── WHAT IT DOES ──────────────────────────────────────────────────────────────
 * 1. Loads all diary entries (isDiaryEntry = true) as a reactive Flow.
 * 2. Builds a Set<String> of "yyyy-MM-dd" date strings for entries that exist,
 *    so the calendar can show dot indicators at O(1) lookup per cell.
 * 3. Tracks which month/year the calendar is currently showing.
 * 4. Handles "open or create" for any date tapped on the calendar.
 * 5. Emits one-shot navigation events (noteId) so the screen navigates to
 *    the editor without re-triggering on recomposition.
 *
 * ── DATE KEY FORMAT ───────────────────────────────────────────────────────────
 * All date keys use "yyyy-MM-dd" (e.g. "2026-03-14").
 * This format is:
 *   • Locale-independent — safe for sorting and comparison
 *   • Unambiguous — no day/month order confusion
 *   • Human-readable in logs
 *
 * ── DIARY ENTRY TITLE FORMAT ──────────────────────────────────────────────────
 * Diary notes use the title "📅 March 14, 2026".
 * This is what the user sees as the date label in the editor.
 * The dateKey "2026-03-14" is derived from that title for calendar lookups.
 *
 * WHY SEARCH IN-MEMORY?
 * Note titles are AES-256-GCM ciphertext in the DB — SQL LIKE can't match.
 * getDiaryEntries() returns fully decrypted Note objects, so we search in Kotlin.
 */
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    // ── UI State ──────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    /**
     * One-shot navigation event — emits the noteId of the entry to open.
     * SharedFlow with replay=0 so rotation doesn't re-trigger navigation.
     */
    private val _navigationEvent = MutableSharedFlow<String>(replay = 0)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    // Formatter for the "yyyy-MM-dd" date key
    private val keyFormatter  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Formatter for the diary note title "📅 March 14, 2026"
    private val titleFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    init {
        // Observe diary entries reactively — any create/delete updates the calendar dots
        noteRepository.getDiaryEntries()
            .onEach { entries -> onEntriesUpdated(entries) }
            .launchIn(viewModelScope)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Called whenever the diary entries Flow emits a new list.
     * Rebuilds the entryDateKeys set and the noteIdByDate map.
     */
    private fun onEntriesUpdated(entries: List<Note>) {
        // Map each entry to its date key by parsing the title "📅 March 14, 2026"
        // Entries that don't match the expected format are silently skipped.
        val dateKeyToNoteId = mutableMapOf<String, String>()

        entries.forEach { note ->
            val dateKey = dateKeyFromTitle(note.title)
            if (dateKey != null) {
                dateKeyToNoteId[dateKey] = note.id
            }
        }

        _uiState.value = _uiState.value.copy(
            entryDateKeys  = dateKeyToNoteId.keys.toSet(),
            noteIdByDate   = dateKeyToNoteId,
            isLoading      = false
        )
    }

    /**
     * Parse a diary note title like "📅 March 14, 2026" into a "yyyy-MM-dd" key.
     * Returns null if the title doesn't match the expected format.
     */
    private fun dateKeyFromTitle(title: String): String? {
        return try {
            val stripped = title.removePrefix("📅 ").trim()
            val date     = titleFormatter.parse(stripped) ?: return null
            keyFormatter.format(date)
        } catch (_: Exception) {
            null
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    /** Navigate forward one month. */
    fun nextMonth() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR,  _uiState.value.displayYear)
            set(Calendar.MONTH, _uiState.value.displayMonth)
            add(Calendar.MONTH, 1)
        }
        _uiState.value = _uiState.value.copy(
            displayYear  = cal.get(Calendar.YEAR),
            displayMonth = cal.get(Calendar.MONTH)
        )
    }

    /** Navigate back one month. */
    fun previousMonth() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR,  _uiState.value.displayYear)
            set(Calendar.MONTH, _uiState.value.displayMonth)
            add(Calendar.MONTH, -1)
        }
        _uiState.value = _uiState.value.copy(
            displayYear  = cal.get(Calendar.YEAR),
            displayMonth = cal.get(Calendar.MONTH)
        )
    }

    /**
     * Open an existing diary entry for [dateKey], or create a new one.
     *
     * OPEN:  If an entry exists for this date → emit its noteId.
     * CREATE: Insert a new Note with isDiaryEntry=true, title="📅 ...",
     *         then emit its noteId. The DB observer will add the dot.
     *
     * DELETE ON EMPTY: handled in the editor via NoteEditorViewModel's ghost
     * note cleanup (onCleared) — if the user opens an entry and writes nothing,
     * the note is deleted when they leave.
     */
    fun openOrCreateEntry(dateKey: String) {
        viewModelScope.launch {
            // Check if an entry already exists for this date
            val existingId = _uiState.value.noteIdByDate[dateKey]
            if (existingId != null) {
                _navigationEvent.emit(existingId)
                return@launch
            }

            // Create a new diary entry for this date
            val date  = keyFormatter.parse(dateKey) ?: Date()
            val title = "📅 ${titleFormatter.format(date)}"

            val noteId = UUID.randomUUID().toString()
            val now    = System.currentTimeMillis()

            noteRepository.insertNote(
                com.greenicephoenix.voidnote.domain.model.Note(
                    id           = noteId,
                    title        = title,
                    content      = "",
                    createdAt    = now,
                    updatedAt    = now,
                    isDiaryEntry = true
                )
            )
            _navigationEvent.emit(noteId)
        }
    }

    /**
     * Convenience — open or create today's diary entry.
     * Called by the "Today" FAB on the calendar screen.
     */
    fun openOrCreateToday() {
        openOrCreateEntry(keyFormatter.format(Date()))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI STATE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * DiaryUiState — immutable snapshot of everything the DiaryScreen needs.
 *
 * @param displayYear    The year currently shown in the calendar header.
 * @param displayMonth   The month currently shown (0 = January, 11 = December).
 * @param entryDateKeys  Set of "yyyy-MM-dd" strings for days that have an entry.
 *                       Used by the calendar to decide whether to draw a dot.
 * @param noteIdByDate   Map of dateKey → noteId. Used to navigate to an existing entry.
 * @param isLoading      True while the initial DB query is running.
 */
data class DiaryUiState(
    val displayYear   : Int                  = Calendar.getInstance().get(Calendar.YEAR),
    val displayMonth  : Int                  = Calendar.getInstance().get(Calendar.MONTH),
    val entryDateKeys : Set<String>          = emptySet(),
    val noteIdByDate  : Map<String, String>  = emptyMap(),
    val isLoading     : Boolean              = true
)