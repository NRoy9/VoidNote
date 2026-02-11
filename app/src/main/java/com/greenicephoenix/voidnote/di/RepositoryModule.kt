package com.greenicephoenix.voidnote.di

import com.greenicephoenix.voidnote.data.repository.FolderRepositoryImpl
import com.greenicephoenix.voidnote.data.repository.NoteRepositoryImpl
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module for Repositories
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Bind NoteRepository interface to NoteRepositoryImpl
     */
    @Binds
    @Singleton
    abstract fun bindNoteRepository(
        noteRepositoryImpl: NoteRepositoryImpl
    ): NoteRepository

    /**
     * Bind FolderRepository interface to FolderRepositoryImpl
     */
    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        folderRepositoryImpl: FolderRepositoryImpl
    ): FolderRepository
}