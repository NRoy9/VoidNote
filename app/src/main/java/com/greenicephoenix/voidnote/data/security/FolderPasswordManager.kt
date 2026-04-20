package com.greenicephoenix.voidnote.data.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.security.SecureRandom
import android.util.Base64

/**
 * FolderPasswordManager — handles hashing and verification of per-folder passwords.
 *
 * WHY PBKDF2?
 * Same algorithm used for the vault password — consistent security model.
 * PBKDF2WithHmacSHA256 with 100,000 iterations makes brute-force attacks
 * computationally expensive, even if the database file is extracted.
 *
 * WHY A SEPARATE SALT PER FOLDER?
 * Each folder gets its own random 16-byte salt. This means two folders with
 * the same password produce completely different hashes — an attacker cannot
 * use a single rainbow table to crack all folder passwords at once.
 *
 * STORAGE:
 * - salt is stored as Base64 in folders.passwordSalt
 * - hash is stored as Base64 in folders.passwordHash
 * - Neither the plaintext password nor the key ever leaves this class.
 */
@Singleton
class FolderPasswordManager @Inject constructor() {

    companion object {
        private const val ALGORITHM    = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS   = 100_000
        private const val KEY_LENGTH   = 256       // bits
        private const val SALT_BYTES   = 16
    }

    /**
     * Generate a fresh random salt.
     * Call this when setting a password for the first time.
     * Store the returned Base64 string in FolderEntity.passwordSalt.
     */
    fun generateSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    /**
     * Hash a plaintext password with a given salt.
     *
     * @param password  The user's plaintext password.
     * @param saltB64   Base64-encoded salt (from generateSalt() or from DB).
     * @return          Base64-encoded hash — store this in FolderEntity.passwordHash.
     */
    fun hashPassword(password: String, saltB64: String): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword() // wipe the password from memory immediately
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Verify a plaintext password against a stored hash + salt.
     *
     * @param password      The password the user just typed.
     * @param storedHashB64 The hash stored in FolderEntity.passwordHash.
     * @param storedSaltB64 The salt stored in FolderEntity.passwordSalt.
     * @return              true if the password is correct.
     */
    fun verifyPassword(
        password: String,
        storedHashB64: String,
        storedSaltB64: String
    ): Boolean {
        val attemptHash = hashPassword(password, storedSaltB64)
        return attemptHash == storedHashB64
    }
}