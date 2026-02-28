package com.greenicephoenix.voidnote.data.repository

import com.greenicephoenix.voidnote.data.local.dao.InlineBlockDao
import com.greenicephoenix.voidnote.data.mapper.toDomainModel
import com.greenicephoenix.voidnote.data.mapper.toDomainModels
import com.greenicephoenix.voidnote.data.mapper.toEntity
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.repository.InlineBlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of InlineBlockRepository.
 *
 * THIS IS THE REAL DATA LAYER WORK.
 * This class is the only place in the app that knows about:
 * - InlineBlockDao (Room database access)
 * - InlineBlockEntity (raw database rows)
 * - InlineBlockMapper (entity ↔ domain conversion)
 *
 * Everything above this (ViewModel, UI) works only with clean
 * InlineBlock domain objects and never sees database details.
 *
 * HOW HILT INJECTS THIS:
 * @Inject constructor tells Hilt: "to create this class, you need
 * an InlineBlockDao — which you already know how to provide from
 * DatabaseModule.kt." Hilt connects them automatically.
 *
 * @param inlineBlockDao  Room DAO injected by Hilt.
 */
class InlineBlockRepositoryImpl @Inject constructor(
    private val inlineBlockDao: InlineBlockDao
) : InlineBlockRepository {

    /**
     * Get all blocks for a note as a reactive Flow.
     *
     * HOW .map{} WORKS ON FLOW:
     * The DAO returns Flow<List<InlineBlockEntity>> — a stream of
     * raw database rows. We use .map{} to transform each emission:
     * every time the DB changes and emits a new list of entities,
     * .map converts them all to domain models before the ViewModel sees them.
     * The ViewModel always receives clean InlineBlock objects.
     */
    override fun getBlocksForNote(noteId: String): Flow<List<InlineBlock>> {
        return inlineBlockDao.getBlocksForNote(noteId)
            .map { entities -> entities.toDomainModels() }
    }

    /**
     * Get a single block by ID. Returns null if not found.
     *
     * The ?. (safe call) means: if the DAO returns null (block not found),
     * toDomainModel() is never called and we return null instead of crashing.
     */
    override suspend fun getBlockById(blockId: String): InlineBlock? {
        return inlineBlockDao.getBlockById(blockId)?.toDomainModel()
    }

    /**
     * Insert a new block into the database.
     *
     * Converts the clean InlineBlock domain model to an InlineBlockEntity
     * (which serializes the payload to JSON) and passes it to the DAO.
     */
    override suspend fun insertBlock(block: InlineBlock) {
        inlineBlockDao.insertBlock(block.toEntity())
    }

    /**
     * Update an existing block.
     *
     * Same as insert — converts domain → entity.
     * Room matches by primary key (id) and replaces the row.
     */
    override suspend fun updateBlock(block: InlineBlock) {
        inlineBlockDao.updateBlock(block.toEntity())
    }

    /**
     * Delete a single block by ID.
     *
     * The DAO runs: DELETE FROM inline_blocks WHERE id = :blockId
     */
    override suspend fun deleteBlock(blockId: String) {
        inlineBlockDao.deleteBlockById(blockId)
    }

    /**
     * Delete all blocks for a given note.
     *
     * The DAO runs: DELETE FROM inline_blocks WHERE noteId = :noteId
     * Called during permanent note deletion for explicit cleanup.
     */
    override suspend fun deleteAllBlocksForNote(noteId: String) {
        inlineBlockDao.deleteBlocksForNote(noteId)
    }

    /**
     * Delegates directly to the DAO's LIKE query.
     * No mapping needed — noteId is a plain String, not a domain model.
     */
    override fun searchNoteIdsByBlockContent(query: String): Flow<List<String>> {
        return inlineBlockDao.searchNoteIdsByPayload(query)
    }
}