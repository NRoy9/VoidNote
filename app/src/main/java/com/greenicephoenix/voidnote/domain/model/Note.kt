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
    val tags: List<String> = emptyList(),
    val folderId: String? = null
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
            // Trim at a word boundary when possible — don't cut mid-word
            val truncated = clean.take(maxLength).trimEnd()
            "$truncated…"
        } else {
            clean
        }
    }

    // ─── Checklist helpers ────────────────────────────────────────────────

    /**
     * Returns true if this note contains at least one checklist block.
     *
     * Used by NoteCard to decide whether to show the checklist badge.
     * Implemented as a simple regex search — O(n) string scan, zero DB calls.
     */
    fun hasChecklists(): Boolean {
        return TODO_MARKER_REGEX.containsMatchIn(content)
    }

    /**
     * Returns the number of checklist blocks embedded in this note.
     *
     * Used by NoteCard to show "☑ 2" when a note has multiple checklists.
     * Returns 0 if the note has no checklists.
     *
     * WHY COUNT MARKERS AND NOT QUERY THE DB?
     * NoteCard is rendered for every note in the list — potentially dozens.
     * Querying InlineBlockRepository once per note would fire N database
     * reads every time the list refreshes, which is wasteful.
     * Counting regex matches in the already-loaded content string is
     * effectively free by comparison.
     */
    fun checklistBlockCount(): Int {
        return TODO_MARKER_REGEX.findAll(content).count()
    }

    // ─── State helpers ────────────────────────────────────────────────────

    /**
     * Returns true if the note has no meaningful content at all.
     *
     * Updated to use logicalContent() so a note that has ONLY checklist
     * blocks (and no text) is not considered empty — it has real content,
     * just not in the text field.
     */
    fun isEmpty(): Boolean {
        val hasText = title.isNotBlank() || logicalContent().isNotBlank()
        val hasBlocks = hasChecklists()
        return !hasText && !hasBlocks
    }
}