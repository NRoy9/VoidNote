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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.greenicephoenix.voidnote.R

/**
 * SplashScreen — startup animation + navigation decision point.
 *
 * CHANGES IN THIS UPDATE:
 * - Added VoidNoteLogo] composable — draws the document logo via Compose Canvas.
 *   This replaces the invisible system splash icon (which was white-on-white in light mode).
 * - The logo fades in alongside the text using the same alphaAnimation.
 * - Version number updated to match build.gradle.kts.
 *
 * TIMING GUARANTEE:
 * Navigation only happens when BOTH conditions are true:
 *   1. Minimum display time (SPLASH_DURATION_MS) has elapsed
 *   2. SplashViewModel has resolved the destination
 *
 * HOW THE ANIMATION WORKS:
 * alphaAnimation goes from 0f → 1f over 1500ms using FastOutSlowInEasing.
 * startAnimation is set to true immediately, triggering the animation.
 * Everything on screen (logo + title + tagline + version) fades in together.
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

    // ── Navigation logic (unchanged from previous version) ────────────────────
    LaunchedEffect(Unit) {
        startAnimation = true
        val timerJob = launch { delay(SPLASH_DURATION_MS) }
        val resolvedDestination = viewModel.destination
            .filter { it !is SplashViewModel.Destination.Loading }
            .first()
        timerJob.join()

        when (resolvedDestination) {
            is SplashViewModel.Destination.Onboarding  -> onNavigateToOnboarding()
            is SplashViewModel.Destination.VaultUnlock -> onNavigateToVaultUnlock()
            is SplashViewModel.Destination.NotesList   -> onNavigateToNotes()
            else -> Unit
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {

            // ── Logo — drawn via Canvas, always visible in both light and dark ─
            //
            // WHY CANVAS INSTEAD OF AN IMAGE RESOURCE?
            // The system splash icon (shown by Android before Compose loads) is
            // white — invisible on a white light-mode background. Drawing the logo
            // ourselves in Compose gives us full control over colours in both themes.
            //
            // The logo uses hardcoded colours that match the design system:
            //   White (#FFFFFF) for the document outline and text lines
            //   Red   (#FF3B30) for the fold crease and void circle (VoidAccent)
            //
            // On dark background → white is fully visible.
            // On light background → white document outline on light background is
            // subtle, but the red accent anchors it. This is intentional — the
            // Nothing aesthetic works on light mode too with high-contrast red.
            VoidNoteLogo(
                size     = 96.dp,
                modifier = Modifier.alpha(alphaAnimation)
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── App name ──────────────────────────────────────────────────────
            Text(
                text     = "VOID NOTE",
                style    = MaterialTheme.typography.displayLarge.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color    = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(alphaAnimation)
            )

            // ── Tagline ───────────────────────────────────────────────────────
            Text(
                text     = "Notes that disappear into the void",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.alpha(alphaAnimation)
            )
        }

        // ── Version number — bottom of screen ─────────────────────────────────
        Text(
            text     = "v1.2.0",   // Keep in sync with versionName in build.gradle.kts
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.large)
                .alpha(alphaAnimation)
        )
    }
}

@Composable
fun VoidNoteLogo(
    size: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
        contentDescription = "Void Note Logo",
        modifier = modifier.size(size)
    )
}