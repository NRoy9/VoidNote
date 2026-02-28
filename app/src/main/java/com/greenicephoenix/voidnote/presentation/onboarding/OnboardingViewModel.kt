package com.greenicephoenix.voidnote.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OnboardingViewModel — Handles the single responsibility of the onboarding screen:
 * marking it as complete and navigating away.
 *
 * WHY IS THIS A SEPARATE VIEWMODEL FROM SPLASHVIEWMODEL?
 * Single Responsibility Principle — each ViewModel manages one screen's logic.
 * SplashViewModel decides where to go. OnboardingViewModel handles what happens
 * when onboarding ends. They are independent lifecycles.
 *
 * WHAT DOES THIS VIEWMODEL DO?
 * Exactly one thing: call PreferencesManager.setOnboardingCompleted() and then
 * trigger the navigation callback. The DataStore write is async so we use a
 * coroutine. The navigation happens after the write completes — we don't want
 * to navigate away and then have the write fail, causing onboarding to show
 * again on next launch.
 *
 * @HiltViewModel: Hilt manages this ViewModel's lifecycle and injects PreferencesManager.
 * @Inject constructor: Hilt sees this and knows how to provide PreferencesManager.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    /**
     * Mark onboarding as complete and navigate away.
     *
     * Called by OnboardingScreen when the user either:
     * - Taps "Get Started" on the last page
     * - Taps "Skip" on any page
     *
     * FLOW:
     * 1. Launch a coroutine in viewModelScope
     * 2. Write onboardingCompleted=true to DataStore (suspend call — waits for disk write)
     * 3. Call onComplete() — this is the NavGraph's navigation lambda
     *
     * WHY PASS onComplete AS A PARAMETER?
     * ViewModel should never hold a reference to NavController or Composables.
     * That would cause memory leaks (ViewModel outlives screens). Instead, the
     * screen passes a callback lambda, and the ViewModel calls it when ready.
     * The ViewModel doesn't know or care that it's triggering navigation.
     *
     * @param onComplete  Lambda to call after the DataStore write succeeds
     */
    fun completeOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            // Persist the completion flag — this survives app restarts.
            preferencesManager.setOnboardingCompleted()
            // Now it's safe to navigate — the flag is written, onboarding won't
            // show again on the next cold start.
            onComplete()
        }
    }
}