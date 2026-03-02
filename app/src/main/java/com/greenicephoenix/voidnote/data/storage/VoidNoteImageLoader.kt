package com.greenicephoenix.voidnote.data.storage

import android.content.Context
import coil.ImageLoader
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoidNoteImageLoader — provides the app's singleton Coil ImageLoader.
 *
 * WHY A CUSTOM IMAGELOADER?
 * Coil's default ImageLoader doesn't know about our .enc file format.
 * We register EncryptedFileFetcher here so that whenever an EncryptedFile
 * model is passed to AsyncImage, Coil routes it through our custom fetcher
 * which decrypts on-the-fly.
 *
 * SINGLETON:
 * ImageLoader maintains its own memory cache and disk cache. Creating multiple
 * instances wastes memory and breaks cache sharing. One shared instance is correct.
 *
 * USAGE in Composables:
 * Instead of passing `model = File(path)` to AsyncImage, pass:
 *   model = EncryptedFile(path)
 * and use this ImageLoader:
 *   imageLoader = voidNoteImageLoader.get()
 *
 * The actual wiring is in ImageBlockComposable, which receives this loader
 * via CompositionLocal or direct injection.
 */
@Singleton
class VoidNoteImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: NoteEncryptionManager
) {
    /**
     * The configured ImageLoader instance.
     * Built once and reused for the lifetime of the app.
     */
    val loader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                // Register our custom fetcher for EncryptedFile models.
                // Coil checks fetchers in registration order — EncryptedFileFetcher
                // handles EncryptedFile, everything else falls through to defaults.
                add(EncryptedFileFetcher.Factory(encryption))
            }
            // Memory cache: allow up to 25% of available memory for decoded bitmaps.
            // Decoded (decrypted) bitmaps are cached in memory for smooth scrolling.
            // Memory cache is cleared when the app goes to background — plain decoded
            // bitmaps do not persist on disk.
            .memoryCache {
                coil.memory.MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Disk cache: DISABLED intentionally.
            // We don't want Coil writing decoded (plain) image bytes to its disk cache.
            // The source of truth is always the .enc file — Coil decrypts it fresh
            // when the memory cache misses (app restart, low memory).
            .diskCache(null)
            .build()
    }
}