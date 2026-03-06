package com.greenicephoenix.voidnote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
 * v6 → No schema change. Establishes the migration chain.
 *       fallbackToDestructiveMigration() removed — data is now safe on upgrades.
 *
 * HOW ROOM MIGRATIONS WORK:
 * Room stores the current schema version inside the database file itself.
 * On app startup, Room compares the version number in the file against the
 * version declared in this @Database annotation. If they differ, it runs
 * the matching Migration(oldVersion, newVersion) object to bring the schema
 * up to date — without touching any existing rows.
 *
 * ADDING A MIGRATION IN A FUTURE SPRINT:
 * 1. Make your schema change in the relevant @Entity class.
 * 2. Bump `version` here (e.g. 6 → 7).
 * 3. Write a MIGRATION_6_7 val below with the SQL to apply the change.
 * 4. Add it to DatabaseModule.kt → .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
 *
 * EXAMPLE — adding a new column in a future sprint:
 *   val MIGRATION_6_7 = object : Migration(6, 7) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT")
 *       }
 *   }
 */
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        InlineBlockEntity::class
    ],
    version = 6,            // ← BUMPED from 5 to 6
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

        /**
         * MIGRATION_5_6 — No schema change.
         *
         * This migration exists purely to establish the migration chain.
         * All previous versions used fallbackToDestructiveMigration(), so
         * every alpha tester is already on v5. This brings them to v6 safely.
         *
         * From Sprint 4 onwards, every schema change MUST have a Migration
         * object here. Never use fallbackToDestructiveMigration() again.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No SQL needed — schema is identical to v5.
                // Room just updates the user_version pragma in the DB file.
            }
        }
    }
}