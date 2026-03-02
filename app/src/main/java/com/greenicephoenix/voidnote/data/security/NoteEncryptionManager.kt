package com.greenicephoenix.voidnote.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NoteEncryptionManager — the single cryptographic authority for Void Note.
 *
 * ─── ENCRYPTION SCOPE (Sprint 5 update) ──────────────────────────────────────
 *
 * Previously this class only encrypted text (note title, content, tags) via the
 * String-based encrypt()/decrypt() methods.
 *
 * Sprint 5 adds encryptBytes()/decryptBytes() — the same AES-256-GCM algorithm
 * but operating on raw ByteArray instead of String. This is used for image files
 * (and future audio files). The same session key encrypts both text and files,
 * so the entire note — words AND images — is protected by the user's vault password.
 *
 * ─── FILE ENCRYPTION FORMAT ───────────────────────────────────────────────────
 *
 * Encrypted files are stored as:
 *   IV[12 bytes] + GCM_ciphertext[n bytes]
 *
 * Written directly to disk — no Base64 encoding (unlike text encryption which
 * uses Base64 for safe string storage). Raw bytes are more efficient for large
 * binary files.
 *
 * ─── TWO KEYS ─────────────────────────────────────────────────────────────────
 *
 * KEY 1 — Master Key (derived from vault password via PBKDF2)
 * Used for: encrypt/decrypt text AND encryptBytes/decryptBytes
 *
 * KEY 2 — Wrap Key (Android Keystore, hardware-backed)
 * Used for: wrapping/unwrapping the Master Key for safe DataStore storage
 *
 * ─── SECURITY PROPERTIES ─────────────────────────────────────────────────────
 *
 * - All encryption: AES-256-GCM (authenticated — detects tampering)
 * - Key derivation: PBKDF2-HMAC-SHA256, 100,000 iterations
 * - Salt: 16 bytes random, stored in DataStore + included in .vnbackup
 * - IV: 12 bytes random per call — fresh IV for every text AND every file
 * - Files never stored in gallery — written to app-private filesDir only
 * - Camera photos written directly to filesDir via FileProvider — never to DCIM
 */
@Singleton
class NoteEncryptionManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS_WRAP    = "void_note_wrap_key"
        private const val TRANSFORMATION    = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH     = 12    // bytes
        private const val GCM_TAG_LENGTH    = 128   // bits
        private const val WRAP_KEY_SIZE     = 256   // bits
        private const val MASTER_KEY_SIZE   = 256   // bits
        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_ALGORITHM  = "PBKDF2WithHmacSHA256"
    }

    /** Session key — null until activateKey() is called after vault unlock/setup. */
    private var sessionKey: SecretKey? = null

    // ─── Public API ───────────────────────────────────────────────────────────

    fun isReady(): Boolean = sessionKey != null

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec    = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, MASTER_KEY_SIZE)
        val rawKey  = factory.generateSecret(spec).encoded
        return SecretKeySpec(rawKey, "AES")
    }

    fun wrapAndEncode(masterKey: SecretKey): String {
        val wrapKey = getOrCreateWrapKey()
        val cipher  = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey)

        val iv      = cipher.iv
        val wrapped = cipher.doFinal(masterKey.encoded)

        val combined = ByteArray(iv.size + wrapped.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(wrapped, 0, combined, iv.size, wrapped.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun activateKey(masterKey: SecretKey) {
        sessionKey = masterKey
    }

    fun tryLoadFromWrapped(wrappedBase64: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            val wrapKey = keyStore.getKey(KEY_ALIAS_WRAP, null) as? SecretKey
                ?: return false

            val combined = Base64.decode(wrappedBase64, Base64.NO_WRAP)
            val iv       = combined.copyOfRange(0, GCM_IV_LENGTH)
            val wrapped  = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            sessionKey = SecretKeySpec(cipher.doFinal(wrapped), "AES")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun generateRandomPassword(): String {
        val lower   = "abcdefghjkmnpqrstuvwxyz"
        val upper   = "ABCDEFGHJKMNPQRSTUVWXYZ"
        val digits  = "23456789"
        val special = "!@#\$%&*"
        val required = listOf(lower.random(), upper.random(), digits.random(), special.random())
        val all  = lower + upper + digits + special
        val rest = (1..4).map { all.random() }
        return (required + rest).shuffled().joinToString("")
    }

    // ─── Text encryption (note title / content / tags) ────────────────────────

    /**
     * Encrypt a plaintext String → Base64(IV + ciphertext).
     * Used for note title, content, and each tag.
     */
    fun encrypt(plaintext: String): String {
        val key = requireKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv         = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt a Base64(IV + ciphertext) String → plaintext.
     * Handles legacy plain-text values gracefully (migration safety).
     */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""

        val key = requireKey()

        return try {
            val combined       = Base64.decode(ciphertext, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ciphertext  // too short → plain text

            val iv             = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            ciphertext  // not valid Base64 → plain text from before encryption
        } catch (e: Exception) {
            ""  // decryption failed (wrong key, tampered data)
        }
    }

    // ─── Binary file encryption (images, audio) ───────────────────────────────

    /**
     * Encrypt raw file bytes (image, audio) → ByteArray ready to write to disk.
     *
     * Output format: IV[12 bytes] + GCM_ciphertext[n bytes]
     *
     * WHY NOT Base64 FOR FILES?
     * Base64 expands data by ~33%. A 3MB photo becomes 4MB of Base64.
     * For text (a few KB) this overhead is negligible. For binary files it
     * wastes significant storage. Raw bytes written to .enc files are fine
     * because file systems handle arbitrary bytes natively — unlike JSON/DB
     * which need text-safe encoding.
     *
     * A fresh 12-byte IV is generated per call. Never reuse IVs.
     *
     * @param plainBytes  Raw bytes of the original file (JPEG, PNG, etc.)
     * @return            IV + ciphertext — write this directly to the .enc file
     */
    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        val key = requireKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv         = cipher.iv
        val ciphertext = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return combined
    }

    /**
     * Decrypt a .enc file's bytes back to the original file bytes.
     *
     * Reads IV from the first 12 bytes, decrypts the rest.
     * Called by EncryptedFileFetcher inside Coil to decrypt images on-the-fly
     * before they are rendered.
     *
     * @param encryptedBytes  The full content of a .enc file (IV + ciphertext)
     * @return                Original file bytes (JPEG, PNG, etc.), or null if
     *                        decryption fails (wrong key, tampered file).
     */
    fun decryptBytes(encryptedBytes: ByteArray): ByteArray? {
        if (encryptedBytes.size <= GCM_IV_LENGTH) return null

        val key = requireKey()

        return try {
            val iv         = encryptedBytes.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = encryptedBytes.copyOfRange(GCM_IV_LENGTH, encryptedBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // Authentication tag mismatch (tampered) or wrong key
            null
        }
    }

    // ─── Salt helpers ─────────────────────────────────────────────────────────

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        return salt
    }

    fun encodeSalt(salt: ByteArray): String =
        Base64.encodeToString(salt, Base64.NO_WRAP)

    fun decodeSalt(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun requireKey(): SecretKey =
        sessionKey ?: throw IllegalStateException(
            "Encryption key not loaded. Vault must be unlocked before encrypting/decrypting."
        )

    private fun getOrCreateWrapKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        val existing = keyStore.getKey(KEY_ALIAS_WRAP, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_WRAP,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(WRAP_KEY_SIZE)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}