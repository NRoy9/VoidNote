package com.greenicephoenix.voidnote.data.storage

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import okio.Buffer
import java.io.File

/**
 * Wrapper model that tells Coil to use EncryptedFileFetcher for this path.
 * Pass this as the `model` parameter to AsyncImage / SubcomposeAsyncImage
 * instead of File(path).
 */
data class EncryptedFile(val path: String)

/**
 * EncryptedFileFetcher — custom Coil Fetcher that decrypts .enc image files
 * on-the-fly before Coil renders them.
 *
 * WHY A CUSTOM FETCHER?
 * Our images are stored as AES-256-GCM ciphertext (.enc files).
 * Coil's default fetcher reads raw bytes and tries to decode them as JPEG/PNG —
 * that obviously fails on ciphertext. This fetcher intercepts the load step,
 * decrypts the bytes in memory, and hands plain image bytes to Coil's decoder.
 *
 * The decrypted bytes exist only in RAM during rendering. Never written to disk.
 *
 * HOW IT FITS IN THE CHAIN:
 *   Coil requests image load
 *       ↓ EncryptedFileFetcher.fetch() is called (because model = EncryptedFile)
 *   Read .enc file bytes from disk
 *       ↓ NoteEncryptionManager.decryptBytes() → plain bytes in memory
 *   Wrap in okio Buffer → hand to Coil decoder (BitmapDecoder)
 *       ↓
 *   Image rendered in ImageBlockComposable
 */
class EncryptedFileFetcher(
    private val data: EncryptedFile,
    private val options: Options,
    private val encryption: NoteEncryptionManager
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val file = File(data.path)
        if (!file.exists()) return null

        // Read encrypted bytes from disk
        val encryptedBytes = file.readBytes()

        // Decrypt in memory — plain bytes never written to disk
        val plainBytes = encryption.decryptBytes(encryptedBytes) ?: return null

        // Wrap in okio Buffer. Buffer implements BufferedSource which is what
        // ImageSource expects. We write all plain bytes into it.
        val buffer = Buffer().write(plainBytes)

        // ImageSource(source, context) — the Coil 2.x constructor for in-memory sources.
        // options.context is the Android Context passed through from the composable.
        return SourceResult(
            source     = ImageSource(source = buffer, context = options.context),
            mimeType   = null,     // Coil detects JPEG/PNG from the decoded bytes
            dataSource = DataSource.DISK  // tells Coil this came from disk (allows memory cache)
        )
    }

    /**
     * Factory — Coil calls create() for every image load to check if this Fetcher
     * handles the given model type. Registered in VoidNoteImageLoader.
     */
    class Factory(
        private val encryption: NoteEncryptionManager
    ) : Fetcher.Factory<EncryptedFile> {
        override fun create(
            data: EncryptedFile,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = EncryptedFileFetcher(data, options, encryption)
    }
}