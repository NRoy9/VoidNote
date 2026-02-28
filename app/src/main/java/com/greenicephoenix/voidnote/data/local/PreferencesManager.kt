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
 * PreferencesManager — Centralised persistent preferences using DataStore.
 *
 * WHY DATASTORE OVER SHAREDPREFERENCES?
 * SharedPreferences is synchronous — reading it on the main thread can cause
 * ANRs (Application Not Responding). DataStore is async and Flow-based:
 * - No main-thread blocking
 * - Type-safe keys
 * - Coroutine-native: suspend functions and Flows
 * - Atomic writes — no partial state corruption
 *
 * SPRINT 3 CHANGE: Added onboardingCompleted preference.
 * - Default: false (never show onboarding again after first launch)
 * - Once the user completes or skips onboarding, we set this to true
 * - SplashViewModel reads this to decide: Onboarding or NotesList
 */

// This extension property creates a single DataStore instance tied to the
// app's Context. 'by preferencesDataStore' is a Kotlin delegate — it lazily
// creates the store the first time it's accessed and reuses it afterwards.
// The name "void_note_preferences" is the filename stored on disk.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "void_note_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    // ─────────────────────────────────────────────────────────────────────────
    // PREFERENCE KEYS
    // ─────────────────────────────────────────────────────────────────────────
    // Each key is a typed handle into the DataStore map.
    // Think of these like the "column names" in a settings table.
    companion object {
        private val THEME_KEY = stringPreferencesKey("app_theme")
        private val BIOMETRIC_LOCK_KEY = booleanPreferencesKey("biometric_lock_enabled")
        private val LAST_SEEN_VERSION_KEY = stringPreferencesKey("last_seen_version")

        /**
         * SPRINT 3 — Onboarding completion flag.
         *
         * false = user has never completed onboarding (fresh install, or first
         *         launch after Sprint 3 update). Show the onboarding flow.
         * true  = user already completed or skipped onboarding. Go straight to
         *         the notes list.
         */
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // THEME PREFERENCE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reactive Flow of the current app theme.
     *
     * HOW FLOWS WORK:
     * A Flow is like a pipe that emits values over time. Every time the theme
     * is changed (via setTheme), the DataStore emits a new snapshot of all
     * preferences. The .map{} transform picks out just the theme value.
     * Any composable collecting this Flow will recompose automatically.
     *
     * Default: DARK (Nothing aesthetic — dark first design)
     */
    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[THEME_KEY] ?: AppTheme.DARK.name
        try {
            AppTheme.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            AppTheme.DARK // Safe default if the stored value is corrupt/unknown
        }
    }

    /**
     * Persist the user's chosen theme.
     *
     * WHY SUSPEND?
     * DataStore writes are always async. 'suspend' means this function must be
     * called from a coroutine or another suspend function — it cannot block
     * the main thread.
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
     * Default is false — biometric lock is opt-in from Settings → Security.
     * MainActivity reads this once at startup to determine whether to show
     * the LockScreen before the NavGraph.
     */
    val biometricLockFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIOMETRIC_LOCK_KEY] ?: false
    }

    /**
     * Enable or disable the biometric lock.
     * Called from SettingsViewModel when the user toggles the switch.
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
     * The version string of the last changelog the user acknowledged.
     *
     * "" (empty string) = user has never seen any changelog.
     * MainActivity compares this to ChangelogData.latestVersion.
     * If they differ → show WhatsNewDialog.
     */
    val lastSeenVersionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_SEEN_VERSION_KEY] ?: ""
    }

    /**
     * Record that the user has now seen the What's New dialog for [version].
     * Called when the dialog is dismissed — won't appear again until the
     * versionName changes.
     */
    suspend fun markVersionSeen(version: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SEEN_VERSION_KEY] = version
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ONBOARDING — SPRINT 3
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Flow that emits whether the user has completed the onboarding flow.
     *
     * HOW IT'S USED:
     * SplashViewModel collects this. On first launch (false), the splash
     * navigates to OnboardingScreen. On all subsequent launches (true),
     * it goes straight to NotesListScreen.
     *
     * WHY TRACK THIS IN DATASTORE INSTEAD OF A VERSION CHECK?
     * We want to show onboarding exactly once — ever. Not once per version.
     * A boolean "completed" flag is the simplest and most explicit way to
     * express that intent. If we ever redesign onboarding and want to re-show
     * it, we'd add a version check on top of this boolean.
     */
    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    /**
     * Mark onboarding as completed.
     *
     * Called by OnboardingScreen when the user either:
     * (a) reaches the last page and taps "Get Started", or
     * (b) taps the "Skip" button on any page.
     *
     * After this is called, SplashViewModel will always navigate to NotesList.
     */
    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = true
        }
    }
}