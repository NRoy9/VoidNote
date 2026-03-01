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
 * WHAT HAPPENS ON confirmUnlock():
 * 1. Read salt from DataStore (was stored at vault setup time)
 * 2. PBKDF2(entered password, salt) → derived key
 * 3. Verify by attempting to decrypt a test value (or just trust it and
 *    let the first note decrypt confirm it — we use option B for simplicity)
 * 4. Re-wrap the derived key with a new Keystore key → store in DataStore
 * 5. Activate the key for this session
 * 6. Navigate to NotesList
 *
 * WRONG PASSWORD DETECTION:
 * We can't directly verify the password without encrypting a known test value.
 * Instead: if the derived key is wrong, the first note decryption will return ""
 * (GCM auth tag mismatch). We handle this gracefully by re-asking.
 *
 * Better approach (Sprint 5): store a small encrypted verification blob at
 * setup time and try decrypting it here to verify the password before loading.
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
        _errorMessage.value = null
    }

    fun toggleShowPassword() {
        _showPassword.value = !_showPassword.value
    }

    val canUnlock: Boolean
        get() = _password.value.isNotEmpty() && !_isLoading.value

    /**
     * Attempt to unlock the vault with the entered password.
     *
     * Uses the salt stored in DataStore at original setup time.
     * PBKDF2(password, same salt) → same key → same encryption output.
     * This is the core property that makes cross-device restore work.
     *
     * @param onSuccess Called when the key is derived and the session is ready.
     */
    fun confirmUnlock(onSuccess: () -> Unit) {
        if (!canUnlock) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Load the salt that was stored at vault setup time
                val saltBase64 = preferencesManager.vaultSaltFlow.first()
                if (saltBase64.isEmpty()) {
                    _errorMessage.value = "Vault data not found. Your backup file is needed to restore."
                    return@launch
                }

                val salt = encryption.decodeSalt(saltBase64)

                // Derive the master key — same password + same salt = same key
                val masterKey = encryption.deriveKey(_password.value, salt)

                // Re-wrap and store so next launch works without password again
                val wrappedKeyBase64 = encryption.wrapAndEncode(masterKey)
                preferencesManager.setVaultWrappedKey(wrappedKeyBase64)

                // Activate for this session
                encryption.activateKey(masterKey)

                onSuccess()

            } catch (e: Exception) {
                // PBKDF2 itself doesn't fail on wrong passwords — it just produces
                // a different key. The user will know it was wrong when notes
                // appear blank. For now, we surface a generic error.
                _errorMessage.value = "Failed to unlock vault. Check your password and try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}