package com.greenicephoenix.voidnote.data.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AudioStorageManager — handles all voice note file I/O for Void Note.
 *
 * ─── PATTERN ──────────────────────────────────────────────────────────────────
 *
 * Mirrors ImageStorageManager exactly. Same two-phase approach:
 *
 * RECORDING:
 *   1. createRecordingTempFile() → plain .aac file in filesDir/audio_tmp/
 *   2. MediaRecorder writes audio to that file (recording session)
 *   3. encryptRecordingTempFile() → encrypt → filesDir/audio/audio_<id>.enc
 *                                 → delete plain .aac
 *
 * PLAYBACK:
 *   decryptToBytes() → plain bytes in memory (ByteArray)
 *   EncryptedAudioDataSource serves bytes to MediaPlayer via MediaDataSource API
 *   → plain bytes NEVER written to disk during playback
 *
 * ─── WHY MEDIARECORDER WRITES TO PLAIN FILE FIRST? ────────────────────────────
 *
 * MediaRecorder is a system-level component that writes compressed audio
 * (AAC/MPEG-4) to a file descriptor. It does not support writing to an
 * in-memory stream or a custom output. Therefore:
 *   - We give MediaRecorder a plain temp file path in app-private storage
 *   - After stopRecording(), we read those bytes, AES-256-GCM encrypt them,
 *     write the .enc file, and DELETE the plain file
 *   - The plain file exists only for the duration of the recording session
 *   - If the app crashes mid-recording, the plain temp file is cleaned up
 *     on the next app start by cleanRecordingTempFiles()
 *
 * ─── STORAGE LAYOUT ───────────────────────────────────────────────────────────
 *
 *   filesDir/
 *     audio/            ← permanent encrypted recordings
 *       audio_<id>.enc
 *     audio_tmp/        ← plain recordings during active session ONLY
 *       record_<ts>.aac
 *
 * Both directories are app-private (mode 0700). Not visible to any other app,
 * media scanner, or file manager without root.
 *
 * ─── FILE FORMAT ──────────────────────────────────────────────────────────────
 *
 * .enc format: IV[12 bytes] + AES-256-GCM ciphertext[n bytes]
 * Identical format to image .enc files — same NoteEncryptionManager key.
 */
@Singleton
class AudioStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: NoteEncryptionManager
) {
    companion object {
        private const val AUDIO_DIR      = "audio"
        private const val AUDIO_TMP_DIR  = "audio_tmp"
        private const val ENC_EXTENSION  = ".enc"
        private const val TMP_EXTENSION  = ".aac"
    }

    // ─── Directories ──────────────────────────────────────────────────────────

    private val audioDir: File
        get() = File(context.filesDir, AUDIO_DIR).also { it.mkdirs() }

    private val audioTmpDir: File
        get() = File(context.filesDir, AUDIO_TMP_DIR).also { it.mkdirs() }

    // ─── Recording temp file ───────────────────────────────────────────────────

    /**
     * Create a plain temp file for MediaRecorder to write into.
     *
     * MediaRecorder requires a real file path — it does not support
     * in-memory streams. This file is TEMPORARY and plain (unencrypted).
     * It must be encrypted and deleted as soon as recording stops.
     *
     * The file is placed in app-private filesDir/audio_tmp/ so no other app
     * or media scanner can access it during recording.
     *
     * @return Absolute path of the created temp .aac file
     */
    fun createRecordingTempFile(): String {
        val file = File(audioTmpDir, "record_${System.currentTimeMillis()}$TMP_EXTENSION")
        file.createNewFile()
        return file.absolutePath
    }

    /**
     * Encrypt a finished recording and move it to permanent storage.
     *
     * Called immediately after MediaRecorder.stop(). Steps:
     *   1. Read all bytes from the plain .aac temp file
     *   2. AES-256-GCM encrypt them
     *   3. Write encrypted bytes to audio/<blockId>.enc
     *   4. DELETE the plain temp file — no unencrypted copy remains
     *
     * @param tempFilePath  Path returned by createRecordingTempFile()
     * @param blockId       UUID of the new AUDIO block
     * @return              Absolute path of the .enc file, or null if failed
     */
    fun encryptRecordingTempFile(tempFilePath: String, blockId: String): String? {
        val tempFile = File(tempFilePath)
        val destFile = encFile(blockId)

        return try {
            val plainBytes     = tempFile.readBytes()
            val encryptedBytes = encryption.encryptBytes(plainBytes)
            destFile.writeBytes(encryptedBytes)

            // Critical: delete the plain file immediately
            tempFile.delete()
            android.util.Log.d("AudioStorage", "Encrypted ${plainBytes.size} bytes → ${destFile.name}")

            destFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("AudioStorage", "encryptRecordingTempFile failed: ${e.message}")
            tempFile.delete()  // always delete plain even on failure
            destFile.delete()  // clean up partial .enc write
            null
        }
    }

    // ─── Playback ─────────────────────────────────────────────────────────────

    /**
     * Decrypt an audio .enc file to plain bytes for in-memory playback.
     *
     * These bytes are passed to EncryptedAudioDataSource, which serves them
     * to MediaPlayer via the MediaDataSource API — no disk write required.
     *
     * The decrypted ByteArray lives only in memory during playback. It is
     * released when the DataSource is closed and GC'd.
     *
     * @param encFilePath  Absolute path to an audio .enc file
     * @return             Plain audio bytes (AAC/MPEG-4), or null if failed
     */
    fun decryptToBytes(encFilePath: String): ByteArray? {
        return try {
            val encryptedBytes = File(encFilePath).readBytes()
            encryption.decryptBytes(encryptedBytes)
        } catch (e: Exception) {
            android.util.Log.e("AudioStorage", "decryptToBytes failed: ${e.message}")
            null
        }
    }

    // ─── Duration ─────────────────────────────────────────────────────────────

    /**
     * Read the duration of an encrypted audio file in milliseconds.
     *
     * Decrypts the full file (GCM requires complete ciphertext for auth),
     * writes to a truly-temporary file, reads duration via MediaMetadataRetriever,
     * then immediately deletes the temp file.
     *
     * WHY A TEMP FILE FOR METADATA?
     * MediaMetadataRetriever.setDataSource(ByteArray) doesn't exist.
     * It requires a file path or FileDescriptor. We write the shortest-lived
     * possible temp file — it exists for <10ms and is always deleted.
     *
     * Alternative: read duration during encryptRecordingTempFile() BEFORE
     * we delete the plain file. That's what VoiceRecorderManager does —
     * it measures duration from the plain temp file before encryption.
     * This function is a fallback for re-reading stored files.
     *
     * @param encFilePath  Absolute path to an audio .enc file
     * @return             Duration in milliseconds, or 0 if failed
     */
    fun readDurationMs(encFilePath: String): Long {
        val plainBytes = decryptToBytes(encFilePath) ?: return 0L

        // Write to a temp file just long enough to read metadata
        val tmpFile = File(audioTmpDir, "meta_tmp_${System.currentTimeMillis()}.aac")
        return try {
            tmpFile.writeBytes(plainBytes)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tmpFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            android.util.Log.e("AudioStorage", "readDurationMs failed: ${e.message}")
            0L
        } finally {
            tmpFile.delete()  // always delete
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    /**
     * Delete an encrypted audio file when its block is removed.
     * Safe to call if the file doesn't exist.
     */
    fun deleteEncFile(encFilePath: String) {
        try {
            File(encFilePath).delete()
        } catch (e: Exception) {
            android.util.Log.w("AudioStorage", "deleteEncFile failed: ${e.message}")
        }
    }

    /**
     * Delete all recording temp files.
     * Call on app startup to clean up files from crashed recording sessions.
     * Also call when the app comes to foreground as a safety net.
     */
    fun cleanRecordingTempFiles() {
        audioTmpDir.listFiles()
            ?.filter { it.name.startsWith("record_") }
            ?.forEach { it.delete() }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun encFile(blockId: String): File =
        File(audioDir, "audio_$blockId$ENC_EXTENSION")
}