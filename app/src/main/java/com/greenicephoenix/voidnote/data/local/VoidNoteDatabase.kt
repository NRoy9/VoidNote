package com.greenicephoenix.voidnote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.greenicephoenix.voidnote.data.local.converter.StringListConverter
import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity

/**
 * Room Database for Void Note
 *
 * This is the main database class that Room uses to:
 * - Create the SQLite database file
 * - Provide DAOs for accessing data
 * - Handle migrations when schema changes
 *
 * @Database annotation defines:
 * - entities = List of tables in the database
 * - version = Database schema version (increment when changing structure)
 * - exportSchema = Whether to export schema for version control
 */
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class VoidNoteDatabase : RoomDatabase() {

    /**
     * Provide access to NoteDao
     * Room generates the implementation
     */
    abstract fun noteDao(): NoteDao

    /**
     * Provide access to FolderDao
     * Room generates the implementation
     */
    abstract fun folderDao(): FolderDao

    companion object {
        const val DATABASE_NAME = "void_note_database"
    }
}