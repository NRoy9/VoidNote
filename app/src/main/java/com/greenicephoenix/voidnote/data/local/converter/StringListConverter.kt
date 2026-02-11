package com.greenicephoenix.voidnote.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Type Converter for Room Database
 *
 * Converts List<String> ↔ String for database storage
 * Room can only store primitive types, so we need converters for complex types
 *
 * Example:
 * List: ["tag1", "tag2", "tag3"]
 * String: '["tag1","tag2","tag3"]'
 */
class StringListConverter {

    /**
     * Convert List<String> to JSON String for storage
     */
    @TypeConverter
    fun fromList(value: List<String>): String {
        return Json.encodeToString(value)
    }

    /**
     * Convert JSON String back to List<String> when reading
     */
    @TypeConverter
    fun toList(value: String): List<String> {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}