package com.greenicephoenix.voidnote.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenicephoenix.voidnote.presentation.settings.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "void_note_preferences")

/**
 * PreferencesManager — DataStore wrapper for all user preferences and app state.
 *
 * ─── NEW: VAULT_VERIFICATION_BLOB ────────────────────────────────────────────
 *
 * WHAT IS IT?
 * At vault creation, we encrypt the known string "void_note_verify_v1" with
 * the master key → store the Base64 ciphertext in this key.
 *
 * WHY?
 * PBKDF2 never fails — it always produces a key, even from a wrong password
 * (it just produces a *different* key). Without the blob, we can't know if
 * the password is correct until notes silently show blank. Bad UX.
 *
 * HOW USED?
 * Export: user re-types password → we derive candidate key from (password +
 * stored salt) → try to decrypt blob → GCM auth tag passes = correct password,
 * fails = wrong password. Check happens before the file picker opens.
 *
 * Vault unlock (reinstall): same check, prevents silent decrypt failures.
 *
 * SECURITY:
 * The plaintext "void_note_verify_v1" is not secret. AES-256-GCM makes it
 * safe: without the exact key, no one can forge a ciphertext that passes
 * the authentication tag check, so the blob reveals nothing.
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private val THEME_KEY                   = stringPreferencesKey("app_theme")
        private val BIOMETRIC_LOCK_KEY          = booleanPreferencesKey("biometric_lock_enabled")
        private val LAST_SEEN_VERSION_KEY       = stringPreferencesKey("last_seen_version")
        private val ONBOARDING_COMPLETE_KEY     = booleanPreferencesKey("onboarding_complete")
        private val VAULT_SETUP_COMPLETE_KEY    = booleanPreferencesKey("vault_setup_complete")
        private val VAULT_SALT_KEY              = stringPreferencesKey("vault_salt")
        private val VAULT_WRAPPED_KEY           = stringPreferencesKey("vault_wrapped_key")
        private val VAULT_VERIFICATION_BLOB_KEY = stringPreferencesKey("vault_verification_blob") // NEW
    }

    // ─── Theme ────────────────────────────────────────────────────────────────

    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        val name = prefs[THEME_KEY] ?: AppTheme.SYSTEM.name
        try { AppTheme.valueOf(name) } catch (e: IllegalArgumentException) { AppTheme.SYSTEM }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[THEME_KEY] = theme.name }
    }

    // ─── Biometric lock ───────────────────────────────────────────────────────

    val biometricLockFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BIOMETRIC_LOCK_KEY] ?: false
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_LOCK_KEY] = enabled }
    }

    // ─── What's New ───────────────────────────────────────────────────────────

    val lastSeenVersionFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_SEEN_VERSION_KEY] ?: ""
    }

    suspend fun markVersionSeen(version: String) {
        context.dataStore.edit { it[LAST_SEEN_VERSION_KEY] = version }
    }

    // ─── Onboarding ───────────────────────────────────────────────────────────

    val onboardingCompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETE_KEY] ?: false
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[ONBOARDING_COMPLETE_KEY] = true }
    }

    // ─── Vault ────────────────────────────────────────────────────────────────

    val vaultSetupCompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[VAULT_SETUP_COMPLETE_KEY] ?: false
    }

    suspend fun setVaultSetupComplete() {
        context.dataStore.edit { it[VAULT_SETUP_COMPLETE_KEY] = true }
    }

    val vaultSaltFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VAULT_SALT_KEY] ?: ""
    }

    suspend fun setVaultSalt(saltBase64: String) {
        context.dataStore.edit { it[VAULT_SALT_KEY] = saltBase64 }
    }

    val vaultWrappedKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VAULT_WRAPPED_KEY] ?: ""
    }

    suspend fun setVaultWrappedKey(wrappedKeyBase64: String) {
        context.dataStore.edit { it[VAULT_WRAPPED_KEY] = wrappedKeyBase64 }
    }

    // ─── Vault verification blob (NEW) ────────────────────────────────────────

    /** Empty string = vault created before this feature (old install). */
    val vaultVerificationBlobFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VAULT_VERIFICATION_BLOB_KEY] ?: ""
    }

    suspend fun setVaultVerificationBlob(blobBase64: String) {
        context.dataStore.edit { it[VAULT_VERIFICATION_BLOB_KEY] = blobBase64 }
    }
}