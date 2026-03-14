package com.greenicephoenix.voidnote.domain.model

/**
 * Note — Domain model representing a single note.
 *
 * CONTENT FORMAT (important for understanding preview helpers below):
 * The `content` field stores RAW content — the logical text PLUS
 * marker tokens for any inline blocks the note contains.
 *
 * Example raw content:
 *   "Shopping list\n⟦block:TODO:abc-123⟧\n⟦block:TODO:def-456⟧"
 *
 * The logical (user-visible) text is:
 *   "Shopping list"
 *
 * The markers are invisible to the user inside the editor — they're
 * stripped by DocumentParser.extractLogicalContent() when loading.
 * But they appear in this raw field, so all preview and isEmpty logic
 * must be marker-aware.
 *
 * VERSION 5 CHANGE: Added trashedAt field to track when a note was trashed.
 * Used by TrashCleanupWorker to auto-delete notes after 30 days.
 *
 * VERSION 7 (Sprint 6): Added color field for note color coding.
 * VERSION 8 (Sprint 11): Added linkedNoteIds for note linking.
 * VERSION 9 (Sprint 12): Added isDiaryEntry flag for Journal feature.
 * null = default card appearance (no tint applied).
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,           // RAW content — may contain ⟦block:...⟧ markers
    val contentFormats: List<FormatRange> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,

    /**
     * Unix timestamp (millis) when this note was moved to trash.
     * NULL if not trashed, or trashed before v5 (before this field existed).
     * TrashCleanupWorker uses this to find notes older than 30 days.
     */
    val trashedAt: Long? = null,

    val tags: List<String> = emptyList(),
    val folderId: String? = null,

    /**
     * Sprint 6: optional color accent for this note.
     * null = no color (default card appearance).
     * Set via the color picker in the note editor's settings panel.
     */
    val color: NoteColor? = null,

    /**
     * Sprint 11: IDs of notes this note is linked to.
     *
     * Links are stored as note UUIDs (not titles). This means:
     * - Links survive note renames (the UUID never changes)
     * - Links survive full vault restores (.vnbackup keeps original UUIDs)
     * - Dead links (pointing to deleted notes) are silently filtered at display time
     *   — the ID stays in the list but resolves to null and is skipped in the UI
     *
     * Stored via StringListConverter as a comma-separated column in Room.
     * emptyList() = no links (default for all pre-Sprint 11 notes).
     */
    val linkedNoteIds: List<String> = emptyList(),

    /**
     * Sprint 12 — Journal feature.
     * True for notes created via the Diary calendar (one per day).
     * Diary entries are excluded from the main note list and search
     * results by default — they live in the Journal screen only.
     */
    val isDiaryEntry: Boolean = false
) {

    // ─── Marker parsing ───────────────────────────────────────────────────

    /**
     * Regex to find block marker tokens in raw content.
     * Matches: ⟦block:TYPE:uuid⟧
     * Used to strip markers from previews and count blocks.
     *
     * WHY A COMPANION OBJECT?
     * Regex compilation is expensive. Defining it here means it's compiled
     * once per class (not once per Note instance or per function call).
     */
    companion object {
        private val BLOCK_MARKER_REGEX = Regex("""⟦block:[A-Z]+:[0-9a-f\-]+⟧""")
        private val TODO_MARKER_REGEX  = Regex("""⟦block:TODO:[0-9a-f\-]+⟧""")
        // Detect IMAGE and AUDIO blocks so the NoteCard can show indicator badges
        private val IMAGE_MARKER_REGEX = Regex("""⟦block:IMAGE:[0-9a-f\-]+⟧""")
        private val AUDIO_MARKER_REGEX = Regex("""⟦block:AUDIO:[0-9a-f\-]+⟧""")
    }

    /**
     * Returns the logical (user-visible) text — markers stripped out.
     *
     * Used internally by getContentPreview() and isEmpty().
     * Trimming removes the trailing newlines that separate text from markers.
     */
    private fun logicalContent(): String {
        return BLOCK_MARKER_REGEX.replace(content, "").trim()
    }

    // ─── Preview helpers ──────────────────────────────────────────────────

    /**
     * Returns a clean preview string for display in note cards.
     *
     * BEFORE (broken): "Shopping list\n⟦block:TODO:abc-123⟧..."
     * AFTER  (correct): "Shopping list"
     *
     * Strips all marker tokens, then trims whitespace, then truncates.
     * The result is always safe to show directly in the UI.
     *
     * @param maxLength Maximum character length before truncating with "…"
     */
    fun getContentPreview(maxLength: Int = 100): String {
        val clean = logicalContent()
        return if (clean.length > maxLength) {
            val truncated = clean.take(maxLength).trimEnd()
            "$truncated…"
        } else {
            clean
        }
    }

    // ─── Checklist helpers ────────────────────────────────────────────────

    /**
     * Returns true if this note contains at least one checklist block.
     */
    fun hasChecklists(): Boolean {
        return TODO_MARKER_REGEX.containsMatchIn(content)
    }

    /**
     * Returns the number of checklist blocks embedded in this note.
     */
    fun checklistBlockCount(): Int {
        return TODO_MARKER_REGEX.findAll(content).count()
    }

    // ─── Word count ───────────────────────────────────────────────────────

    /**
     * Returns the number of words in the note's logical (marker-stripped) content.
     * Used by NoteCard to show a minimal word count indicator in the card footer.
     * Returns 0 for blank notes so callers can safely skip showing the badge.
     */
    fun wordCount(): Int {
        val text = logicalContent()
        if (text.isBlank()) return 0
        return text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    }

    /**
     * Returns estimated reading time in minutes (200 wpm average).
     * Returns 0 if less than 1 minute — callers should skip showing it.
     */
    fun readingTimeMinutes(): Int {
        val words = wordCount()
        if (words < 200) return 0
        return (words / 200.0).let { kotlin.math.ceil(it).toInt() }
    }

    // ─── Image helpers ────────────────────────────────────────────────────

    /**
     * Returns true if this note contains at least one image block.
     * Used by NoteCard to show the image badge indicator.
     */
    fun hasImages(): Boolean {
        return IMAGE_MARKER_REGEX.containsMatchIn(content)
    }

    /**
     * Returns the number of image blocks embedded in this note.
     */
    fun imageCount(): Int {
        return IMAGE_MARKER_REGEX.findAll(content).count()
    }

    // ─── Audio helpers ────────────────────────────────────────────────────

    /**
     * Returns true if this note contains at least one audio block.
     * Used by NoteCard to show the audio badge indicator.
     */
    fun hasAudio(): Boolean {
        return AUDIO_MARKER_REGEX.containsMatchIn(content)
    }

    /**
     * Returns the number of audio blocks embedded in this note.
     */
    fun audioCount(): Int {
        return AUDIO_MARKER_REGEX.findAll(content).count()
    }

    // ─── State helpers ────────────────────────────────────────────────────

    /**
     * Returns true if the note has no meaningful content at all.
     */
    fun isEmpty(): Boolean {
        val hasText = title.isNotBlank() || logicalContent().isNotBlank()
        val hasBlocks = hasChecklists()
        return !hasText && !hasBlocks
    }
}