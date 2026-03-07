package com.greenicephoenix.voidnote.presentation.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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

/**
 * SplashScreen — startup animation + navigation decision point.
 *
 * CHANGES IN THIS UPDATE:
 * - Added [VoidNoteLogo] composable — draws the document logo via Compose Canvas.
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
            text     = "@string/app_name",   // Keep in sync with versionName in build.gradle.kts
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.large)
                .alpha(alphaAnimation)
        )
    }
}


/**
 * VoidNoteLogo — The Void Note document icon drawn entirely with Compose Canvas.
 *
 * DESIGN:
 * This is the exact same logo used on the website and Android adaptive icon,
 * drawn programmatically so it's theme-aware and resolution-independent.
 *
 * ELEMENTS:
 *   1. Document outline — white rectangle with folded top-right corner
 *   2. Red fold crease  — L-shaped red lines at the top-right corner
 *   3. Three text lines — white horizontal lines representing note content
 *   4. Void circle      — small red circle, the ○ brand signature
 *
 * COORDINATE SYSTEM:
 * All coordinates are based on a 96×96 unit viewbox (matching the SVG source).
 * The scale factor converts those units to actual pixels based on [size].
 * This makes the logo crisp at any dp size.
 *
 * COLORS:
 *   White = #FFFFFF — matches VoidWhite in Color.kt
 *   Red   = #FF3B30 — matches VoidAccent in Color.kt exactly
 *
 * @param size     The dp size of the logo square. Default 96.dp for splash screen.
 * @param modifier Optional modifier for placement/animation.
 */
@Composable
fun VoidNoteLogo(
    size: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    // Brand colours — hardcoded so the logo looks correct on BOTH light and dark backgrounds.
    // Do NOT use MaterialTheme colors here — the logo must be consistent everywhere.
    val white = Color(0xFFFFFFFF)
    val red   = Color(0xFFFF3B30)    // VoidAccent — matches Color.kt
    val black = Color(0xFF000000)

    Box(
        modifier = modifier
            .size(size)
            .aspectRatio(1f) // Ensures it stays a perfect circle
            .border(
                width = 2.dp,   // Adjust this for thickness (thin = 1.dp to 2.dp)
                color = red,
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(black)
            .padding(size * 0.12f), // Slightly increased padding to avoid the red border
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = modifier.size(size)) {

            // scale converts our 96-unit coordinate system to actual canvas pixels.
            // e.g. at size=96.dp on a 2x screen: canvasSize=192px, scale=192/96=2.0
            val scale = this.size.width / 96f

            // Helper lambdas — convert design coordinates to canvas coordinates.
            // 's' = scale. Using inline lambdas avoids allocating lambda objects per frame.
            fun sx(x: Float) = x * scale
            fun sy(y: Float) = y * scale

            // ── 1. DOCUMENT OUTLINE ───────────────────────────────────────────────
            //
            // A rectangle with the top-right corner "notched" for the fold effect.
            // Drawn as a single Path going clockwise from the top-left corner:
            //   top-left → top edge → fold-start point → diagonal cut → right edge
            //   → bottom-right corner (rounded) → bottom edge
            //   → bottom-left corner (rounded) → left edge → back to top-left
            //
            // quadraticBezierTo() creates the rounded corners (equivalent to rx="3" in SVG).
            // The control point is at the actual corner; the end point is where the straight
            // edge begins/ends — this creates a smooth curve matching RoundedCornerShape(3.dp).
            val documentPath = Path().apply {
                moveTo(sx(25f), sy(16f))             // top-left, after corner
                lineTo(sx(58f), sy(16f))             // top edge → fold start
                lineTo(sx(74f), sy(32f))             // diagonal cut (the folded corner)
                lineTo(sx(74f), sy(79f))             // right edge down
                quadraticBezierTo(                   // bottom-right rounded corner
                    sx(74f), sy(82f),
                    sx(71f), sy(82f)
                )
                lineTo(sx(25f), sy(82f))             // bottom edge
                quadraticBezierTo(                   // bottom-left rounded corner
                    sx(22f), sy(82f),
                    sx(22f), sy(79f)
                )
                lineTo(sx(22f), sy(19f))             // left edge up
                quadraticBezierTo(                   // top-left rounded corner
                    sx(22f), sy(16f),
                    sx(25f), sy(16f)
                )
                close()
            }
            drawPath(
                path = documentPath,
                color = white,
                style = Stroke(
                    width = sx(2.5f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // ── 2. RED FOLD CREASE ────────────────────────────────────────────────
            //
            // Two connected lines forming an L-shape at the top-right corner:
            //   - Vertical: from fold-start (58,16) down to (58,32)
            //   - Horizontal: from (58,32) right to fold-end (74,32)
            //
            // This is the "dog-ear" fold. The red colour makes it the most eye-catching
            // element of the icon — it anchors the brand identity.
            val foldPath = Path().apply {
                moveTo(sx(58f), sy(16f))
                lineTo(sx(58f), sy(32f))
                lineTo(sx(74f), sy(32f))
            }
            drawPath(
                path = foldPath,
                color = red,
                style = Stroke(
                    width = sx(2.5f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // ── 3. TEXT LINES ─────────────────────────────────────────────────────
            //
            // Three horizontal lines representing the note content.
            // The third line is intentionally shorter (ends at x=48 instead of x=60)
            // — it looks like a paragraph that doesn't fill the full width. More natural.
            //
            // strokeWidth is scaled so lines stay proportionally thin at any size.
            val lineStroke = sx(2f)

            drawLine(                                // First line — full width
                color = white,
                start = Offset(sx(30f), sy(44f)),
                end = Offset(sx(60f), sy(44f)),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
            drawLine(                                // Second line — full width
                color = white,
                start = Offset(sx(30f), sy(53f)),
                end = Offset(sx(60f), sy(53f)),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
            drawLine(                                // Third line — shorter (paragraph end)
                color = white,
                start = Offset(sx(30f), sy(62f)),
                end = Offset(sx(48f), sy(62f)),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )

            // ── 4. VOID CIRCLE ────────────────────────────────────────────────────
            //
            // The ○ brand mark. Sits inside the document at bottom-right.
            // "The void is inside the note."
            //
            // Positioned at (60, 66) in the 96-unit space — overlapping the last text
            // line slightly. Radius=7, no fill, red stroke.
            // The same red as the fold ties both elements together visually.
            drawCircle(
                color = red,
                radius = sx(7f),
                center = Offset(sx(60f), sy(66f)),
                style = Stroke(width = sx(2f))
            )
        }
    }
}