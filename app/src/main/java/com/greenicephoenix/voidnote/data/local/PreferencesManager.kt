package com.greenicephoenix.voidnote.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenicephoenix.voidnote.presentation.settings.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences Manager using DataStore
 *
 * Stores user preferences like theme, settings, etc.
 * DataStore is better than SharedPreferences (type-safe, async, Flow-based)
 */

// Extension property to get DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "void_note_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    companion object {
        // Preference keys
        private val THEME_KEY = stringPreferencesKey("app_theme")
        private val BIOMETRIC_LOCK_KEY = booleanPreferencesKey("biometric_lock_enabled")
        // Tracks the last version whose "What's New" dialog the user has seen.
        // When this differs from the current versionName, we show the dialog.
        private val LAST_SEEN_VERSION_KEY = stringPreferencesKey("last_seen_version")
    }

    /**
     * Get current theme as Flow
     */
    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[THEME_KEY] ?: AppTheme.DARK.name
        try {
            AppTheme.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            AppTheme.DARK // Default fallback
        }
    }

    /**
     * Save theme preference
     */
    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BIOMETRIC LOCK PREFERENCE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Flow that emits whether biometric lock is enabled.
     *
     * Default is false — biometric lock is opt-in.
     * The user enables it from Settings → Security → Biometric Lock.
     *
     * WHY FLOW AND NOT A SUSPEND FUNCTION?
     * The settings screen observes this reactively — if the user toggles it,
     * the toggle switches immediately. With a one-shot read we'd need manual
     * refresh logic. Flow makes this automatic.
     */
    val biometricLockFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIOMETRIC_LOCK_KEY] ?: false
    }

    /**
     * Enable or disable the biometric lock.
     *
     * Called when the user toggles the switch in Settings.
     * If enabling — MainActivity will show the LockScreen on next launch/resume.
     * If disabling — app opens directly with no authentication.
     *
     * @param enabled  true = lock enabled, false = lock disabled
     */
    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_LOCK_KEY] = enabled
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WHAT'S NEW — LAST SEEN VERSION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The last version string whose "What's New" dialog was shown to the user.
     *
     * Empty string = user has never seen any changelog (fresh install or first
     * update that includes this feature).
     *
     * Logic in MainActivity:
     *   currentVersion != lastSeenVersion → show dialog → call markVersionSeen()
     */
    val lastSeenVersionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_SEEN_VERSION_KEY] ?: ""
    }

    /**
     * Record that the user has now seen the What's New dialog for [version].
     * Called after the dialog is shown — subsequent launches won't show it again
     * until the version changes.
     */
    suspend fun markVersionSeen(version: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SEEN_VERSION_KEY] = version
        }
    }
}