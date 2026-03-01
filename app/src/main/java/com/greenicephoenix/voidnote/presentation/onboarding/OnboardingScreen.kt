package com.greenicephoenix.voidnote.presentation.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import kotlinx.coroutines.launch

/**
 * OnboardingScreen — 3-page introduction shown on first install only.
 *
 * PAGE FLOW (enforced by button logic):
 *   Page 1 → [Next ]        → Page 2
 *   Page 2 → [Next ]        → Page 3
 *   Page 3 → [Get Started] → VaultSetup (marks onboarding complete)
 *
 * Skip (top-right) is available on pages 1 and 2 only.
 * Swiping between pages is also supported via HorizontalPager.
 *
 * PAGES:
 * 1. Welcome       — what Void Note is
 * 2. Encrypted     — AES-256, zero-knowledge, your key
 * 3. Fully Featured — rich text, folders, tags, biometric lock
 *
 * The three pages tell a complete story:
 *   what it is → why it's safe → what you can do with it
 *
 * DESIGN: Nothing aesthetic — pure black/white, pill dot indicators,
 * high-contrast button, generous vertical breathing room.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pages = listOf(
        OnboardingPage(
            symbol      = "○",
            title       = "WELCOME TO\nVOID NOTE",
            description = "A notes app built around one idea:\nyour thoughts are yours alone.\nMinimal by design. Private by default."
        ),
        OnboardingPage(
            symbol      = "⬡",
            title       = "ENCRYPTED\nBY DEFAULT",
            description = "Every note is protected with AES-256 encryption before it's saved. We can't read your notes. Nobody can."
        ),
        OnboardingPage(
            symbol      = "◈",
            title       = "EVERYTHING\nYOU NEED",
            description = "Rich text editor. Folders and tags. Biometric lock. Offline-first. No account. No tracking. No ads. Ever."
        )
    )

    val pagerState    = rememberPagerState(pageCount = { pages.size })
    val scope         = rememberCoroutineScope()
    val isLastPage    = pagerState.currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // ── Skip button — top-right, hidden on the last page ──────────────────
        // Skipping always completes onboarding and routes to VaultSetup.
        AnimatedVisibility(
            visible  = !isLastPage,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.medium),
            enter = fadeIn(),
            exit  = fadeOut()
        ) {
            TextButton(onClick = { viewModel.markOnboardingComplete(onCompleted) }) {
                Text(
                    text  = "Skip",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // ── Pager ──────────────────────────────────────────────────────────────
        // HorizontalPager handles swipe gestures between pages automatically.
        // The button below is the tap-based navigation alternative.
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // Leave room at the bottom for the controls
                .padding(bottom = 160.dp)
        ) { pageIndex ->
            OnboardingPageContent(page = pages[pageIndex])
        }

        // ── Bottom controls ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.extraLarge)
                .padding(horizontal = Spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {

            // ── Pill dot indicators ────────────────────────────────────────────
            // The active dot stretches wider (pill shape) — Nothing-style indicator.
            // Width transitions smoothly via animateDpAsState.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                repeat(pages.size) { index ->
                    val isSelected = index == pagerState.currentPage
                    val width by animateDpAsState(
                        targetValue   = if (isSelected) 24.dp else 7.dp,
                        animationSpec = tween(durationMillis = 250),
                        label         = "dot_width_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 7.dp)
                            .background(
                                color  = if (isSelected)
                                    MaterialTheme.colorScheme.onBackground
                                else
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            // ── Primary button ────────────────────────────────────────────────
            //
            // PAGES 1 & 2 → Label: "Next"
            //   onClick: animate pager to next page (pagerState.animateScrollToPage)
            //   Does NOT call markOnboardingComplete — user stays in onboarding.
            //
            // PAGE 3 (last) → Label: "Get Started"
            //   onClick: markOnboardingComplete → routes to VaultSetup
            //
            // WHY rememberCoroutineScope?
            // pagerState.animateScrollToPage() is a suspend function — it must run
            // inside a coroutine. We launch it from the composable's scope so it
            // is automatically cancelled if the screen leaves composition.
            Button(
                onClick = {
                    if (isLastPage) {
                        // Final page — complete onboarding and proceed to vault setup
                        viewModel.markOnboardingComplete(onCompleted)
                    } else {
                        // Not the last page — advance to the next page
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor   = MaterialTheme.colorScheme.background
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                // Label animates between "Next" and "Get Started" with a crossfade
                AnimatedContent(
                    targetState   = isLastPage,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "button_label"
                ) { isLast ->
                    Text(
                        text  = if (isLast) "Get Started" else "Next",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

// ── Single page composable ─────────────────────────────────────────────────────

private data class OnboardingPage(
    val symbol:      String,
    val title:       String,
    val description: String
)

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    // Each page fades in independently when it first enters composition.
    // key = page.symbol ensures the animation re-triggers when the page changes.
    var visible by remember(page.symbol) { mutableStateOf(false) }
    LaunchedEffect(page.symbol) { visible = true }
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label         = "page_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.extraLarge)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Large geometric symbol — Nothing dot-matrix aesthetic
        Text(
            text  = page.symbol,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.extraLarge))

        // Title — wide letter spacing, all-caps, bold
        Text(
            text      = page.title,
            style     = MaterialTheme.typography.headlineLarge.copy(
                fontWeight    = FontWeight.Bold,
                letterSpacing = 3.sp,
                lineHeight    = 44.sp
            ),
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        // Description — softer opacity, comfortable line height
        Text(
            text      = page.description,
            style     = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}