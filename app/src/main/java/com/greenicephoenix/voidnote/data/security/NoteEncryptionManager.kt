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
 * ─── NEW IN THIS SPRINT ───────────────────────────────────────────────────────
 *
 * TWO NEW METHODS:
 *
 * 1. createVerificationBlob()
 *    Called once at vault setup. Encrypts the known string VERIFICATION_VALUE
 *    with the current session key. The resulting Base64 ciphertext is stored
 *    in DataStore via PreferencesManager.setVaultVerificationBlob().
 *
 * 2. verifyPasswordAgainstBlob(password, saltBase64, blobBase64)
 *    Called at export (and future: vault unlock). Derives a candidate key from
 *    (entered password + stored salt) without touching the session key, then
 *    tries to decrypt the blob. If the GCM authentication tag passes, the
 *    password is correct. This is a PURE FUNCTION — it does not modify any
 *    state.
 *
 * ─── WHY NOT SWAP THE SESSION KEY? ───────────────────────────────────────────
 *
 * An earlier design swapped sessionKey temporarily to use the existing decrypt()
 * method. That approach has a race condition: if another coroutine calls
 * encrypt() or decrypt() while the session key is swapped, it would use the
 * wrong key. By making verifyPasswordAgainstBlob() completely standalone (it
 * calls the JCE Cipher directly without touching sessionKey), it is thread-safe.
 *
 * ─── FALLBACK FOR OLD INSTALLS ───────────────────────────────────────────────
 *
 * If blobBase64 is empty (vault set up before this feature was added),
 * verifyPasswordAgainstBlob() returns true for any non-empty password.
 * This lets existing users export without being blocked. They will get the blob
 * stored the next time they change their vault password (future feature).
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

        // The known plaintext encrypted to create the verification blob.
        // Not a secret — AES-GCM's authentication tag is what makes this safe.
        const val VERIFICATION_VALUE = "void_note_verify_v1"
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

    // ─── Verification blob (NEW) ──────────────────────────────────────────────

    /**
     * Encrypt VERIFICATION_VALUE with the current session key.
     * Store the result in DataStore via PreferencesManager.setVaultVerificationBlob().
     *
     * Call this ONCE at vault setup, after activateKey().
     *
     * @throws IllegalStateException if called before the session key is loaded
     */
    fun createVerificationBlob(): String = encrypt(VERIFICATION_VALUE)

    /**
     * Check whether [password] matches the vault password used at setup.
     *
     * Algorithm:
     *   1. Decode salt from [saltBase64]
     *   2. PBKDF2(password, salt, 100_000 iterations) → candidateKey
     *   3. Decode [blobBase64] → IV + ciphertext
     *   4. AES-256-GCM decrypt(ciphertext, IV, candidateKey)
     *   5. If decryption succeeds AND plaintext == VERIFICATION_VALUE → true
     *      If GCM auth tag fails (wrong key) → catch → false
     *
     * This is a PURE function — it does NOT modify sessionKey or any other state.
     * Safe to call from any coroutine without locking.
     *
     * FALLBACK: if [blobBase64] is empty (old install, no blob stored yet),
     * returns true for any non-empty password to avoid blocking the user.
     * They will be warned to back up their password in the export UI.
     *
     * @param password    The vault password the user just typed
     * @param saltBase64  The base64 salt from DataStore (stored at vault setup)
     * @param blobBase64  The base64 verification blob from DataStore
     * @return            true if password is correct, false otherwise
     */
    fun verifyPasswordAgainstBlob(
        password: String,
        saltBase64: String,
        blobBase64: String
    ): Boolean {
        // Fallback for users who set up the vault before this sprint
        if (blobBase64.isEmpty()) {
            return password.isNotEmpty()
        }

        return try {
            // Step 1: Re-derive the candidate key from the entered password
            val salt         = decodeSalt(saltBase64)
            val candidateKey = deriveKey(password, salt)

            // Step 2: Decode the stored blob → IV + ciphertext
            val combined     = Base64.decode(blobBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return false

            val iv         = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            // Step 3: Decrypt with the candidate key (NOT sessionKey — pure function)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, candidateKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val plaintext = String(cipher.doFinal(ciphertext), Charsets.UTF_8)

            // Step 4: Verify the plaintext matches exactly
            plaintext == VERIFICATION_VALUE

        } catch (e: Exception) {
            // javax.crypto.AEADBadTagException — GCM authentication failed = wrong key
            // Any other exception also means verification failed
            false
        }
    }

    // ─── Text encryption (note title / content / tags) ────────────────────────

    /**
     * Encrypt a plaintext String → Base64(IV + ciphertext).
     */
    fun encrypt(plaintext: String): String {
        val key    = requireKey()
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
     * Returns "" on wrong key; returns [ciphertext] unchanged if it is not
     * valid Base64 (migration safety for plain-text notes from before encryption).
     */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""

        val key = requireKey()

        return try {
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ciphertext

            val iv             = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            ciphertext  // not valid Base64 → plain text from before encryption
        } catch (e: Exception) {
            ""          // GCM auth tag mismatch (wrong key or tampered data)
        }
    }

    // ─── Binary file encryption (images, audio) ───────────────────────────────

    /** Encrypt raw file bytes → IV[12] + ciphertext. Write directly to .enc file. */
    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        val key    = requireKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv         = cipher.iv
        val ciphertext = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return combined
    }

    /** Decrypt a .enc file's bytes (IV[12] + ciphertext) → original bytes. */
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