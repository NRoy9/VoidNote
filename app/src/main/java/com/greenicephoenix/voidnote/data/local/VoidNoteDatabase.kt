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
 * Room Database for Void Note.
 *
 * VERSION HISTORY:
 * v1 → Initial schema (notes table)
 * v2 → Added folders table
 * v3 → Block experiment (rolled back, version number kept)
 * v4 → Added inline_blocks table
 * v5 → Added trashedAt column to notes (nullable Long)
 *
 * WHY BUMP THE VERSION FOR ONE COLUMN?
 * Room compares the declared schema (from your @Entity classes) against the
 * schema stored in the database file. If they don't match AND no migration
 * is provided, Room throws an IllegalStateException at startup.
 * Bumping the version number tells Room "I know the schema changed."
 * Since we use fallbackToDestructiveMigration() during alpha, the old DB
 * is wiped and rebuilt at v5 — data loss is acceptable at this stage.
 * Before production, we will add a proper Migration(4, 5) instead.
 */
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        InlineBlockEntity::class
    ],
    version = 5,           // ← BUMPED from 4 to 5 (added trashedAt to notes)
    exportSchema = false
)
@TypeConverters(
    StringListConverter::class,
    FormatRangeConverter::class
)
abstract class VoidNoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun inlineBlockDao(): InlineBlockDao

    companion object {
        const val DATABASE_NAME = "void_note_database"
    }
}