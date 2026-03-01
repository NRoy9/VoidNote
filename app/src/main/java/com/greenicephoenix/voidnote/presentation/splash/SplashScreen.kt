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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SplashScreen — startup animation + navigation decision point.
 *
 * TIMING GUARANTEE:
 * Navigation only happens when BOTH conditions are true:
 *   1. Minimum display time (SPLASH_DURATION_MS) has elapsed
 *   2. SplashViewModel has resolved the destination
 *
 * WHY THE PREVIOUS VERSION FAILED:
 * The old implementation had two separate LaunchedEffect blocks:
 *   - LaunchedEffect(Unit)        → delay(2000), then nothing
 *   - LaunchedEffect(destination) → navigates immediately when destination changes
 *
 * These ran independently. The ViewModel resolves in ~50ms (fast DataStore +
 * Keystore reads), so LaunchedEffect(destination) fired almost instantly —
 * long before the 2-second delay in the other block had elapsed.
 * Result: the splash flashed for a fraction of a second and disappeared.
 *
 * THE FIX — single LaunchedEffect, parallel coroutines, both must finish:
 *
 *   launch { delay(SPLASH_DURATION_MS) }        ← timer coroutine
 *   val dest = viewModel.destination             ← wait for resolved destination
 *       .filter { it !is Loading }
 *       .first()
 *
 * Both run in parallel. The outer coroutine only reaches the navigation call
 * after BOTH the timer and the destination resolve. Whichever finishes last
 * determines when we navigate — the minimum is always respected.
 *
 * If the ViewModel somehow takes longer than SPLASH_DURATION_MS (shouldn't
 * happen, but possible on very slow devices), we wait for it — no crash, no
 * blank screen, just a slightly longer splash.
 */

private const val SPLASH_DURATION_MS = 2200L  // Slightly longer than the fade-in (1500ms)

@Composable
fun SplashScreen(
    onNavigateToNotes: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToVaultUnlock: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    // ── Animation ─────────────────────────────────────────────────────────────
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnimation by animateFloatAsState(
        targetValue   = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label         = "splash_alpha"
    )

    // ── Single coroutine that handles both timing and navigation ───────────────
    //
    // launch { delay(...) } starts the timer as a CHILD coroutine.
    // The parent coroutine immediately moves on to await the destination.
    // joinAll() is implicit here — the timer and the destination wait run in
    // parallel because the timer is launched as a child, and the destination
    // await is the next sequential line.
    //
    // Actually the correct pattern: launch timer as child, await destination,
    // then join the timer. This way:
    //   - If timer finishes before destination → we wait for destination
    //   - If destination resolves before timer → we wait for timer
    //   - Both must complete before we navigate
    LaunchedEffect(Unit) {
        startAnimation = true

        // Start the minimum-time timer as a child coroutine
        val timerJob = launch { delay(SPLASH_DURATION_MS) }

        // Simultaneously wait for the ViewModel to resolve a destination.
        // filter { it !is Loading } skips the initial Loading state.
        // .first() suspends until the first non-Loading emission, then returns it.
        val resolvedDestination = viewModel.destination
            .filter { it !is SplashViewModel.Destination.Loading }
            .first()

        // Wait for the timer to finish if it hasn't already.
        // If destination resolved after the timer, this returns immediately.
        // If destination resolved before the timer, this waits out the remainder.
        timerJob.join()

        // Both conditions satisfied — navigate
        when (resolvedDestination) {
            is SplashViewModel.Destination.Onboarding  -> onNavigateToOnboarding()
            is SplashViewModel.Destination.VaultUnlock -> onNavigateToVaultUnlock()
            is SplashViewModel.Destination.NotesList   -> onNavigateToNotes()
            else -> Unit
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier        = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Text(
                text     = "VOID NOTE",
                style    = MaterialTheme.typography.displayLarge.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color    = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(alphaAnimation)
            )
            Text(
                text     = "Notes that disappear into the void",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.alpha(alphaAnimation)
            )
        }

        Text(
            text     = "v0.0.4-alpha",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.large)
                .alpha(alphaAnimation)
        )
    }
}