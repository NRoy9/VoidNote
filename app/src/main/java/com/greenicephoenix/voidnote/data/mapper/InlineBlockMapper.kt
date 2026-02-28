package com.greenicephoenix.voidnote.data.mapper

import com.greenicephoenix.voidnote.data.local.entity.InlineBlockEntity
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.domain.model.InlineBlockType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mapper functions to convert between InlineBlockEntity (database) and InlineBlock (domain).
 *
 * WHY DO WE NEED A MAPPER?
 * The database entity stores the payload as a raw JSON String —
 * Room doesn't know about sealed classes. The domain model has a
 * typed InlineBlockPayload. The mapper bridges that gap cleanly,
 * keeping both layers independent of each other.
 *
 * DATABASE ENTITY (InlineBlockEntity):
 *   type    = "TODO"  (String)
 *   payload = '{"items":[{"id":"...","text":"Buy milk","isChecked":false,"sortOrder":0}]}'
 *
 * DOMAIN MODEL (InlineBlock):
 *   type    = InlineBlockType.TODO  (enum)
 *   payload = InlineBlockPayload.Todo(items = listOf(TodoItem(...)))
 *
 * JSON INSTANCE:
 * We use a shared Json instance with ignoreUnknownKeys = true.
 * This means if we add new fields to a payload in a future version,
 * old stored JSON with missing fields won't crash the app.
 */

/**
 * Shared kotlinx.serialization Json instance.
 * ignoreUnknownKeys: Don't crash if JSON has extra fields we don't know about.
 *                    Important for forward-compatibility with future app versions.
 */
private val json = Json { ignoreUnknownKeys = true }

// ─── Entity → Domain ───────────────────────────────────────────────────────

/**
 * Convert InlineBlockEntity (database row) → InlineBlock (domain model).
 *
 * This is called when we read blocks from the database and need to
 * pass them up to the ViewModel and UI.
 *
 * The key step here is deserializing the JSON payload string into
 * the appropriate InlineBlockPayload subclass based on the type.
 *
 * @throws IllegalArgumentException if the type string is unrecognized
 * @throws kotlinx.serialization.SerializationException if JSON is malformed
 */
fun InlineBlockEntity.toDomainModel(): InlineBlock {

    // Convert the type string (e.g. "TODO") back to the enum value
    val blockType = InlineBlockType.valueOf(this.type)

    // Deserialize the JSON payload into the correct typed payload class
    val blockPayload: InlineBlockPayload = when (blockType) {
        InlineBlockType.TODO -> {
            // Decode the JSON string into a TodoPayload wrapper
            json.decodeFromString<InlineBlockPayload.Todo>(this.payload)
        }
        InlineBlockType.IMAGE -> {
            json.decodeFromString<InlineBlockPayload.Image>(this.payload)
        }
        InlineBlockType.AUDIO -> {
            json.decodeFromString<InlineBlockPayload.Audio>(this.payload)
        }
        InlineBlockType.DRAWING -> {
            // Drawing not yet implemented — return empty todo as placeholder
            // This case should not occur in production yet
            InlineBlockPayload.Todo(items = emptyList())
        }
    }

    return InlineBlock(
        id = this.id,
        noteId = this.noteId,
        type = blockType,
        payload = blockPayload,
        createdAt = this.createdAt
    )
}

// ─── Domain → Entity ───────────────────────────────────────────────────────

/**
 * Convert InlineBlock (domain model) → InlineBlockEntity (database row).
 *
 * This is called when we want to save a block to the database —
 * either on creation or after an update (e.g. a todo item was checked).
 *
 * The key step here is serializing the typed InlineBlockPayload back
 * into a JSON string for storage.
 */
fun InlineBlock.toEntity(): InlineBlockEntity {

    // Serialize the payload sealed class to JSON based on its type
    val payloadJson: String = when (val p = this.payload) {
        is InlineBlockPayload.Todo -> json.encodeToString(p)
        is InlineBlockPayload.Image -> json.encodeToString(p)
        is InlineBlockPayload.Audio -> json.encodeToString(p)
    }

    return InlineBlockEntity(
        id = this.id,
        noteId = this.noteId,
        type = this.type.name,     // Enum → String: InlineBlockType.TODO → "TODO"
        payload = payloadJson,
        createdAt = this.createdAt
    )
}

// ─── List Extensions ───────────────────────────────────────────────────────

/**
 * Convert a list of InlineBlockEntity → list of InlineBlock.
 * Convenience extension, same pattern as NoteMapper.
 */
fun List<InlineBlockEntity>.toDomainModels(): List<InlineBlock> {
    return this.map { it.toDomainModel() }
}