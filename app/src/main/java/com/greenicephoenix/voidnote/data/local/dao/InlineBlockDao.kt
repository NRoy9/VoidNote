package com.greenicephoenix.voidnote.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greenicephoenix.voidnote.data.local.entity.InlineBlockEntity
import kotlinx.coroutines.flow.Flow

/**
 * InlineBlockDao — Room Data Access Object for the inline_blocks table.
 *
 * VERSION 5 ADDITION: getAllBlocksOnce()
 * One-shot snapshot of every block across all notes, used by
 * ImportExportManager to bundle inline blocks into the .vnbackup ZIP.
 *
 * All other queries unchanged from Sprint 4.
 */
@Dao
interface InlineBlockDao {

    // ─── READ ─────────────────────────────────────────────────────────────────

    /**
     * Reactive stream of all blocks for a specific note.
     * Re-emits whenever any block in that note changes.
     * Used by NoteEditorViewModel to keep the editor in sync.
     */
    @Query("SELECT * FROM inline_blocks WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun getBlocksForNote(noteId: String): Flow<List<InlineBlockEntity>>

    /**
     * One-shot read of a single block by its ID.
     * Returns null if not found.
     */
    @Query("SELECT * FROM inline_blocks WHERE id = :id LIMIT 1")
    suspend fun getBlockById(id: String): InlineBlockEntity?

    /**
     * One-shot snapshot of ALL blocks across ALL notes.
     * Used by ImportExportManager for export — collects every block so they
     * can be written into the backup.json alongside their parent notes.
     *
     * No ORDER BY needed — we group by noteId in the export code, and
     * insertion order doesn't matter for backup purposes.
     */
    @Query("SELECT * FROM inline_blocks")
    suspend fun getAllBlocksOnce(): List<InlineBlockEntity>

    // ─── WRITE ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: InlineBlockEntity)

    @Update
    suspend fun updateBlock(block: InlineBlockEntity)

    @Query("DELETE FROM inline_blocks WHERE id = :id")
    suspend fun deleteBlockById(id: String)

    @Query("DELETE FROM inline_blocks WHERE noteId = :noteId")
    suspend fun deleteBlocksForNote(noteId: String)

    /**
     * Search for note IDs whose block payloads contain the given query string.
     * Searches ALL block types (TODO, IMAGE, AUDIO) — not just TODO.
     * See Sprint 4 bug fix for full rationale.
     */
    @Query("SELECT DISTINCT noteId FROM inline_blocks WHERE payload LIKE '%' || :query || '%'")
    fun searchNoteIdsByPayload(query: String): Flow<List<String>>
}