package com.greenicephoenix.voidnote.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a single checklist item.
 */
@Serializable
data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)