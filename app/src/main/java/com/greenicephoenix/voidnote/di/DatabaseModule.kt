package com.greenicephoenix.voidnote.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.local.VoidNoteDatabase
import com.greenicephoenix.voidnote.data.local.VoidNoteDatabase.Companion.MIGRATION_5_6
import com.greenicephoenix.voidnote.data.local.VoidNoteDatabase.Companion.MIGRATION_6_7
import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.InlineBlockDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DatabaseModule — Hilt DI module for all data-layer singletons.
 *
 * SPRINT 4 CHANGES:
 * 1. fallbackToDestructiveMigration() REMOVED → replaced with addMigrations().
 *    User data is now safe across app updates.
 *
 * 2. Foreign key enforcement ADDED via addCallback().
 *    SQLite ignores foreign key constraints by default. The PRAGMA foreign_keys = ON
 *    command must be run on every new database connection.
 *
 * SPRINT 6 CHANGES:
 * 3. MIGRATION_6_7 added — adds the `color` TEXT column to notes table.
 *
 * WHY FOREIGN KEYS MATTER FOR VOID NOTE:
 * notes.folderId references folders.id. Without FK enforcement, deleting a
 * folder leaves notes with a folderId pointing to nothing (orphan notes).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Callback that enables SQLite foreign key enforcement on every connection.
     *
     * WHY onOpen() and not onCreate()?
     * PRAGMA foreign_keys is a per-connection setting — it resets to OFF every
     * time SQLite opens a new connection to the database file. onOpen() fires
     * on every open (including the very first), so FK enforcement is always on.
     * onCreate() only fires when the DB file is first created — useless here.
     */
    private val foreignKeyCallback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    @Provides
    @Singleton
    fun provideVoidNoteDatabase(
        @ApplicationContext context: Context
    ): VoidNoteDatabase {
        return Room.databaseBuilder(
            context,
            VoidNoteDatabase::class.java,
            VoidNoteDatabase.DATABASE_NAME
        )
            // ── Migration chain ───────────────────────────────────────────────
            // Room applies these automatically in order when upgrading.
            // Add new migrations here as you create them in VoidNoteDatabase.kt.
            .addMigrations(
                MIGRATION_5_6,   // v5 → v6: no schema change (chain establishment)
                MIGRATION_6_7    // v6 → v7: adds color TEXT column to notes
            )

            // ── SQLite foreign key enforcement ────────────────────────────────
            .addCallback(foreignKeyCallback)

            .build()
    }

    @Provides
    @Singleton
    fun provideNoteDao(db: VoidNoteDatabase): NoteDao = db.noteDao()

    @Provides
    @Singleton
    fun provideFolderDao(db: VoidNoteDatabase): FolderDao = db.folderDao()

    @Provides
    @Singleton
    fun provideInlineBlockDao(db: VoidNoteDatabase): InlineBlockDao = db.inlineBlockDao()

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager = PreferencesManager(context)

    @Provides
    @Singleton
    fun provideNoteEncryptionManager(): NoteEncryptionManager = NoteEncryptionManager()
}