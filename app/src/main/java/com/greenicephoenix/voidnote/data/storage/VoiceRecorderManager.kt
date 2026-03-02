package com.greenicephoenix.voidnote.data.storage

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoiceRecorderManager — wraps MediaRecorder with a clean, crash-safe API.
 *
 * ─── RECORDING LIFECYCLE ──────────────────────────────────────────────────────
 *
 * MediaRecorder has a strict state machine. Calling methods in the wrong order
 * throws IllegalStateException. This class enforces correct ordering:
 *
 *   IDLE → setOutputFile → prepare() → start() → recording... → stop() → release()
 *
 * We keep a single recorder instance and reset it properly between sessions.
 * The `isRecording` flag prevents double-start or stop-before-start.
 *
 * ─── API LEVEL HANDLING ───────────────────────────────────────────────────────
 *
 * MediaRecorder(context) constructor was added in API 31.
 * Below API 31, MediaRecorder() (no-arg) is used — deprecated in 31 but still works.
 * Our min SDK is 26, so we handle both.
 *
 * ─── AUDIO FORMAT ─────────────────────────────────────────────────────────────
 *
 * Container:  MPEG_4 (.mp4/.m4a) — universally supported, better seeking
 * Codec:      AAC                 — best quality/size ratio, hardware accelerated
 * Bitrate:    128kbps             — voice quality, ~1MB/minute
 * Sample rate: 44100 Hz           — CD quality, supported on all devices
 *
 * ─── PLAYBACK ─────────────────────────────────────────────────────────────────
 *
 * Playback uses MediaPlayer with EncryptedAudioDataSource, which serves
 * decrypted bytes from memory to MediaPlayer without writing to disk.
 * MediaDataSource requires API 23+ (our min is 26, so this is safe).
 *
 * ─── THREAD SAFETY ────────────────────────────────────────────────────────────
 *
 * MediaRecorder and MediaPlayer are NOT thread-safe. All calls must happen
 * on the same thread. Since ViewModel calls these from viewModelScope
 * (which defaults to Dispatchers.Main), this is satisfied automatically.
 */
@Singleton
class VoiceRecorderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var isRecording = false

    // ─── Recording ────────────────────────────────────────────────────────────

    /**
     * Start recording to a plain temp file.
     *
     * Creates and configures a new MediaRecorder instance. The caller
     * (ViewModel) provides the temp file path from AudioStorageManager.
     *
     * @param outputPath  Absolute path of the temp file to record into.
     *                    Must be writable and in app-private storage.
     * @return            true if recording started successfully, false on error
     */
    fun startRecording(outputPath: String): Boolean {
        if (isRecording) {
            android.util.Log.w("VoiceRecorder", "startRecording called while already recording")
            return false
        }

        return try {
            // Create MediaRecorder (API-level-aware constructor)
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                // AUDIO_SOURCE must be set first in the state machine
                setAudioSource(MediaRecorder.AudioSource.MIC)

                // MPEG_4 container + AAC codec: best compatibility + quality
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)   // 128 kbps
                setAudioSamplingRate(44_100)        // 44.1 kHz

                setOutputFile(outputPath)

                prepare()
                start()
            }

            isRecording = true
            android.util.Log.d("VoiceRecorder", "Recording started → $outputPath")
            true
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "startRecording failed: ${e.message}")
            releaseRecorder()
            false
        }
    }

    /**
     * Stop the active recording and release the MediaRecorder.
     *
     * After this call, the plain temp file at outputPath is complete and
     * ready to be encrypted by AudioStorageManager.encryptRecordingTempFile().
     *
     * @return true if stopped successfully, false if not recording or on error
     */
    fun stopRecording(): Boolean {
        if (!isRecording) {
            android.util.Log.w("VoiceRecorder", "stopRecording called but not recording")
            return false
        }

        return try {
            recorder?.apply {
                stop()
                // release() must always be called after stop() to free system resources.
                // Not calling release() leaks the microphone hardware resource.
                release()
            }
            recorder = null
            isRecording = false
            android.util.Log.d("VoiceRecorder", "Recording stopped")
            true
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "stopRecording failed: ${e.message}")
            releaseRecorder()
            false
        }
    }

    /**
     * Emergency release — called on ViewModel.onCleared() or app backgrounding.
     * Ensures the microphone is released even if recording wasn't stopped cleanly.
     */
    fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            android.util.Log.w("VoiceRecorder", "releaseRecorder: ${e.message}")
        }
        recorder = null
        isRecording = false
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    // ─── Playback ─────────────────────────────────────────────────────────────

    /**
     * Create a MediaPlayer pre-loaded with decrypted audio bytes.
     *
     * Uses EncryptedAudioDataSource (MediaDataSource) to serve bytes from
     * memory — the decrypted audio is NEVER written to disk for playback.
     *
     * The caller is responsible for:
     * - Calling mediaPlayer.start() to begin playback
     * - Calling mediaPlayer.release() when done (in onDispose or onCompletion)
     *
     * @param decryptedBytes  Plain audio bytes from AudioStorageManager.decryptToBytes()
     * @param onCompletion    Called when playback finishes naturally (not when stopped)
     * @return                Prepared MediaPlayer, or null if setup failed
     */
    fun createPlayer(
        decryptedBytes: ByteArray,
        onCompletion: () -> Unit
    ): MediaPlayer? {
        return try {
            val dataSource = EncryptedAudioDataSource(decryptedBytes)
            val player = MediaPlayer()
            player.setDataSource(dataSource)
            player.setOnCompletionListener { onCompletion() }
            player.prepare()  // synchronous prepare (bytes are already in memory)
            player
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorder", "createPlayer failed: ${e.message}")
            null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EncryptedAudioDataSource
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MediaDataSource implementation that serves decrypted audio bytes from memory.
 *
 * WHY THIS EXISTS:
 * MediaPlayer needs either a file path, URI, or MediaDataSource to read audio.
 * We don't want to write decrypted audio to disk for playback.
 * MediaDataSource (API 23+) lets us serve any ByteArray to MediaPlayer directly.
 *
 * HOW IT WORKS:
 * MediaPlayer calls readAt() with a position and buffer. We copy bytes from
 * the decrypted array at that position. readAt() returns -1 at EOF.
 *
 * THREAD SAFETY:
 * MediaPlayer may call readAt() from a background thread. Our implementation
 * is read-only (decryptedBytes is never modified) so no synchronisation needed.
 *
 * MEMORY:
 * The decryptedBytes array lives as long as this DataSource. Release the
 * MediaPlayer when done and the GC will collect the bytes.
 */
class EncryptedAudioDataSource(
    private val decryptedBytes: ByteArray
) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= decryptedBytes.size) return -1  // EOF

        val pos        = position.toInt()
        val bytesLeft  = decryptedBytes.size - pos
        val bytesToRead = minOf(size, bytesLeft)

        System.arraycopy(decryptedBytes, pos, buffer, offset, bytesToRead)
        return bytesToRead
    }

    override fun getSize(): Long = decryptedBytes.size.toLong()

    override fun close() {
        // Nothing to close — the ByteArray will be GC'd naturally.
        // Implementing this is required by the abstract class contract.
    }
}