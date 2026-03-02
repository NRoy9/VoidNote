package com.greenicephoenix.voidnote.di

import com.greenicephoenix.voidnote.data.storage.VoidNoteImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint for accessing VoidNoteImageLoader from Composables.
 *
 * WHY AN ENTRYPOINT?
 * Jetpack Compose Composables are not Hilt injection targets — only Android
 * components (Activity, Fragment, Service) are. To access a Hilt-managed
 * singleton from a Composable, we use EntryPointAccessors.
 *
 * USAGE in a Composable:
 *
 *   val imageLoader = EntryPointAccessors.fromApplication(
 *       LocalContext.current.applicationContext,
 *       ImageLoaderEntryPoint::class.java
 *   ).imageLoader()
 *
 * This is the standard Hilt pattern for Composable injection.
 * The returned VoidNoteImageLoader is the @Singleton instance from the Hilt graph —
 * the same one used everywhere, with its memory cache shared across the app.
 *
 * PLACE THIS FILE AT:
 *   di/ImageLoaderEntryPoint.kt
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageLoaderEntryPoint {
    fun imageLoader(): VoidNoteImageLoader
}