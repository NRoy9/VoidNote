package com.greenicephoenix.voidnote.presentation.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import kotlinx.coroutines.launch

/**
 * OnboardingScreen — First-launch 3-page introduction to Void Note.
 *
 * WHAT IS THIS SCREEN FOR?
 * Onboarding has one job: communicate the app's value proposition before the
 * user sees an empty note list. We have 3 pages:
 *   Page 1 — Privacy: "Your notes. Nobody else's." (builds trust)
 *   Page 2 — Aesthetic: "Beauty in the void." (builds desire)
 *   Page 3 — Power: "Find anything. Instantly." (builds confidence)
 *
 * WHY HORIZONTAL PAGER?
 * HorizontalPager (Jetpack Compose Foundation) is the standard Compose
 * implementation of a swipeable page viewer. Users swipe left to advance,
 * or tap the navigation buttons. It's the same pattern used by every major
 * app (Spotify, Instagram, WhatsApp) for their onboarding.
 *
 * SKIP BUTTON:
 * Always visible in the top-right corner. Respects the user's time — if they
 * already understand the app, they shouldn't be forced through 3 screens.
 * Both Skip and "Get Started" call the same completion handler.
 *
 * HOW NAVIGATION WORKS:
 * OnboardingScreen doesn't navigate itself. It calls onOnboardingComplete()
 * which is a lambda passed from NavGraph. NavGraph then navigates to NotesList
 * and pops the onboarding destination off the back stack (so back press doesn't
 * bring them back here).
 *
 * @param onOnboardingComplete Called when user taps "Get Started" or "Skip"
 * @param viewModel            Persists the onboarding-completed flag in DataStore
 */
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    // ── Pager state ───────────────────────────────────────────────────────
    // PagerState tracks which page is currently shown and handles swipe physics.
    // pageCount = 3 means pages 0, 1, 2.
    val pagerState = rememberPagerState(pageCount = { OnboardingPage.pages.size })

    // coroutineScope lets us animate to a specific page programmatically.
    // We need this for the "Next" button — it calls pagerState.animateScrollToPage().
    val coroutineScope = rememberCoroutineScope()

    // ── Page content definitions ──────────────────────────────────────────
    // (See OnboardingPage sealed class at the bottom of this file)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // ── Skip button — always visible, top right ───────────────────────
        // Users who don't want onboarding can exit immediately.
        TextButton(
            onClick = {
                viewModel.completeOnboarding(onOnboardingComplete)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = Spacing.medium, end = Spacing.medium)
                // Adjust for status bar height (edge-to-edge mode)
                .statusBarsPadding()
        ) {
            Text(
                text = "Skip",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
        }

        // ── Page content area ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // HorizontalPager: the swipeable page container.
            // modifier = Modifier.weight(1f) makes it take all available space
            // between the top (skip button area) and bottom (nav controls).
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                // This block is called for each page.
                // 'pageIndex' is 0, 1, or 2.
                val page = OnboardingPage.pages[pageIndex]
                OnboardingPageContent(page = page)
            }

            // ── Page indicator dots + navigation buttons ──────────────────
            OnboardingNavigation(
                pagerState = pagerState,
                onNextPage = {
                    coroutineScope.launch {
                        // Animate scroll to the next page.
                        // animateScrollToPage is a suspend function — needs a coroutine.
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onComplete = {
                    viewModel.completeOnboarding(onOnboardingComplete)
                }
            )
        }
    }
}

/**
 * OnboardingPageContent — The main content area for a single onboarding page.
 *
 * Each page has:
 * - A large geometric icon/illustration (ASCII-art style, Nothing aesthetic)
 * - A bold headline
 * - A subtitle description
 *
 * WHY ASCII-ART STYLE GRAPHICS INSTEAD OF IMAGE FILES?
 * - Zero asset dependencies — no PNG/SVG files to manage
 * - Scales perfectly to any screen size
 * - Consistent with Nothing's dot-matrix, retro-tech aesthetic
 * - Instant load time (no disk I/O)
 *
 * @param page  The data for this page — icon, headline, subtitle
 */
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ── Icon / Illustration ───────────────────────────────────────────
        // A bordered box containing the dot-matrix style icon text.
        // This replicates the Nothing Phone UI card aesthetic.
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            // Inner content: large icon character + label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Large symbolic character — represents the page theme
                Text(
                    text = page.iconEmoji,
                    fontSize = 72.sp,
                    textAlign = TextAlign.Center
                )
                // Small label below the icon — Nothing-style ALL CAPS monospace feel
                Text(
                    text = page.iconLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 3.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.extraExtraLarge))

        // ── Headline ──────────────────────────────────────────────────────
        Text(
            text = page.headline,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        // ── Subtitle ──────────────────────────────────────────────────────
        Text(
            text = page.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}

/**
 * OnboardingNavigation — Page indicator dots + Next/Get Started button.
 *
 * This sits below the pager content and provides two controls:
 * 1. Dot indicators — show which page the user is on (standard UX pattern)
 * 2. Next button — advances to the next page. On the last page, it says
 *    "Get Started" and calls onComplete instead.
 *
 * WHY DOTS INSTEAD OF A PROGRESS BAR?
 * Dots communicate "how many pages" clearly. A progress bar would suggest
 * completion percentage — dots suggest discrete steps. This matches what users
 * expect from mobile onboarding flows.
 *
 * @param pagerState  The shared state with HorizontalPager — tells us currentPage
 * @param onNextPage  Called when user taps "Next" (not the last page)
 * @param onComplete  Called when user taps "Get Started" (last page)
 */
@Composable
private fun OnboardingNavigation(
    pagerState: PagerState,
    onNextPage: () -> Unit,
    onComplete: () -> Unit
) {
    val isLastPage = pagerState.currentPage == OnboardingPage.pages.size - 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.extraLarge)
            .padding(bottom = Spacing.extraExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
    ) {

        // ── Dot indicators ────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(OnboardingPage.pages.size) { index ->
                // Each dot: filled white if active, dim if inactive.
                // The active dot is wider — a pill shape — for a modern feel.
                val isActive = pagerState.currentPage == index

                // Animate the width of the dot for a smooth transition effect.
                val dotWidth by animateDpAsState(
                    targetValue = if (isActive) 24.dp else 8.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "dot_width_$index"
                )

                Box(
                    modifier = Modifier
                        .width(dotWidth)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                        )
                )
            }
        }

        // ── Primary CTA button ────────────────────────────────────────────
        // "Next" on pages 1 and 2. "Get Started" on page 3.
        // Full-width, high contrast — primary action is always obvious.
        Button(
            onClick = if (isLastPage) onComplete else onNextPage,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp), // 56dp is the standard Material 3 button height
            shape = RoundedCornerShape(12.dp), // Slightly rounded — not pill, not square
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground, // White on dark
                contentColor = MaterialTheme.colorScheme.background       // Black text on white
            )
        ) {
            Text(
                text = if (isLastPage) "Get Started" else "Next",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PAGE DATA MODEL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * OnboardingPage — Data model for a single onboarding page.
 *
 * WHY A SEALED CLASS INSTEAD OF DATA CLASS?
 * We could use a data class with a list, but the sealed class approach lets
 * each page be a self-contained named object. This makes the code more readable
 * at the call site — you can reference OnboardingPage.Privacy directly.
 *
 * Properties:
 * @param iconEmoji  Large visual element displayed in the card (Unicode symbol)
 * @param iconLabel  Small ALL CAPS label below the icon (e.g. "SECURE")
 * @param headline   Bold page title — 3-5 words, punchy
 * @param subtitle   2-3 sentence description — explains the benefit clearly
 */
sealed class OnboardingPage(
    val iconEmoji: String,
    val iconLabel: String,
    val headline: String,
    val subtitle: String
) {
    /**
     * Page 1 — Privacy
     * Core promise: your notes never leave your device unless you choose.
     * This builds trust before the user has committed to using the app.
     */
    data object Privacy : OnboardingPage(
        iconEmoji = "◎",                    // Hollow circle — the void symbol
        iconLabel = "SECURE",
        headline = "Your notes.\nNobody else's.",
        subtitle = "Everything stays on your device. No accounts required, no tracking, no ads. Ever. Your thoughts are yours alone."
    )

    /**
     * Page 2 — Aesthetic
     * The design differentiator. Most note apps look identical.
     * Void Note looks like nothing else — and that's the point.
     */
    data object Aesthetic : OnboardingPage(
        iconEmoji = "▣",                    // Grid/matrix symbol — dot-matrix theme
        iconLabel = "DESIGN",
        headline = "Beauty in\nthe void.",
        subtitle = "Three carefully crafted themes — Dark, Extra Dark for OLED, and Light. Minimalist. High contrast. Nothing wasted."
    )

    /**
     * Page 3 — Power
     * Reassures the user that minimal design doesn't mean limited features.
     * Folders, tags, search — it's a serious notes app.
     */
    data object Power : OnboardingPage(
        iconEmoji = "⌖",                    // Target/crosshair — precision/search
        iconLabel = "ORGANISED",
        headline = "Find anything.\nInstantly.",
        subtitle = "Folders, tags, and full-text search across every note, checklist, and folder name. Organised your way."
    )

    companion object {
        /**
         * Ordered list of all pages.
         * This is the single source of truth for page order and count.
         * HorizontalPager uses pages.size for pageCount.
         * Change the order here to reorder pages — nothing else needs updating.
         */
        val pages: List<OnboardingPage> = listOf(Privacy, Aesthetic, Power)
    }
}