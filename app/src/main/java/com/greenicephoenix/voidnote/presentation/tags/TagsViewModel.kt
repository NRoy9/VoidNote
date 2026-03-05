package com.greenicephoenix.voidnote.presentation.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * TagsViewModel — drives the Tags screen.
 *
 * HOW TAGS WORK IN THIS APP:
 * Tags are NOT stored in a separate database table. They are a List<String>
 * field on each NoteEntity, stored as JSON and decrypted by NoteMapper.
 * So to get all tags, we collect all notes and extract tag strings from them.
 *
 * This approach means:
 * - No extra DB query needed — we just transform the note stream we already have.
 * - Tag counts are always accurate (derived from live data, not cached).
 * - Adding/removing a tag on a note immediately updates this screen.
 *
 * TWO-LEVEL UI:
 * Level 1 — tag list: shows all unique tags with their note count.
 * Level 2 — filtered notes: shows all notes that contain the selected tag.
 *
 * Back navigation: Level 2 → Level 1 (clearTag), Level 1 → nav pop.
 * This is handled via BackHandler in the screen, not the nav stack.
 * We deliberately do NOT push a new nav destination for tag filtering —
 * it keeps the back stack clean and avoids animation jank.
 */
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    // Which tag is currently selected (null = show all tags)
    private val _selectedTag = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TagsUiState> = combine(
        noteRepository.getAllNotes(),
        _selectedTag
    ) { notes, selectedTag ->

        // Build a map: tagName → how many non-trashed notes contain it
        // sorted by count descending so popular tags appear first
        val tagCounts = mutableMapOf<String, Int>()
        notes.forEach { note ->
            note.tags.forEach { tag ->
                if (tag.isNotBlank()) {
                    tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
                }
            }
        }
        val allTags = tagCounts.entries
            .sortedByDescending { it.value }
            .map { TagWithCount(name = it.key, noteCount = it.value) }

        // Filter notes for the selected tag (empty list if none selected)
        val filteredNotes = if (selectedTag != null) {
            notes.filter { note -> selectedTag in note.tags }
        } else {
            emptyList()
        }

        TagsUiState(
            allTags       = allTags,
            selectedTag   = selectedTag,
            filteredNotes = filteredNotes,
            isLoading     = false
        )
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5000),
        initialValue   = TagsUiState(isLoading = true)
    )

    /** Select a tag to filter notes by — switches to Level 2 view. */
    fun selectTag(tag: String) { _selectedTag.value = tag }

    /** Clear selection — returns to Level 1 tag list. */
    fun clearTag() { _selectedTag.value = null }
}

// ─── UI State models ─────────────────────────────────────────────────────────

data class TagsUiState(
    val allTags       : List<TagWithCount> = emptyList(),
    val selectedTag   : String?            = null,
    val filteredNotes : List<Note>         = emptyList(),
    val isLoading     : Boolean            = true
)

/** A tag name paired with the number of notes that carry it. */
data class TagWithCount(
    val name      : String,
    val noteCount : Int
)