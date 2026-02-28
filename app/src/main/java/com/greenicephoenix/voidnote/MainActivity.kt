package com.greenicephoenix.voidnote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.greenicephoenix.voidnote.data.changelog.ChangelogData
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.presentation.changelog.WhatsNewDialog
import com.greenicephoenix.voidnote.presentation.lock.LockScreen
import com.greenicephoenix.voidnote.presentation.navigation.SetupNavGraph
import com.greenicephoenix.voidnote.presentation.settings.AppTheme
import com.greenicephoenix.voidnote.presentation.theme.VoidNoteTheme
import com.greenicephoenix.voidnote.presentation.theme.rememberThemeState
import com.greenicephoenix.voidnote.security.BiometricLockManager
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainActivity — Entry point of the app.
 *
 * WHY APPCOMPATACTIVITY AND NOT COMPONENTACTIVITY?
 * BiometricPrompt requires a FragmentActivity. AppCompatActivity extends
 * FragmentActivity, so it satisfies that requirement. ComponentActivity does NOT.
 * Changing to AppCompatActivity is the correct approach for biometric support.
 *
 * BIOMETRIC GATE:
 * When biometric lock is enabled (stored in DataStore), we show [LockScreen]
 * instead of the normal app. Once the user authenticates successfully, we set
 * isUnlocked = true and the normal NavGraph replaces the lock screen.
 *
 * LOCK-ON-BACKGROUND:
 * We re-lock whenever the app goes to background (onStop). This means:
 * - Task-switcher → foreground → shows LockScreen
 * - App is killed → cold start → shows LockScreen
 * This matches the behaviour of banking apps and password managers.
 *
 * HILT:
 * @AndroidEntryPoint tells Hilt to inject dependencies into this Activity.
 * BiometricLockManager and PreferencesManager are injected via @Inject.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var biometricLockManager: BiometricLockManager

    @Inject
    lateinit var preferencesManager: PreferencesManager

    // Whether the user has successfully authenticated this session.
    // Backed by mutableStateOf so the Compose UI reacts when it changes.
    private var isUnlocked by mutableStateOf(false)

    // The last error message from a failed auth attempt (shown on LockScreen)
    private var lockErrorMessage by mutableStateOf<String?>(null)

    // Whether biometric lock is currently enabled (read from DataStore)
    private var isBiometricEnabled by mutableStateOf(false)

    // Whether to show the "What's New" dialog.
    // Set to true on first launch after a version update, false once dismissed.
    private var showWhatsNew by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read the biometric lock setting once at startup.
        // We use lifecycleScope.launch so we can call a suspend function.
        // The UI will re-render when isBiometricEnabled changes.
        lifecycleScope.launch {
            isBiometricEnabled = preferencesManager.biometricLockFlow.first()
            // If lock is not enabled, consider the app immediately unlocked.
            if (!isBiometricEnabled) isUnlocked = true
        }

        // ── What's New check ──────────────────────────────────────────────────
        // Compare the stored "last seen version" with the current release's version.
        // If they differ (or if nothing is stored = fresh install), show the dialog.
        lifecycleScope.launch {
            val lastSeen = preferencesManager.lastSeenVersionFlow.first()
            if (lastSeen != ChangelogData.latestVersion) {
                showWhatsNew = true
            }
        }

        setContent {
            val currentTheme by rememberThemeState()

            val isDarkTheme = when (currentTheme) {
                AppTheme.LIGHT      -> false
                AppTheme.DARK       -> true
                AppTheme.EXTRA_DARK -> true
                AppTheme.SYSTEM     -> isSystemInDarkTheme()
            }
            val isExtraDark = currentTheme == AppTheme.EXTRA_DARK

            // Status bar icons: white on dark, dark on light
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme

            VoidNoteTheme(darkTheme = isDarkTheme, extraDark = isExtraDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isBiometricEnabled && !isUnlocked) {
                        // ── LOCKED STATE ───────────────────────────────────
                        // Show lock screen. The fingerprint icon and "Unlock" button
                        // both call showBiometricPrompt().
                        LockScreen(
                            onUnlockClick = { showBiometricPrompt() },
                            errorMessage = lockErrorMessage
                        )
                    } else {
                        // ── UNLOCKED STATE ─────────────────────────────────
                        // Normal app navigation. The lock screen is gone.
                        val navController = rememberNavController()
                        SetupNavGraph(navController = navController)

                        // ── What's New dialog ───────────────────────────────
                        // Shown on top of NavGraph — doesn't interrupt navigation.
                        // Only appears when showWhatsNew = true (set above in onCreate).
                        // Dismissed by user tapping "Got it" → we mark the version as
                        // seen so it won't appear again until the next release.
                        if (showWhatsNew) {
                            WhatsNewDialog(
                                onDismiss = {
                                    showWhatsNew = false
                                    lifecycleScope.launch {
                                        preferencesManager.markVersionSeen(ChangelogData.latestVersion)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Show the system biometric prompt.
     *
     * Callbacks update the Compose state which re-renders the UI:
     * - Success → isUnlocked = true → NavGraph replaces LockScreen
     * - Error → lockErrorMessage → shown on LockScreen
     */
    private fun showBiometricPrompt() {
        lockErrorMessage = null  // Clear previous error
        biometricLockManager.showPrompt(
            activity = this,
            onSuccess = {
                isUnlocked = true
                lockErrorMessage = null
            },
            onFailed = {
                // Not a terminal error — user can try again. Don't show a message
                // because the system already provides feedback (vibration, icon shake).
            },
            onError = { errorMessage ->
                // Terminal — too many attempts, hardware unavailable, user cancelled.
                // Show the message on the lock screen.
                lockErrorMessage = errorMessage
            }
        )
    }

    /**
     * Re-lock the app when it goes to background.
     *
     * onStop() is called when the Activity is no longer visible — task switcher,
     * another app, power button. We set isUnlocked = false so that when the
     * user returns, they see the LockScreen.
     *
     * WHY onStop AND NOT onPause?
     * onPause fires for things like notification drawers, permission dialogs, etc.
     * We don't want to lock for those. onStop fires when the app is actually hidden.
     */
    override fun onStop() {
        super.onStop()
        if (isBiometricEnabled) {
            isUnlocked = false
            lockErrorMessage = null
        }
    }
}