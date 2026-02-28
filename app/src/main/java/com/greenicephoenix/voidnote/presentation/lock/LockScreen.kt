package com.greenicephoenix.voidnote.presentation.lock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * Lock Screen — shown at app launch when biometric lock is enabled.
 *
 * DESIGN:
 * Pure black full-screen surface (OLED-friendly) with:
 * - App name in dot-matrix style
 * - Fingerprint icon (large, centered)
 * - "Unlock" button
 * - Subtle "Tap to authenticate" hint
 *
 * NOTHING AESTHETIC:
 * - Pure black background — no gradients, no images
 * - Monochromatic: white text on black
 * - Generous vertical breathing room
 * - Fingerprint icon at 80dp — prominent but not aggressive
 *
 * FLOW:
 * MainActivity shows this screen when:
 *   1. Biometric lock is enabled in Settings
 *   2. App is launched (cold start or foreground resume)
 * When auth succeeds, MainActivity sets isUnlocked = true → NavHost replaces this screen.
 *
 * @param onUnlockClick  Called when user taps the unlock button.
 *                       The actual BiometricPrompt is shown from MainActivity
 *                       (because BiometricPrompt needs a FragmentActivity reference).
 * @param errorMessage   Optional error string to display under the icon
 *                       (e.g. "Authentication failed — try again")
 */
@Composable
fun LockScreen(
    onUnlockClick: () -> Unit,
    errorMessage: String? = null
) {
    // Fingerprint icon pulses in on first composition — subtle, non-distracting
    var iconVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { iconVisible = true }
    val iconAlpha by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "lockIconAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),  // Pure black in dark mode
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {

            // ── App name ─────────────────────────────────────────────────────
            Text(
                text = "VOID NOTE",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Spacing.large))

            // ── Fingerprint icon ──────────────────────────────────────────────
            // Large and centered — the primary visual CTA.
            // Tapping it also triggers unlock (more intuitive than just the button).
            IconButton(
                onClick = onUnlockClick,
                modifier = Modifier
                    .size(100.dp)
                    .alpha(iconAlpha)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Unlock",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
            }

            // ── Hint text ─────────────────────────────────────────────────────
            Text(
                text = "Tap to authenticate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )

            // ── Error message (if any) ────────────────────────────────────────
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.large)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // ── Unlock button ─────────────────────────────────────────────────
            // Outlined style — present but not aggressive.
            // The fingerprint icon above is the primary CTA; this is the fallback.
            OutlinedButton(
                onClick = onUnlockClick,
                modifier = Modifier.width(200.dp)
            ) {
                Text("Unlock")
            }
        }
    }
}