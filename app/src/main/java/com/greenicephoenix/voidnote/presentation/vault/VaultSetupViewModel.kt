package com.greenicephoenix.voidnote.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VaultSetupViewModel — handles first-time vault password creation.
 *
 * ─── WHAT HAPPENS ON confirmSetup() ──────────────────────────────────────────
 *
 *  1. Generate 16-byte random salt
 *  2. PBKDF2(password, salt, 100_000 iterations) → masterKey       [~300ms]
 *  3. Wrap masterKey using Keystore → wrappedKey
 *  4. Store salt + wrappedKey in DataStore
 *  5. Activate masterKey in NoteEncryptionManager (session is now ready)
 *  6. Create verification blob (encrypt "void_note_verify_v1" with masterKey)  ← NEW
 *  7. Store verification blob in DataStore                                      ← NEW
 *  8. Mark vault setup complete in DataStore
 *  9. Navigate forward (onComplete callback)
 *
 * ─── WHAT THE VERIFICATION BLOB IS FOR ───────────────────────────────────────
 *
 * When the user re-types their password at export time, we can't compare
 * passwords directly (we never stored the password — only the derived key).
 *
 * Instead: PBKDF2(re-entered password + same salt) → candidate key →
 * try to decrypt the blob → GCM auth tag passes = same key = correct password.
 *
 * This check happens BEFORE the file picker opens, so a typo is caught
 * immediately with a clear error message rather than producing a corrupt backup.
 */
@HiltViewModel
class VaultSetupViewModel @Inject constructor(
    private val encryption: NoteEncryptionManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    // ── Form state ────────────────────────────────────────────────────────────

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _showPassword = MutableStateFlow(false)
    val showPassword: StateFlow<Boolean> = _showPassword.asStateFlow()

    private val _showConfirmPassword = MutableStateFlow(false)
    val showConfirmPassword: StateFlow<Boolean> = _showConfirmPassword.asStateFlow()

    // ── Async state ───────────────────────────────────────────────────────────

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete.asStateFlow()

    // ── Input handlers ────────────────────────────────────────────────────────

    fun onPasswordChange(value: String) {
        _password.value = value
        _errorMessage.value = null
    }

    fun onConfirmPasswordChange(value: String) {
        _confirmPassword.value = value
        _errorMessage.value = null
    }

    fun toggleShowPassword() { _showPassword.value = !_showPassword.value }

    fun toggleShowConfirmPassword() { _showConfirmPassword.value = !_showConfirmPassword.value }

    // ── Validation ────────────────────────────────────────────────────────────

    val isPasswordValid: Boolean
        get() = _password.value.length >= 8

    val doPasswordsMatch: Boolean
        get() = _password.value == _confirmPassword.value

    val canConfirm: Boolean
        get() = isPasswordValid && doPasswordsMatch && !_isLoading.value

    // ── Password generator ────────────────────────────────────────────────────

    fun generatePassword() {
        val generated = encryption.generateRandomPassword()
        _password.value = generated
        _confirmPassword.value = generated
        _showPassword.value = true
        _showConfirmPassword.value = true
        _errorMessage.value = null
    }

    // ── Vault creation ────────────────────────────────────────────────────────

    /**
     * Create the vault with the entered password.
     *
     * Runs off the main thread via viewModelScope because PBKDF2 with 100,000
     * iterations takes ~200–400ms. The loading state shows a spinner to prevent
     * double-taps.
     *
     * @param onComplete Called on the main thread when setup is fully done.
     *                   NavGraph uses this to show the biometric offer dialog,
     *                   then navigate to NotesList.
     */
    fun confirmSetup(onComplete: () -> Unit) {
        if (!canConfirm) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val password = _password.value

                // Step 1: Generate a random 16-byte salt
                val salt       = encryption.generateSalt()
                val saltBase64 = encryption.encodeSalt(salt)

                // Step 2: Derive the 256-bit master key — this is the slow step
                val masterKey = encryption.deriveKey(password, salt)

                // Step 3: Wrap the master key using the Keystore wrap key
                val wrappedKeyBase64 = encryption.wrapAndEncode(masterKey)

                // Step 4: Persist salt + wrapped key
                preferencesManager.setVaultSalt(saltBase64)
                preferencesManager.setVaultWrappedKey(wrappedKeyBase64)

                // Step 5: Activate the master key for this session
                // After this point NoteRepositoryImpl can encrypt/decrypt.
                encryption.activateKey(masterKey)

                // Step 6 (NEW): Create verification blob — encrypts "void_note_verify_v1"
                // with the now-active master key. Stored in DataStore so future export
                // password confirmation and vault unlock checks can verify the password
                // without re-deriving the full key.
                val blob = encryption.createVerificationBlob()

                // Step 7 (NEW): Persist the verification blob
                preferencesManager.setVaultVerificationBlob(blob)

                // Step 8: Mark vault as set up
                preferencesManager.setVaultSetupComplete()

                _setupComplete.value = true
                onComplete()

            } catch (e: Exception) {
                _errorMessage.value = "Setup failed: ${e.message}. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}