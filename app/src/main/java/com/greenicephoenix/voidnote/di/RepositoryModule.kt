package com.greenicephoenix.voidnote.di

import com.greenicephoenix.voidnote.data.repository.FolderRepositoryImpl
import com.greenicephoenix.voidnote.data.repository.InlineBlockRepositoryImpl
import com.greenicephoenix.voidnote.data.repository.NoteRepositoryImpl
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.InlineBlockRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module for Repositories
 *
 * WHY @Binds INSTEAD OF @Provides?
 * We use @Binds when Hilt already knows how to create the implementation
 * class (because it has @Inject constructor), and we just need to tell Hilt:
 * "when someone asks for the interface, give them this implementation."
 *
 * @Provides would require us to write the constructor call manually.
 * @Binds is more efficient — Hilt generates less code.
 *
 * RULE: @Binds functions must be abstract, and the module must be abstract class.
 * This is a Hilt requirement — abstract functions have no body (Hilt fills it in).
 *
 * SINGLETON:
 * All repositories are singletons — one instance shared across the entire app.
 * This means every ViewModel that injects NoteRepository gets the SAME instance,
 * which is important for cache consistency and Flow sharing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Bind NoteRepository interface → NoteRepositoryImpl
     *
     * When a ViewModel injects NoteRepository, Hilt provides NoteRepositoryImpl.
     */
    @Binds
    @Singleton
    abstract fun bindNoteRepository(
        noteRepositoryImpl: NoteRepositoryImpl
    ): NoteRepository

    /**
     * Bind FolderRepository interface → FolderRepositoryImpl
     */
    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        folderRepositoryImpl: FolderRepositoryImpl
    ): FolderRepository

    /**
     * Bind InlineBlockRepository interface → InlineBlockRepositoryImpl
     *
     * When NoteEditorViewModel (next step) injects InlineBlockRepository,
     * Hilt will provide InlineBlockRepositoryImpl, which uses InlineBlockDao,
     * which Hilt knows how to provide from DatabaseModule.
     *
     * The full chain Hilt resolves automatically:
     * NoteEditorViewModel
     *   → needs InlineBlockRepository
     *   → binds to InlineBlockRepositoryImpl
     *   → needs InlineBlockDao
     *   → provided by DatabaseModule.provideInlineBlockDao()
     *   → needs VoidNoteDatabase
     *   → provided by DatabaseModule.provideVoidNoteDatabase()
     *   → needs ApplicationContext
     *   → provided by Hilt automatically
     */
    @Binds
    @Singleton
    abstract fun bindInlineBlockRepository(
        inlineBlockRepositoryImpl: InlineBlockRepositoryImpl
    ): InlineBlockRepository
}