package com.greenicephoenix.voidnote.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VaultUnlockViewModel — re-derives the master key from the vault password.
 *
 * WHEN IS THIS SCREEN SHOWN?
 * Only when SplashViewModel routes here — which happens when:
 * - App was reinstalled (Keystore key wiped on uninstall)
 * - Factory reset (everything wiped)
 * - Hardware security module changed (rare)
 *
 * This is NOT the daily unlock screen. Normal daily use never shows this.
 * The Keystore wrapping means the key is loaded silently on every normal launch.
 *
 * ─── SPRINT 5 FIX ─────────────────────────────────────────────────────────────
 *
 * PROBLEM:
 * PBKDF2 is a key-derivation function — it never "fails". It always returns a
 * key, even from a wrong password. It just returns a *different* key.
 * Before this fix, if the user typed the wrong password here, confirmUnlock()
 * would succeed, activate the wrong key, and all notes would appear blank
 * (GCM decryption would silently return "" for each note). Very confusing.
 *
 * FIX:
 * At vault setup time, VaultSetupViewModel encrypts the known string
 * "void_note_verify_v1" and stores the ciphertext in DataStore as
 * vaultVerificationBlob. We call this the "verification blob".
 *
 * Here, BEFORE activating the derived key, we call:
 *   encryption.verifyPasswordAgainstBlob(password, salt, blob)
 *
 * That function:
 *   1. Re-derives a candidate key from (password + stored salt)
 *   2. Tries to decrypt the blob with that candidate key
 *   3. AES-GCM authentication tag: passes only if the key is exactly right
 *   4. Returns true (correct password) or false (wrong password)
 *
 * This is completely safe: "void_note_verify_v1" is not a secret, and
 * AES-256-GCM's authentication tag makes it impossible to forge a ciphertext
 * that passes verification without the exact key.
 *
 * BACKWARD COMPATIBILITY:
 * Users who installed before the blob was introduced have an empty blobBase64.
 * verifyPasswordAgainstBlob() returns true for any non-empty password in that
 * case — they are not blocked. They will get a blob written the next time
 * they change their vault password via ChangeVaultPasswordScreen.
 */
@HiltViewModel
class VaultUnlockViewModel @Inject constructor(
    private val encryption: NoteEncryptionManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _showPassword = MutableStateFlow(false)
    val showPassword: StateFlow<Boolean> = _showPassword.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onPasswordChange(value: String) {
        _password.value = value
        _errorMessage.value = null   // clear any previous error as user types
    }

    fun toggleShowPassword() {
        _showPassword.value = !_showPassword.value
    }

    // canUnlock is a computed property — no password field = button stays disabled
    val canUnlock: Boolean
        get() = _password.value.isNotEmpty() && !_isLoading.value

    /**
     * Attempt to unlock the vault with the entered password.
     *
     * STEP-BY-STEP:
     * 1. Read the salt stored at original vault setup time
     * 2. Read the verification blob stored at vault setup time
     * 3. *** NEW: Verify password against the blob BEFORE deriving the real key ***
     *    If wrong → show error, return early. No key loaded.
     * 4. If correct → derive master key with PBKDF2(password, salt)
     * 5. Re-wrap the key with a new Android Keystore key → store in DataStore
     *    (This is "re-wrapping" — from now on the next launch won't need a password)
     * 6. Activate the key for this session in NoteEncryptionManager
     * 7. Call onSuccess() → NavGraph navigates to NotesList
     *
     * @param onSuccess Called when key is ready and session is fully set up.
     */
    fun confirmUnlock(onSuccess: () -> Unit) {
        if (!canUnlock) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // ── Step 1: Read stored salt ───────────────────────────────────
                // Salt was generated randomly at first launch and stored in DataStore.
                // The SAME salt must be used for PBKDF2 to re-derive the same key.
                val saltBase64 = preferencesManager.vaultSaltFlow.first()
                if (saltBase64.isEmpty()) {
                    _errorMessage.value = "Vault data not found. Your backup file is needed to restore."
                    return@launch
                }

                // ── Step 2: Read verification blob ────────────────────────────
                // May be empty for vaults created before Sprint 5 (old installs).
                // verifyPasswordAgainstBlob() handles the empty case gracefully.
                val blobBase64 = preferencesManager.vaultVerificationBlobFlow.first()

                // ── Step 3: VERIFY password before deriving the real key ──────
                //
                // WHY THIS ORDER MATTERS:
                // We verify BEFORE calling deriveKey() for the "real" activation.
                // verifyPasswordAgainstBlob() is a pure function — it derives an
                // internal candidate key just for verification without touching
                // the session key at all. If the password is wrong, we return
                // early without ever activating anything. The user gets a clear
                // error message and can try again.
                val isPasswordCorrect = encryption.verifyPasswordAgainstBlob(
                    password   = _password.value,
                    saltBase64 = saltBase64,
                    blobBase64 = blobBase64
                )

                if (!isPasswordCorrect) {
                    // Wrong password — give the user a clear message
                    _errorMessage.value = "Incorrect password. Please try again."
                    return@launch
                }

                // ── Step 4: Password verified — now derive the real master key ─
                val salt      = encryption.decodeSalt(saltBase64)
                val masterKey = encryption.deriveKey(_password.value, salt)

                // ── Step 5: Re-wrap with Keystore ─────────────────────────────
                // Stores the Keystore-encrypted key in DataStore.
                // On the NEXT app launch, SplashViewModel will find this and
                // unwrap it silently — no password needed again.
                val wrappedKeyBase64 = encryption.wrapAndEncode(masterKey)
                preferencesManager.setVaultWrappedKey(wrappedKeyBase64)

                // ── Step 6: Activate for this session ─────────────────────────
                // All encrypt/decrypt calls in NoteEncryptionManager will now
                // use this key until the app is killed.
                encryption.activateKey(masterKey)

                // ── Step 7: Navigate to the main screen ───────────────────────
                onSuccess()

            } catch (e: Exception) {
                // This catch handles truly unexpected errors (e.g. DataStore I/O failure,
                // Keystore hardware error). Wrong passwords are caught in Step 3 above
                // and never reach here.
                _errorMessage.value = "Failed to unlock vault. Check your password and try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}