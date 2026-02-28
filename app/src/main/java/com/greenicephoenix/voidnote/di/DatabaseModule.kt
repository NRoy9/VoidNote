package com.greenicephoenix.voidnote.di

import android.content.Context
import androidx.room.Room
import com.greenicephoenix.voidnote.data.local.VoidNoteDatabase
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.InlineBlockDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module for Database
 *
 * Tells Hilt how to create and provide:
 * - The Room database instance (singleton)
 * - All DAOs (each derived from the database)
 * - PreferencesManager (DataStore wrapper)
 *
 * HOW HILT WORKS (quick explanation):
 * When a ViewModel or Repository needs a NoteDao, it adds
 * @Inject constructor(private val noteDao: NoteDao)
 * Hilt sees this, looks in all @Module classes for a @Provides function
 * that returns NoteDao, calls it, and passes the result in.
 * You never manually create these objects — Hilt does it for you.
 *
 * @Module         = This object contains dependency providers
 * @InstallIn(...) = These dependencies live as long as the whole app
 * @Singleton      = Only one instance ever created (shared across app)
 * @Provides       = Hilt will call this function to create the dependency
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provide the Room Database instance.
     *
     * fallbackToDestructiveMigration():
     * In alpha, if Room detects a schema version mismatch and can't
     * find a migration path, it DESTROYS and RECREATES the database.
     * This is fine during development — we wipe the emulator manually anyway.
     * Before production release, we will replace this with proper migrations.
     */
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
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Provide NoteDao — derived from the database instance above.
     * Room generates the implementation at compile time.
     */
    @Provides
    @Singleton
    fun provideNoteDao(database: VoidNoteDatabase): NoteDao {
        return database.noteDao()
    }

    /**
     * Provide FolderDao — derived from the database instance above.
     */
    @Provides
    @Singleton
    fun provideFolderDao(database: VoidNoteDatabase): FolderDao {
        return database.folderDao()
    }

    /**
     * Provide InlineBlockDao — derived from the database instance above.
     *
     * Any class that @Inject-s InlineBlockDao will receive this instance.
     * We'll use this in the next step when we build InlineBlockRepository.
     */
    @Provides
    @Singleton
    fun provideInlineBlockDao(database: VoidNoteDatabase): InlineBlockDao {  // ← NEW
        return database.inlineBlockDao()
    }

    /**
     * Provide PreferencesManager — wraps DataStore for theme/settings.
     */
    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }
}