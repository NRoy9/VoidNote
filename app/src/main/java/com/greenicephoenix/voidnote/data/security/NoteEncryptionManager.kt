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
 * ─── HOW IT ALL FITS TOGETHER ────────────────────────────────────────────────
 *
 * There are TWO keys involved. Understanding both is essential:
 *
 * KEY 1 — Master Key (derived from vault password)
 * ─────────────────────────────────────────────────
 * Derived via PBKDF2(password, salt, 100_000 iterations) → 256-bit key.
 * This is the key that actually encrypts/decrypts your note content.
 *
 * Why PBKDF2? Because the same password + same salt = always the same key,
 * on any device, at any time. This is what makes cross-device restore possible.
 *
 * KEY 2 — Wrap Key (generated and stored in Android Keystore)
 * ────────────────────────────────────────────────────────────
 * Generated once by Android Keystore (hardware-backed, never leaves the device).
 * Its only purpose is to encrypt (wrap) the Master Key so it can be stored
 * safely in DataStore. On every app launch, the Wrap Key decrypts the stored
 * Master Key back into memory.
 *
 * Why two keys? Because we need the Master Key to be:
 * (a) derivable from password on any device (PBKDF2, not hardware-bound)
 * (b) stored securely on-device so you don't type your password on every launch
 *
 * Solution: derive with PBKDF2, then protect the derived key with Keystore.
 *
 * ─── DAILY LAUNCH FLOW ───────────────────────────────────────────────────────
 *
 *   DataStore: wrappedMasterKey (encrypted bytes, base64)
 *        ↓ unwrap using Keystore Wrap Key
 *   sessionKey (SecretKey in memory, this session only)
 *        ↓ used by NoteRepositoryImpl
 *   Note content encrypted/decrypted transparently
 *
 * ─── FIRST SETUP FLOW ────────────────────────────────────────────────────────
 *
 *   User enters vault password
 *        ↓ PBKDF2 with random 16-byte salt
 *   masterKey (256-bit SecretKey)
 *        ↓ wrap using Keystore Wrap Key (generated if first time)
 *   wrappedMasterKey → stored in DataStore
 *   salt → stored in DataStore (also included in .vnbackup exports)
 *
 * ─── NEW DEVICE / REINSTALL ──────────────────────────────────────────────────
 *
 *   No wrappedMasterKey in DataStore (or Keystore Wrap Key gone)
 *   SplashViewModel detects this → route to VaultUnlockScreen
 *   User enters vault password
 *        ↓ PBKDF2 with salt from backup file
 *   Same masterKey as before (same password + same salt = same key)
 *   Notes decrypted correctly
 *
 * ─── SECURITY PROPERTIES ─────────────────────────────────────────────────────
 *
 * - Note content: AES-256-GCM (authenticated encryption — detects tampering)
 * - Key derivation: PBKDF2-HMAC-SHA256, 100,000 iterations (OWASP 2023 minimum)
 * - Salt: 16 bytes random (NIST SP 800-132 recommendation)
 * - IV: 12 bytes random per encryption (GCM standard, prevents IV reuse)
 * - Wrap Key: hardware-backed Keystore AES-256-GCM
 *
 * - Session key lives in process memory only (normal for encrypted note apps)
 * - Wrap Key never leaves the device hardware
 * - Master Key at rest is always wrapped by the Keystore Wrap Key
 *
 * @Singleton — one instance per app session. Critically important because
 * sessionKey is held in memory here. Multiple instances = multiple caches = bugs.
 */
@Singleton
class NoteEncryptionManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER  = "AndroidKeyStore"
        private const val KEY_ALIAS_WRAP     = "void_note_wrap_key"   // Keystore wrap key
        private const val TRANSFORMATION     = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH      = 12    // bytes — NIST-recommended for GCM
        private const val GCM_TAG_LENGTH     = 128   // bits — maximum auth tag
        private const val WRAP_KEY_SIZE      = 256   // bits
        private const val MASTER_KEY_SIZE    = 256   // bits
        private const val PBKDF2_ITERATIONS  = 100_000
        private const val PBKDF2_ALGORITHM   = "PBKDF2WithHmacSHA256"
    }

    /**
     * The master key for this session.
     * Set once via loadFromWrapped() or deriveAndStore().
     * Used by every encrypt/decrypt call.
     * null = not yet loaded (app just started and setup hasn't run yet).
     */
    private var sessionKey: SecretKey? = null

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * True if the session key has been loaded and encryption is ready.
     * SplashViewModel checks this to decide whether to show VaultUnlockScreen.
     */
    fun isReady(): Boolean = sessionKey != null

    /**
     * STEP 1 of setup: derive the master key from the user's vault password.
     *
     * Called from VaultSetupViewModel and VaultUnlockViewModel.
     *
     * @param password The vault password entered by the user
     * @param salt     16 random bytes generated at vault setup time.
     *                 Stored in DataStore and included in .vnbackup exports.
     *                 NOT secret — just prevents precomputed rainbow table attacks.
     * @return The derived SecretKey — pass to [wrapAndEncode] to store it
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            MASTER_KEY_SIZE
        )
        val rawKey = factory.generateSecret(spec).encoded
        // Convert raw bytes to a usable SecretKey with AES algorithm label
        return SecretKeySpec(rawKey, "AES")
    }

    /**
     * STEP 2 of setup: wrap the derived master key using the Keystore Wrap Key
     * so it can be stored safely in DataStore.
     *
     * "Wrapping" = encrypting the master key bytes with the Keystore-backed key.
     * The Keystore key never leaves hardware — wrapping happens inside the HSM.
     *
     * @param masterKey The SecretKey returned from [deriveKey]
     * @return Base64-encoded string of (IV + wrapped key bytes) — store this in DataStore
     */
    fun wrapAndEncode(masterKey: SecretKey): String {
        val wrapKey = getOrCreateWrapKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey)

        val iv = cipher.iv                              // 12-byte random IV
        val wrapped = cipher.doFinal(masterKey.encoded) // encrypt the raw key bytes

        val combined = ByteArray(iv.size + wrapped.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(wrapped, 0, combined, iv.size, wrapped.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Activate the master key for this session.
     *
     * Called after [deriveKey] during setup or unlock.
     * Sets the session key so [encrypt] and [decrypt] can use it.
     *
     * @param masterKey The derived SecretKey from [deriveKey]
     */
    fun activateKey(masterKey: SecretKey) {
        sessionKey = masterKey
    }

    /**
     * Try to load the session key from the stored wrapped key.
     *
     * Called by SplashViewModel on every app launch. If successful,
     * the app opens without asking for the vault password.
     *
     * @param wrappedBase64 The value previously stored by [wrapAndEncode]
     * @return true if successfully loaded, false if the Keystore wrap key
     *         was wiped (reinstall/hardware change) — shows VaultUnlockScreen
     */
    fun tryLoadFromWrapped(wrappedBase64: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            // If the wrap key doesn't exist in Keystore, we can't unwrap
            val wrapKey = keyStore.getKey(KEY_ALIAS_WRAP, null) as? SecretKey
                ?: return false

            val combined = Base64.decode(wrappedBase64, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val wrapped = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val rawKeyBytes = cipher.doFinal(wrapped)
            sessionKey = SecretKeySpec(rawKeyBytes, "AES")
            true
        } catch (e: Exception) {
            // Keystore key wiped (reinstall, factory reset, hardware change)
            false
        }
    }

    /**
     * Generate a random 8-character vault password.
     *
     * Guarantees at least one lowercase, one uppercase, one digit, one special
     * character. Then shuffles so the types don't appear in a predictable order.
     *
     * Characters chosen to avoid visual ambiguity (no 0/O, 1/l/I).
     */
    fun generateRandomPassword(): String {
        val lower   = "abcdefghjkmnpqrstuvwxyz"     // no i, l
        val upper   = "ABCDEFGHJKMNPQRSTUVWXYZ"     // no I, O
        val digits  = "23456789"                     // no 0, 1
        val special = "!@#\$%&*"

        val required = listOf(
            lower.random(),
            upper.random(),
            digits.random(),
            special.random()
        )
        val all = lower + upper + digits + special
        val rest = (1..4).map { all.random() }

        return (required + rest).shuffled().joinToString("")
    }

    // ─── Note encryption / decryption ─────────────────────────────────────────

    /**
     * Encrypt a plaintext string using the session master key (AES-256-GCM).
     *
     * A fresh 12-byte random IV is generated for EVERY call. GCM security
     * requires a unique IV per (key, message) pair — reusing IVs is catastrophic.
     *
     * Output format: Base64( IV[12 bytes] + GCM_ciphertext[n bytes] )
     * The IV is prepended so [decrypt] can extract it without extra storage.
     *
     * Throws IllegalStateException if [activateKey] hasn't been called yet.
     */
    fun encrypt(plaintext: String): String {
        val key = sessionKey
            ?: throw IllegalStateException("Encryption key not loaded. Call activateKey() first.")

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
     * Decrypt a ciphertext string back to plaintext using the session master key.
     *
     * GCM authentication: if the ciphertext is tampered with, decryption fails
     * and we return empty string rather than crashing. The note shows as blank —
     * bad UX but correct security behaviour.
     *
     * Also handles the migration case: if the stored value is plain text from
     * before encryption was introduced (e.g. alpha dev data), returns it as-is.
     */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""

        val key = sessionKey
            ?: throw IllegalStateException("Encryption key not loaded. Call activateKey() first.")

        return try {
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)

            // If too short to contain a valid IV + auth tag, it's probably plain text
            if (combined.size <= GCM_IV_LENGTH) return ciphertext

            val iv            = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            // Not valid Base64 — plain text from before encryption was introduced
            ciphertext
        } catch (e: Exception) {
            // Decryption failed (tampered data, wrong key, etc.)
            ""
        }
    }

    // ─── Keystore wrap key management ─────────────────────────────────────────

    /**
     * Get the AES-256-GCM wrap key from Keystore, creating it if it doesn't exist.
     *
     * This key is hardware-backed on supported devices. Its only purpose is to
     * wrap (encrypt) the PBKDF2-derived master key for safe storage in DataStore.
     *
     * Key properties:
     * - PURPOSE_ENCRYPT | PURPOSE_DECRYPT — used for wrapping and unwrapping
     * - setRandomizedEncryptionRequired(true) — OS enforces a fresh IV each time
     * - No user authentication required — app can use it without biometric/PIN
     *   (biometric is a separate UI gate, not tied to this key)
     */
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

    // ─── Salt generation ──────────────────────────────────────────────────────

    /**
     * Generate a cryptographically random 16-byte salt.
     * Called once at vault setup. Store the result in DataStore and in .vnbackup exports.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Encode salt bytes to Base64 for DataStore storage.
     */
    fun encodeSalt(salt: ByteArray): String =
        Base64.encodeToString(salt, Base64.NO_WRAP)

    /**
     * Decode salt from Base64 DataStore value back to bytes.
     */
    fun decodeSalt(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)
}