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
 * v6 → No schema change. Established the migration chain; removed fallbackToDestructiveMigration.
 * v7 → Added color column to notes (nullable String — stores NoteColor enum name)
 * v8 → Added linkedNoteIds column to notes (TEXT NOT NULL DEFAULT '' — comma-separated UUIDs)
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
 * 2. Bump `version` here (e.g. 8 → 9).
 * 3. Write a MIGRATION_8_9 val below with the SQL to apply the change.
 * 4. Add it to DatabaseModule.kt → .addMigrations(..., MIGRATION_8_9)
 */
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        InlineBlockEntity::class
    ],
    version = 10,            // ← Sprint 15: passwordHash + passwordSalt added to folders
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
         * every alpha tester was on v5. This brought them to v6 safely.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No SQL needed — schema is identical to v5.
                // Room just updates the user_version pragma in the DB file.
            }
        }

        /**
         * MIGRATION_6_7 — Adds the `color` column to the notes table.
         *
         * WHY TEXT (not INTEGER)?
         * We store the NoteColor enum NAME (e.g. "RED", "BLUE") rather than
         * an ordinal. This is safer: adding or reordering enum values in future
         * sprints won't corrupt old data. An integer ordinal would shift if we
         * insert a new color variant between existing ones.
         *
         * WHY DEFAULT NULL?
         * SQLite's ALTER TABLE ... ADD COLUMN only supports adding nullable columns
         * (or columns with a constant literal default). NULL is the correct default:
         * existing notes have no color assigned, which is exactly what null means.
         *
         * ALL EXISTING ROWS:
         * Room/SQLite sets color = NULL for every existing note row automatically.
         * NoteColor.fromString(null) returns null (no color). No data loss.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT")
            }
        }

        /**
         * MIGRATION_7_8 — Adds the `linkedNoteIds` column to the notes table.
         *
         * WHY TEXT NOT NULL DEFAULT ''?
         * StringListConverter serialises List<String> to a comma-separated string.
         * An empty string '' correctly deserialises to emptyList().
         * NOT NULL + DEFAULT '' means all existing notes simply start with
         * no links, which is exactly correct — no data loss.
         *
         * WHY NOT A JOIN TABLE?
         * Note links are directional (this note → that note) and low-cardinality
         * (users won't link hundreds of notes to a single note). A comma-separated
         * column in the notes table is simpler, faster, and avoids a schema
         * redesign at this stage of the project.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN linkedNoteIds TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * MIGRATION_8_9 — Adds the `isDiaryEntry` column to the notes table.
         *
         * WHY INTEGER NOT NULL DEFAULT 0?
         * Room maps Boolean to SQLite INTEGER (0 = false, 1 = true).
         * DEFAULT 0 means all existing notes start as non-diary entries,
         * which is exactly correct — no data loss.
         *
         * WHY A SEPARATE FLAG (not a folder, not a tag)?
         * Diary entries need to be excluded from the main notes list and
         * search results at the SQL layer for performance. A dedicated
         * boolean column lets us filter with a simple WHERE clause.
         * A tag or folder approach would require a JOIN or an in-memory
         * filter on every query.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isDiaryEntry INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * MIGRATION_9_10 — Adds per-folder password columns to the folders table.
         *
         * WHY TWO COLUMNS?
         * passwordHash alone is insufficient — without a unique salt per folder,
         * two folders with the same password would have identical hashes, which
         * leaks information. Salt + hash together is the minimum secure design.
         *
         * WHY NULLABLE?
         * NULL = no password set. This is the correct default for all existing
         * folders — they were created without a password and should remain
         * accessible without one after the migration.
         *
         * SQLite ALTER TABLE can only add one column at a time, so we run
         * two execSQL statements.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN passwordHash TEXT")
                db.execSQL("ALTER TABLE folders ADD COLUMN passwordSalt TEXT")
            }
        }
    }
}