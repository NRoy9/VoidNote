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
 * DEFAULT THEME CHANGE (Sprint 4):
 * Default is now AppTheme.SYSTEM instead of AppTheme.DARK.
 * On first install (no preference stored), the app respects the OS light/dark
 * setting rather than forcing dark mode. Users who prefer dark can set it in Settings.
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private val THEME_KEY                = stringPreferencesKey("app_theme")
        private val BIOMETRIC_LOCK_KEY       = booleanPreferencesKey("biometric_lock_enabled")
        private val LAST_SEEN_VERSION_KEY    = stringPreferencesKey("last_seen_version")
        private val ONBOARDING_COMPLETE_KEY  = booleanPreferencesKey("onboarding_complete")
        private val VAULT_SETUP_COMPLETE_KEY = booleanPreferencesKey("vault_setup_complete")
        private val VAULT_SALT_KEY           = stringPreferencesKey("vault_salt")
        private val VAULT_WRAPPED_KEY        = stringPreferencesKey("vault_wrapped_key")
    }

    // ─── Theme ────────────────────────────────────────────────────────────────

    /**
     * Emits the current theme, defaulting to SYSTEM if nothing is stored yet.
     *
     * WHY SYSTEM DEFAULT?
     * Android users have explicitly chosen their OS theme (light or dark).
     * Overriding that choice on first launch is presumptuous and jarring.
     * SYSTEM respects their preference from the very first frame.
     */
    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        val name = prefs[THEME_KEY] ?: AppTheme.SYSTEM.name   // ← was AppTheme.DARK
        try {
            AppTheme.valueOf(name)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM                                    // ← was AppTheme.DARK
        }
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

    // ─── Vault / Encryption ───────────────────────────────────────────────────

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
}