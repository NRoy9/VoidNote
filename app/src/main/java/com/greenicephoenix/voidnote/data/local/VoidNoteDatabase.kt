package com.greenicephoenix.voidnote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.greenicephoenix.voidnote.data.local.converter.FormatRangeConverter
import com.greenicephoenix.voidnote.data.local.converter.StringListConverter
import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.InlineBlockDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import com.greenicephoenix.voidnote.data.local.entity.InlineBlockEntity
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity

/**
 * Room Database for Void Note
 *
 * VERSION HISTORY:
 * Version 1 → Initial schema (notes table)
 * Version 2 → Added folders table
 * Version 3 → (Block experiment — rolled back, but version number remains)
 * Version 4 → Added inline_blocks table (this version)
 *
 * WHY NOT RESET TO VERSION 1?
 * Room tracks the version number in the database file itself.
 * If you go backward (e.g. 3 → 1), Room sees it as a downgrade
 * and may throw an error or behave unpredictably.
 * We always go forward. Since we're using fallbackToDestructiveMigration()
 * in alpha, the emulator's existing DB will be wiped and recreated at v4.
 *
 * @Database(entities = [...])
 * Lists every table this database contains.
 * Each entity = one table. If you forget to list a new entity here,
 * Room will NOT create its table and you'll get a runtime crash.
 *
 * exportSchema = false
 * Disables exporting the schema to a JSON file.
 * For production, set to true and commit the schema files to git.
 * For now, false keeps things simple.
 *
 * @TypeConverters(...)
 * Registers all type converters for this database.
 * Room uses these when it encounters a field type it can't store directly
 * (like List<String> or List<FormatRange>).
 * NOTE: InlineBlockEntity does NOT need TypeConverters because its payload
 * is already a plain String — we handle serialization in InlineBlockMapper.
 */
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        InlineBlockEntity::class   // ← NEW: inline blocks table
    ],
    version = 4,                   // ← BUMPED from 3 to 4
    exportSchema = false
)
@TypeConverters(
    StringListConverter::class,
    FormatRangeConverter::class
)
abstract class VoidNoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun folderDao(): FolderDao

    /**
     * Provides access to the inline_blocks table.
     * Room generates the implementation automatically.
     */
    abstract fun inlineBlockDao(): InlineBlockDao   // ← NEW

    companion object {
        const val DATABASE_NAME = "void_note_database"
    }
}