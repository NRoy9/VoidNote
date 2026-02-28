package com.greenicephoenix.voidnote.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SplashViewModel — Decides where to navigate after the splash animation.
 *
 * WHAT IS A VIEWMODEL?
 * A ViewModel is a special class that holds UI state and survives screen
 * rotations. When the user rotates the phone, the Composable is destroyed and
 * recreated — but the ViewModel lives on. This prevents us from re-fetching
 * data or re-running logic on every rotation.
 *
 * WHY DOES SPLASH NEED A VIEWMODEL?
 * The splash screen needs to make a decision: "Has the user completed
 * onboarding?" This requires reading from DataStore, which is async (suspend).
 * We can't call suspend functions directly in a Composable — that would break
 * the rules of Compose. A ViewModel provides a coroutine scope (viewModelScope)
 * that is safe to use for this purpose.
 *
 * NAVIGATION DECISION LOGIC:
 * ┌──────────────────────────────────────────────────────────┐
 * │ onboardingCompleted = false → navigate to ONBOARDING     │
 * │ onboardingCompleted = true  → navigate to NOTES_LIST     │
 * └──────────────────────────────────────────────────────────┘
 *
 * @HiltViewModel tells Hilt to manage this ViewModel's lifecycle and inject
 * its dependencies (@Inject constructor). The ViewModel is tied to the
 * SplashScreen composable's lifecycle.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    /**
     * Navigation destination that SplashScreen should go to after animation.
     *
     * WHY SEALED CLASS INSTEAD OF A BOOLEAN?
     * Using a sealed class makes the intent explicit and scalable. If we ever
     * need a third destination from splash (e.g., force-update screen), we just
     * add a new subclass. A boolean would force us to refactor everywhere.
     *
     * Sealed classes: only the cases defined here can ever exist.
     *
     * null = not yet decided (DataStore read in progress — splash is animating)
     * ShowOnboarding = first launch, show the 3-page onboarding
     * ShowNotesList = returning user, go straight to the notes list
     */
    sealed class SplashDestination {
        /** First launch — show the onboarding flow. */
        data object ShowOnboarding : SplashDestination()

        /** Returning user — skip straight to the main notes list. */
        data object ShowNotesList : SplashDestination()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * MutableStateFlow vs StateFlow:
     * - MutableStateFlow: can emit new values (used internally in ViewModel)
     * - StateFlow: read-only, exposed to the UI — UI can observe but not change
     *
     * This pattern (private mutable, public read-only) prevents the UI from
     * accidentally mutating the state directly. The ViewModel is the single
     * source of truth.
     *
     * Initial value = null, meaning "not ready yet". SplashScreen checks for
     * null before navigating (it waits for the animation to finish anyway).
     */
    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination: StateFlow<SplashDestination?> = _destination

    // ─────────────────────────────────────────────────────────────────────────
    // LOGIC
    // ─────────────────────────────────────────────────────────────────────────

    init {
        // 'init' runs as soon as the ViewModel is created.
        // We immediately check the onboarding status so the decision is ready
        // by the time the splash animation finishes (≈2.5 seconds).
        checkOnboardingStatus()
    }

    /**
     * Read the onboarding completion flag from DataStore.
     *
     * viewModelScope.launch: starts a coroutine tied to this ViewModel's
     * lifecycle. When the ViewModel is cleared (screen exits), any running
     * coroutines are automatically cancelled — no memory leaks.
     *
     * .first(): DataStore returns a Flow, but we only need one value here.
     * .first() takes the first emitted value and cancels the collection.
     * This is safe because DataStore always emits at least one value
     * immediately (the stored value or the default).
     */
    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            val onboardingCompleted = preferencesManager.onboardingCompletedFlow.first()
            _destination.value = if (onboardingCompleted) {
                SplashDestination.ShowNotesList
            } else {
                SplashDestination.ShowOnboarding
            }
        }
    }
}