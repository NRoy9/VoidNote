package com.greenicephoenix.voidnote.di

import android.content.Context
import androidx.room.Room
import com.greenicephoenix.voidnote.data.local.VoidNoteDatabase
import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module for Database
 *
 * Tells Hilt how to provide:
 * - Database instance
 * - DAOs
 *
 * @Module = This class provides dependencies
 * @InstallIn(SingletonComponent::class) = Dependencies live as long as app lives
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provide Room Database
     *
     * @Singleton = Only one instance exists
     * @Provides = Hilt will call this to get the database
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
            .fallbackToDestructiveMigration() // OK in alpha feature branch
            .build()

    }

    /**
     * Provide NoteDao
     */
    @Provides
    @Singleton
    fun provideNoteDao(database: VoidNoteDatabase): NoteDao {
        return database.noteDao()
    }

    /**
     * Provide FolderDao
     */
    @Provides
    @Singleton
    fun provideFolderDao(database: VoidNoteDatabase): FolderDao {
        return database.folderDao()
    }

    /**
     * Provide PreferencesManager
     */
    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }
}