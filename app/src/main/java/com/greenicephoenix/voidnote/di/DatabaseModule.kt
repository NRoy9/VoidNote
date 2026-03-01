package com.greenicephoenix.voidnote.di

import android.content.Context
import androidx.room.Room
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.local.VoidNoteDatabase
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
 * SPRINT 4: NoteEncryptionManager added.
 *
 * It is provided explicitly (rather than relying on @Inject constructor auto-binding)
 * so it appears alongside all other data-layer singletons in one visible place,
 * and so future constructor parameters (if any) are easy to add here.
 *
 * CRITICAL: NoteEncryptionManager must be @Singleton.
 * It holds the session key in memory. Multiple instances = multiple separate
 * in-memory key references = one instance encrypted data the other can't decrypt.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

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