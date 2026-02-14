package com.greenicephoenix.voidnote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greenicephoenix.voidnote.data.local.converter.StringListConverter
import com.greenicephoenix.voidnote.data.local.converter.FormatRangeConverter
import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(
    StringListConverter::class,
    FormatRangeConverter::class
)
abstract class VoidNoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao

    companion object {

        const val DATABASE_NAME = "void_note_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE notes 
                    ADD COLUMN contentFormats TEXT NOT NULL DEFAULT '[]'
                    """.trimIndent()
                )
            }
        }
    }
}
