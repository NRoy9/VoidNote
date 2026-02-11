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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Splash Screen - First screen users see
 *
 * Features:
 * - Nothing-inspired minimalist design
 * - Fade-in animation for text
 * - Pure black OLED background
 * - Automatically navigates to main screen after delay
 *
 * @param onNavigateToNotes Callback to navigate to notes list
 */
@Composable
fun SplashScreen(
    onNavigateToNotes: () -> Unit
) {
    // Animation state for fade-in effect
    var startAnimation by remember { mutableStateOf(false) }

    // Alpha (opacity) animation: 0 (invisible) → 1 (visible)
    val alphaAnimation = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1500, // 1.5 seconds fade-in
            easing = FastOutSlowInEasing // Smooth easing curve
        ),
        label = "splash_alpha"
    )

    // Trigger animation and navigation when screen loads
    LaunchedEffect(key1 = true) {
        startAnimation = true // Start fade-in
        delay(2500) // Show splash for 2.5 seconds total
        onNavigateToNotes() // Navigate to main screen
    }

    // UI Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background), // Pure black
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            // App name - Large, bold, Nothing-style
            Text(
                text = "VOID NOTE",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp // Wide letter spacing for impact
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(alphaAnimation.value)
            )

            // Tagline - Smaller, subtle
            Text(
                text = "Notes that disappear into the void",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.alpha(alphaAnimation.value)
            )
        }

        // Version text at bottom
        Text(
            text = "v1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.large)
                .alpha(alphaAnimation.value)
        )
    }
}