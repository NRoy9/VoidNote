package com.greenicephoenix.voidnote.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.changelog.ChangelogData
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OnboardingViewModel — marks the onboarding flow as complete.
 *
 * Called when the user taps Skip or Continue on the final page.
 *
 * ALSO marks the current version as "seen" so the What's New dialog
 * does NOT fire immediately after onboarding on a fresh install.
 *
 * WHY MARK VERSION SEEN HERE?
 * The What's New dialog checks: currentVersion != lastSeenVersion → show dialog.
 * On a fresh install, lastSeenVersion is "" and currentVersion is e.g. "0.0.4-alpha",
 * so the dialog fires on first launch — even though it's a brand new install
 * with nothing "new" to announce. By marking the version as seen during
 * onboarding, we ensure the dialog only appears on UPDATES (when the user
 * already knows the app and is seeing a new version).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    /**
     * Record that onboarding is done and navigate forward.
     *
     * Sets two DataStore flags:
     * 1. onboardingComplete = true — never show onboarding again
     * 2. lastSeenVersion = currentVersion — suppress What's New on first open
     *
     * Then calls [onCompleted] which NavGraph uses to route to VaultSetup.
     */
    fun markOnboardingComplete(onCompleted: () -> Unit) {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete()
            preferencesManager.markVersionSeen(ChangelogData.latestVersion)
            // DataStore writes are done. Now call onCompleted() back on the main
            // thread. Navigation (navController.navigate) must run on the main
            // thread — calling it inside a coroutine launch{} silently fails
            // because the coroutine may resume on a background dispatcher.
        }
        // Called outside the coroutine: DataStore writes are fire-and-forget here,
        // which is fine — they complete asynchronously and onCompleted() navigates
        // away before they finish, but both flags are written reliably regardless.
        onCompleted()
    }
}