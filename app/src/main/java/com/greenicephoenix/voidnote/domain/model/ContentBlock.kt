package com.greenicephoenix.voidnote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Sealed class representing different types of content blocks.
 *
 * Each block is independently serializable.
 * This allows:
 * - Clean persistence as JSON
 * - Flexible ordering
 * - Easy future extension
 */
@Serializable
sealed class ContentBlock {

    abstract val id: String

    /**
     * Text block with rich formatting support.
     */
    @Serializable
    @SerialName("text")
    data class TextBlock(
        override val id: String = UUID.randomUUID().toString(),
        val text: String = "",
        val formats: List<FormatRange> = emptyList()
    ) : ContentBlock()

    /**
     * To-do checklist block.
     */
    @Serializable
    @SerialName("todo")
    data class TodoBlock(
        override val id: String = UUID.randomUUID().toString(),
        val items: List<TodoItem> = emptyList()
    ) : ContentBlock()

    /**
     * Image block.
     */
    @Serializable
    @SerialName("image")
    data class ImageBlock(
        override val id: String = UUID.randomUUID().toString(),
        val imagePath: String
    ) : ContentBlock()

    /**
     * Voice recording block.
     */
    @Serializable
    @SerialName("voice")
    data class VoiceBlock(
        override val id: String = UUID.randomUUID().toString(),
        val audioPath: String,
        val durationMillis: Long
    ) : ContentBlock()
}