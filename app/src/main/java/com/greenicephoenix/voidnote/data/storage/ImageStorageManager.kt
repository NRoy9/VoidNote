package com.greenicephoenix.voidnote.data.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ImageStorageManager — handles all image file I/O for Void Note.
 *
 * ─── RESPONSIBILITIES ─────────────────────────────────────────────────────────
 *
 * 1. GALLERY PICK: read the URI → encrypt bytes → write .enc file to filesDir
 * 2. CAMERA CAPTURE: create a temp file in filesDir → return a FileProvider URI
 *    the camera app writes to → encrypt the captured bytes → delete the temp file
 * 3. DELETE: delete .enc file when a block is removed
 *
 * ─── STORAGE LOCATION ─────────────────────────────────────────────────────────
 *
 * All encrypted files live in:
 *   context.filesDir / "images" / "image_<blockId>.enc"
 *
 * filesDir is app-private (mode 0700):
 * - Not visible to other apps or the gallery
 * - Not accessible without root
 * - NOT included in Android auto-backup by default (we configure this in
 *   backup_rules.xml to exclude it — encrypted bytes without the key are useless)
 * - Deleted when the app is uninstalled
 *
 * Camera temp files live in:
 *   context.filesDir / "camera_tmp" / "capture_<timestamp>.jpg"
 *
 * These are plain JPEG temporarily — only exist during the camera session.
 * They are encrypted and deleted immediately after capture completes.
 *
 * ─── CAMERA AND GALLERY PRIVACY ──────────────────────────────────────────────
 *
 * GALLERY PICK:
 * The source file in the gallery is NEVER modified or deleted. We copy the
 * bytes, encrypt them, and store the encrypted copy. The gallery photo stays
 * untouched — the user chose to include it and may want it there for other apps.
 *
 * CAMERA CAPTURE:
 * The FileProvider URI given to the camera app points to camera_tmp/ inside
 * filesDir. The camera writes directly here. The photo NEVER appears in DCIM
 * or any gallery. After capture, we encrypt the file in place (temp → .enc).
 *
 * ─── WHY NOT MediaStore FOR CAMERA? ──────────────────────────────────────────
 *
 * MediaStore.createImageUri() writes to the shared media collection (DCIM/gallery).
 * Even if we delete the MediaStore entry after, the file may persist and appear
 * in gallery apps until the system scans. FileProvider with filesDir avoids this
 * entirely — the camera writes to our private storage from the start.
 *
 * ─── FILE FORMAT ──────────────────────────────────────────────────────────────
 *
 * .enc format: IV[12 bytes] + AES-256-GCM ciphertext[n bytes]
 * No headers, no metadata — just the raw encrypted bytes.
 * Decrypted by EncryptedFileFetcher when Coil requests the image.
 */
@Singleton
class ImageStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: NoteEncryptionManager
) {
    companion object {
        private const val IMAGES_DIR     = "images"
        private const val CAMERA_TMP_DIR = "camera_tmp"
        private const val ENC_EXTENSION  = ".enc"
        private const val TMP_EXTENSION  = ".jpg"
    }

    // ─── Directories (created lazily) ─────────────────────────────────────────

    private val imagesDir: File
        get() = File(context.filesDir, IMAGES_DIR).also { it.mkdirs() }

    private val cameraTmpDir: File
        get() = File(context.filesDir, CAMERA_TMP_DIR).also { it.mkdirs() }

    // ─── Gallery pick ─────────────────────────────────────────────────────────

    /**
     * Copy a content URI (from the photo picker) to encrypted app-private storage.
     *
     * @param imageUri  Content URI returned by PickVisualMedia
     * @param blockId   The UUID of the new IMAGE block — used as the filename
     * @return          Absolute path of the written .enc file, or null if failed
     */
    fun saveFromUri(imageUri: Uri, blockId: String): String? {
        val destFile = encFile(blockId)

        return try {
            val plainBytes = context.contentResolver.openInputStream(imageUri)?.use {
                it.readBytes()
            } ?: return null

            val encryptedBytes = encryption.encryptBytes(plainBytes)
            destFile.writeBytes(encryptedBytes)

            destFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("ImageStorage", "saveFromUri failed: ${e.message}")
            destFile.delete()  // clean up partial write
            null
        }
    }

    // ─── Camera capture ───────────────────────────────────────────────────────

    /**
     * Create a temporary plain-JPEG file and return a FileProvider URI for it.
     *
     * Pass this URI to the TakePicture contract as the output destination.
     * The camera app writes the JPEG here (inside filesDir — never in gallery).
     * After the camera returns, call encryptCameraTempFile() to encrypt it.
     *
     * FileProvider is required because content:// URIs (not file://) must be
     * used for sharing files with other apps (the camera) since Android 7.0.
     *
     * @return Pair of (FileProvider content URI, absolute path of the temp file)
     */
    fun createCameraTempFile(): Pair<Uri, String> {
        val tempFile = File(cameraTmpDir, "capture_${System.currentTimeMillis()}$TMP_EXTENSION")
        tempFile.createNewFile()

        // FileProvider converts the file:// path to a content:// URI that the
        // camera app is allowed to write to. The authority must match the one
        // declared in AndroidManifest.xml.
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        return Pair(uri, tempFile.absolutePath)
    }

    /**
     * Encrypt a camera temp file and move it to the permanent images directory.
     *
     * Called after the camera confirms capture (TakePicture returns true).
     *
     * Steps:
     * 1. Read plain JPEG bytes from the temp file
     * 2. Encrypt with AES-256-GCM
     * 3. Write encrypted bytes to images/<blockId>.enc
     * 4. Delete the temp plain JPEG (no trace left)
     *
     * @param tempFilePath  Absolute path returned by createCameraTempFile()
     * @param blockId       UUID of the new IMAGE block
     * @return              Absolute path of the .enc file, or null if failed
     */
    fun encryptCameraTempFile(tempFilePath: String, blockId: String): String? {
        val tempFile = File(tempFilePath)
        val destFile = encFile(blockId)

        return try {
            val plainBytes     = tempFile.readBytes()
            val encryptedBytes = encryption.encryptBytes(plainBytes)
            destFile.writeBytes(encryptedBytes)

            // Delete the plain temp file — this is the critical step that ensures
            // no unencrypted copy remains anywhere on device.
            tempFile.delete()

            destFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("ImageStorage", "encryptCameraTempFile failed: ${e.message}")
            tempFile.delete()   // always delete temp even on failure
            destFile.delete()   // clean up partial .enc write
            null
        }
    }

    // ─── Read dimensions ──────────────────────────────────────────────────────

    /**
     * Read the dimensions of an encrypted image file WITHOUT decrypting it fully.
     *
     * Decrypts all bytes (GCM requires the full ciphertext to authenticate),
     * then uses BitmapFactory.inJustDecodeBounds to read header only — no pixels
     * allocated. This avoids loading a 10MB decrypted bitmap just to get width/height.
     *
     * @param encFilePath  Absolute path to a .enc file
     * @return             Pair(width, height) in pixels, or (0, 0) if failed
     */
    fun readDimensions(encFilePath: String): Pair<Int, Int> {
        return try {
            val encryptedBytes = File(encFilePath).readBytes()
            val plainBytes     = encryption.decryptBytes(encryptedBytes) ?: return Pair(0, 0)

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size, options)

            Pair(
                options.outWidth.coerceAtLeast(0),
                options.outHeight.coerceAtLeast(0)
            )
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    /**
     * Delete an encrypted image file when its block is removed.
     *
     * Safe to call even if the file doesn't exist (e.g. already deleted).
     *
     * @param encFilePath  Absolute path stored in InlineBlockPayload.Image.filePath
     */
    fun deleteEncFile(encFilePath: String) {
        try {
            File(encFilePath).delete()
        } catch (e: Exception) {
            // Log but don't crash — the DB row will be deleted regardless
            android.util.Log.w("ImageStorage", "deleteEncFile failed: ${e.message}")
        }
    }

    /**
     * Delete all camera temp files. Call on app startup to clean up
     * any temp files left over from a crashed camera session.
     */
    fun cleanCameraTempFiles() {
        cameraTmpDir.listFiles()?.forEach { it.delete() }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Returns the File for a block's encrypted image, whether or not it exists. */
    private fun encFile(blockId: String): File =
        File(imagesDir, "image_$blockId$ENC_EXTENSION")
}