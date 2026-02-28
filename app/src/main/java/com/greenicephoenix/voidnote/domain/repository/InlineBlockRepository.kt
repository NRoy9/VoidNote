package com.greenicephoenix.voidnote.domain.repository

import com.greenicephoenix.voidnote.domain.model.InlineBlock
import kotlinx.coroutines.flow.Flow

/**
 * Repository Interface for Inline Blocks
 *
 * WHAT IS THIS?
 * This interface lives in the DOMAIN layer. It defines WHAT operations
 * are possible with inline blocks — without saying HOW they work.
 *
 * WHY AN INTERFACE AND NOT THE REAL CLASS?
 * The ViewModel will depend on this interface, not the implementation.
 * This gives us two important benefits:
 *
 * 1. TESTABILITY: In unit tests, we can create a fake/mock version of
 *    this interface that returns test data, without needing a real database.
 *
 * 2. SWAPPABILITY: If we change from Room to SQLCipher (encrypted DB)
 *    or add cloud sync, we create a new implementation — the ViewModel
 *    doesn't need to change at all.
 *
 * Hilt wires InlineBlockRepository → InlineBlockRepositoryImpl at runtime.
 * That binding lives in RepositoryModule.kt.
 */
interface InlineBlockRepository {

    // ─── READ OPERATIONS ─────────────────────────────────────────────

    /**
     * Get all inline blocks for a specific note, as a reactive stream.
     *
     * WHY Flow?
     * When a todo item is checked or unchecked, the block is updated in the
     * database. Flow automatically emits a new list to any observer.
     * The ViewModel observes this, and the UI re-renders the checklist
     * with the updated state — no manual refresh needed.
     *
     * @param noteId The ID of the note whose blocks we want.
     * @return A Flow that emits an updated list whenever blocks change.
     */
    fun getBlocksForNote(noteId: String): Flow<List<InlineBlock>>

    /**
     * Get a single block by its ID. One-shot, not reactive.
     *
     * Used when the DocumentParser encounters a marker token like
     * ⟦block:TODO:abc-123⟧ and needs to look up block "abc-123".
     *
     * Returns null if the block doesn't exist (e.g. corrupted note content).
     *
     * @param blockId The UUID from the marker token.
     */
    suspend fun getBlockById(blockId: String): InlineBlock?

    // ─── WRITE OPERATIONS ────────────────────────────────────────────

    /**
     * Insert a new inline block into the database.
     *
     * Called when the user taps the "insert TODO" button.
     * The ViewModel:
     * 1. Creates an InlineBlock with a new UUID
     * 2. Inserts the marker token ⟦block:TODO:UUID⟧ into note content
     * 3. Calls this function to save the block's data
     *
     * @param block The new block to store.
     */
    suspend fun insertBlock(block: InlineBlock)

    /**
     * Update an existing block (e.g. after a todo item is checked).
     *
     * Called every time the user interacts with a block:
     * - Checking/unchecking a todo item
     * - Adding a new todo item
     * - Editing a todo item's text
     * - Deleting a todo item
     *
     * The entire block is replaced (same ID, updated payload).
     *
     * @param block The block with updated data.
     */
    suspend fun updateBlock(block: InlineBlock)

    /**
     * Delete a single block permanently.
     *
     * Called when the user removes a block from the editor.
     * The ViewModel must also remove the marker token from note content.
     * Both operations happen together in the ViewModel (atomically from
     * the user's perspective, though not a true DB transaction yet).
     *
     * @param blockId The UUID of the block to delete.
     */
    suspend fun deleteBlock(blockId: String)

    /**
     * Delete all blocks belonging to a note.
     *
     * Called when a note is permanently deleted from trash.
     * Ensures no orphan block rows remain.
     *
     * Note: Room's CASCADE foreign key does this automatically too,
     * but an explicit call here makes the intent clear in the codebase.
     *
     * @param noteId The note whose blocks should all be deleted.
     */
    suspend fun deleteAllBlocksForNote(noteId: String)

    /**
     * Search for note IDs whose checklist items contain the given query string.
     *
     * Returns a Flow of noteIds — use this alongside text-based note search
     * to find notes where the query appears in a checklist item (not just
     * the note's text content).
     *
     * @param query The search term. Partial matches are included.
     */
    fun searchNoteIdsByBlockContent(query: String): Flow<List<String>>
}