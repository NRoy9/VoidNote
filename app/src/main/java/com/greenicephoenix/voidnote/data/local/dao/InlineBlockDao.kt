package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greenicephoenix.voidnote.data.local.entity.InlineBlockEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the inline_blocks table.
 *
 * WHAT IS A DAO?
 * A DAO is an interface that defines all the ways your app can
 * talk to a specific database table. Room generates the actual
 * SQL and implementation code at compile time — you just declare
 * what you want, not how to do it.
 *
 * WHY @Dao?
 * The @Dao annotation tells Room: "implement this interface for me
 * and generate efficient, safe SQL for each function."
 *
 * FLOW vs SUSPEND:
 * - Functions that return Flow<T> are "reactive queries".
 *   They automatically re-emit new data whenever the table changes.
 *   Use these in ViewModels so the UI stays up-to-date automatically.
 *
 * - Functions marked `suspend` are one-shot operations.
 *   They run once and complete. Use these for insert/update/delete.
 *
 * WHY @Insert(onConflict = REPLACE)?
 * If we insert a block with an ID that already exists, REPLACE will
 * delete the old row and insert the new one. This makes upsert
 * (insert or update) a single operation.
 */
@Dao
interface InlineBlockDao {

    // ─── READ OPERATIONS ─────────────────────────────────────────────────────

    /**
     * Get all blocks belonging to a specific note, as a reactive stream.
     *
     * WHY Flow?
     * When the user adds, checks, or deletes a todo item, the block is
     * updated in the database. Flow automatically notifies the ViewModel,
     * which updates the UI. No manual refresh needed.
     *
     * @param noteId The note whose blocks we want.
     * @return A Flow that emits the updated list whenever anything changes.
     */
    @Query("SELECT * FROM inline_blocks WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun getBlocksForNote(noteId: String): Flow<List<InlineBlockEntity>>

    /**
     * Get a single block by its ID. One-shot, not reactive.
     *
     * Used by the mapper/renderer when it encounters a marker token
     * and needs to fetch the block data immediately.
     *
     * Returns null if no block with that ID exists (e.g. data corruption).
     *
     * @param id The block UUID from the marker token.
     */
    @Query("SELECT * FROM inline_blocks WHERE id = :id LIMIT 1")
    suspend fun getBlockById(id: String): InlineBlockEntity?

    // ─── WRITE OPERATIONS ─────────────────────────────────────────────────────

    /**
     * Insert a new block, or replace it if one with the same ID exists.
     *
     * Called when the user inserts a new TODO block into a note.
     *
     * @param block The block entity to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: InlineBlockEntity)

    /**
     * Update an existing block's data (e.g. when a todo item is checked).
     *
     * Room matches by primary key (id). Only the row with that id is updated.
     *
     * @param block The block with updated payload.
     */
    @Update
    suspend fun updateBlock(block: InlineBlockEntity)

    /**
     * Delete a single block by its ID.
     *
     * Called when the user deletes a TODO block from the editor.
     * The marker token in the note's content must also be removed
     * (handled in the ViewModel, not here).
     *
     * @param id The UUID of the block to delete.
     */
    @Query("DELETE FROM inline_blocks WHERE id = :id")
    suspend fun deleteBlockById(id: String)

    @Query("DELETE FROM inline_blocks WHERE noteId = :noteId")
    suspend fun deleteBlocksForNote(noteId: String)

    /**
     * Search for note IDs whose TODO block payloads contain the given query.
     *
     * HOW THE SEARCH WORKS:
     * The payload column stores JSON like:
     *   {"items":[{"id":"...","text":"Buy milk","isChecked":false,"sortOrder":0}]}
     *
     * We search it with SQLite LIKE — case-insensitive partial match.
     * If someone searches "milk", any note with a checklist item containing
     * "milk" anywhere in the text will be returned.
     *
     * WHY DISTINCT?
     * A note can have multiple blocks. If two blocks both match the query,
     * we'd get duplicate noteIds. DISTINCT ensures each noteId appears once.
     *
     * WHY NOT FTS (Full Text Search)?
     * FTS would be faster for large datasets, but requires a virtual table
     * and additional schema setup. LIKE is sufficient for Phase 1.
     * We can upgrade to FTS in a future sprint if performance is an issue.
     *
     * @param query The search term (no wildcards needed — added by the query).
     * @return A Flow of distinct noteIds whose block payloads match.
     */
    @Query("SELECT DISTINCT noteId FROM inline_blocks WHERE type = 'TODO' AND payload LIKE '%' || :query || '%'")
    fun searchNoteIdsByPayload(query: String): Flow<List<String>>
}