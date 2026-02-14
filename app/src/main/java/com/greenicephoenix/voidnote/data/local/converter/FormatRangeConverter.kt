package com.greenicephoenix.voidnote.data.local.converter

import androidx.room.TypeConverter
import com.greenicephoenix.voidnote.domain.model.FormatRange
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Converts List<FormatRange> to JSON string and back.
 *
 * This allows Room to persist formatting metadata.
 */
class FormatRangeConverter {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @TypeConverter
    fun fromFormatRangeList(list: List<FormatRange>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun toFormatRangeList(value: String): List<FormatRange> {
        return json.decodeFromString(value)
    }
}