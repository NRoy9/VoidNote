package com.greenicephoenix.voidnote.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenicephoenix.voidnote.domain.model.NoteSort
import com.greenicephoenix.voidnote.presentation.settings.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "void_note_preferences")

/**
 * PreferencesManager — DataStore wrapper for all user preferences and app state.
 *
 * SPRINT 6 ADDITIONS:
 *
 * ─── NOTE_SORT ────────────────────────────────────────────────────────────────
 * Persists the user's chosen sort order for the notes list.
 * Default is UPDATED_DESC (most recently modified first).
 * The sort is applied in-memory in NotesListViewModel after the DB read.
 *
 * ─── DISMISSED_UPDATE_VERSION ────────────────────────────────────────────────
 * When the GitHub update checker finds a new version and the user taps "Dismiss",
 * we store that version string here. The banner will not re-appear for that
 * same version — but WILL re-appear if an even newer version is detected later.
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
        private val VAULT_VERIFICATION_BLOB_KEY = stringPreferencesKey("vault_verification_blob")

        // ── Sprint 6 ──────────────────────────────────────────────────────
        private val NOTE_SORT_KEY               = stringPreferencesKey("note_sort")
        private val DISMISSED_UPDATE_VERSION_KEY = stringPreferencesKey("dismissed_update_version")
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

    // ─── Vault verification blob ───────────────────────────────────────────────

    /** Empty string = vault created before this feature (old install). */
    val vaultVerificationBlobFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VAULT_VERIFICATION_BLOB_KEY] ?: ""
    }

    suspend fun setVaultVerificationBlob(blobBase64: String) {
        context.dataStore.edit { it[VAULT_VERIFICATION_BLOB_KEY] = blobBase64 }
    }

    // ─── Note sort (Sprint 6) ─────────────────────────────────────────────────

    /**
     * The user's chosen sort order for the notes list.
     * Emits NoteSort.UPDATED_DESC if no preference has been saved yet.
     */
    val noteSortFlow: Flow<NoteSort> = context.dataStore.data.map { prefs ->
        NoteSort.fromString(prefs[NOTE_SORT_KEY])
    }

    /**
     * Persist the chosen sort order.
     * Called by NotesListViewModel when the user picks a sort from the menu.
     */
    suspend fun setNoteSort(sort: NoteSort) {
        context.dataStore.edit { it[NOTE_SORT_KEY] = sort.name }
    }

    // ─── Dismissed update version (Sprint 6) ─────────────────────────────────

    /**
     * The version string the user last dismissed from the update banner.
     * Empty string = never dismissed.
     *
     * If UpdateCheckerManager detects version "v0.2.0-alpha" and
     * dismissedUpdateVersion is also "v0.2.0-alpha", the banner stays hidden.
     * If a newer "v0.3.0-alpha" is detected, the banner shows again.
     */
    val dismissedUpdateVersionFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DISMISSED_UPDATE_VERSION_KEY] ?: ""
    }

    suspend fun setDismissedUpdateVersion(version: String) {
        context.dataStore.edit { it[DISMISSED_UPDATE_VERSION_KEY] = version }
    }
}