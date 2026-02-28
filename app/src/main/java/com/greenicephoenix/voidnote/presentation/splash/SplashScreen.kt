package com.greenicephoenix.voidnote.presentation.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import kotlinx.coroutines.delay

/**
 * SplashScreen — First thing the user sees on every launch.
 *
 * SPRINT 3 CHANGES:
 * - Now accepts a ViewModel (injected by Hilt via hiltViewModel())
 * - Observes SplashViewModel.destination to decide navigation target
 * - Calls onNavigateToOnboarding on first launch
 * - Calls onNavigateToNotes on all subsequent launches
 *
 * ANIMATION FLOW:
 *   0.0s  Screen appears — content invisible (alpha=0)
 *   0.0s  Animation starts — content fades in over 1.5s
 *   2.5s  Navigate to the next destination
 *
 * WHY 2.5 SECONDS?
 * Long enough to feel intentional and branded, short enough not to frustrate
 * users. Industry standard for splash screens is 2-3 seconds.
 *
 * @param onNavigateToNotes      Navigate to NotesList (returning user path)
 * @param onNavigateToOnboarding Navigate to OnboardingScreen (first launch path)
 * @param viewModel              Injected by Hilt — reads onboarding status from DataStore
 */
@Composable
fun SplashScreen(
    onNavigateToNotes: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()  // Hilt provides this automatically
) {
    // ── Observe ViewModel state ────────────────────────────────────────────
    // collectAsState() converts a Kotlin Flow into a Compose State object.
    // Whenever the Flow emits a new value, Compose re-renders this composable.
    val destination by viewModel.destination.collectAsState()

    // ── Splash animation ──────────────────────────────────────────────────
    // mutableStateOf: a Compose state variable. When this changes, any
    // composable reading it re-renders. Here, changing startAnimation
    // triggers the animateFloatAsState below to animate.
    var startAnimation by remember { mutableStateOf(false) }

    // animateFloatAsState: smoothly interpolates a Float value.
    // When startAnimation flips to true, alpha animates from 0 to 1
    // over 1500ms using the FastOutSlowIn easing curve (starts fast, slows to finish).
    val alphaAnimation = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "splash_alpha"
    )

    // ── Navigation trigger ────────────────────────────────────────────────
    // LaunchedEffect runs this block once when the composable enters the
    // composition. The key1 = true means it never re-runs on recomposition.
    //
    // We wait 2500ms (covers the animation + brief hold) then navigate.
    // By then, the ViewModel should have read the preference (DataStore is fast).
    // If for some reason it hasn't finished, we default to NotesList — safe fallback.
    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2500L)

        // Navigate based on what the ViewModel decided.
        // 'destination' might still be null if DataStore was very slow (unlikely).
        // The when(null) branch is a safety net — go to notes list as a safe fallback.
        when (destination) {
            is SplashViewModel.SplashDestination.ShowOnboarding -> onNavigateToOnboarding()
            is SplashViewModel.SplashDestination.ShowNotesList  -> onNavigateToNotes()
            null -> onNavigateToNotes() // Safe fallback — shouldn't happen in practice
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            // App name — large, bold, Nothing-style wide tracking
            Text(
                text = "VOID NOTE",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(alphaAnimation.value)
            )

            // Tagline — smaller, secondary opacity, mysterious
            Text(
                text = "Notes that disappear into the void",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.alpha(alphaAnimation.value)
            )
        }

        // Version number — very subtle, bottom centre
        // This is useful for beta testers to report which build they're on
        Text(
            text = "v0.0.1",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.large)
                .alpha(alphaAnimation.value)
        )
    }
}