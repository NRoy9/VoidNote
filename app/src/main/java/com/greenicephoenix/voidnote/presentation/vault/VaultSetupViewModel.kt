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
 * Called once in the app's lifetime (or after a reinstall when the user
 * needs to re-establish their vault on the new install).
 *
 * WHAT HAPPENS ON confirmSetup():
 * 1. Generate 16-byte random salt
 * 2. PBKDF2(password, salt, 100_000 iterations) → masterKey
 * 3. Wrap masterKey using Keystore → wrappedKey
 * 4. Store salt + wrappedKey in DataStore
 * 5. Activate masterKey in NoteEncryptionManager (session is now ready)
 * 6. Mark vault setup complete in DataStore
 * 7. Navigate to NotesList
 *
 * After step 5, NoteRepositoryImpl can encrypt and decrypt notes immediately.
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

    // Controls whether password characters are visible or hidden
    private val _showPassword = MutableStateFlow(false)
    val showPassword: StateFlow<Boolean> = _showPassword.asStateFlow()

    private val _showConfirmPassword = MutableStateFlow(false)
    val showConfirmPassword: StateFlow<Boolean> = _showConfirmPassword.asStateFlow()

    // ── Async state ───────────────────────────────────────────────────────────

    // True while PBKDF2 is running (can take ~300ms — show a spinner)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Non-null = setup failed, show this error to the user
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // True once setup is complete — screen navigates away
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

    fun toggleShowPassword() {
        _showPassword.value = !_showPassword.value
    }

    fun toggleShowConfirmPassword() {
        _showConfirmPassword.value = !_showConfirmPassword.value
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Password requirements:
     * - At least 8 characters
     * The generate button produces passwords that meet this automatically.
     * We don't enforce complexity on manual entry — trust the user.
     */
    val isPasswordValid: Boolean
        get() = _password.value.length >= 8

    val doPasswordsMatch: Boolean
        get() = _password.value == _confirmPassword.value

    /**
     * True when the "Create Vault" button should be enabled.
     */
    val canConfirm: Boolean
        get() = isPasswordValid && doPasswordsMatch && !_isLoading.value

    // ── Password generator ────────────────────────────────────────────────────

    /**
     * Fill both password fields with a generated 8-char password.
     *
     * Generates via NoteEncryptionManager.generateRandomPassword() which
     * guarantees: lowercase + uppercase + digit + special character,
     * shuffled, visually unambiguous characters only.
     *
     * Both fields are set to the same value so the confirm check passes
     * automatically — the user doesn't need to type it twice.
     */
    fun generatePassword() {
        val generated = encryption.generateRandomPassword()
        _password.value = generated
        _confirmPassword.value = generated
        // Show the generated password so the user can see and save it
        _showPassword.value = true
        _showConfirmPassword.value = true
        _errorMessage.value = null
    }

    // ── Vault creation ────────────────────────────────────────────────────────

    /**
     * Create the vault with the entered password.
     *
     * Runs on viewModelScope because PBKDF2 with 100,000 iterations takes
     * ~200-400ms on a modern Android device — long enough to block the main
     * thread and cause a jank/ANR. We move it off the main thread via the
     * coroutine dispatcher (Hilt provides the correct scope).
     *
     * The loading spinner prevents the user from tapping twice.
     *
     * @param onComplete Called on the main thread when setup is done.
     *                   NavGraph uses this to navigate to NotesList.
     */
    fun confirmSetup(onComplete: () -> Unit) {
        if (!canConfirm) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val password = _password.value

                // Step 1: Generate a random 16-byte salt
                val salt = encryption.generateSalt()
                val saltBase64 = encryption.encodeSalt(salt)

                // Step 2: Derive the 256-bit master key from password + salt
                // This is the slow step — PBKDF2 with 100,000 iterations
                val masterKey = encryption.deriveKey(password, salt)

                // Step 3: Wrap (encrypt) the master key using Keystore
                // The Keystore wrap key is generated here if it doesn't exist yet
                val wrappedKeyBase64 = encryption.wrapAndEncode(masterKey)

                // Step 4: Persist salt and wrapped key to DataStore
                preferencesManager.setVaultSalt(saltBase64)
                preferencesManager.setVaultWrappedKey(wrappedKeyBase64)

                // Step 5: Activate the master key for this session
                // From this point, NoteRepositoryImpl can encrypt/decrypt
                encryption.activateKey(masterKey)

                // Step 6: Mark vault as set up (won't show setup screen again)
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