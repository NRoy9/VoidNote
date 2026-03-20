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
 * OnboardingScreen — 4-page introduction shown on first install only.
 *
 * PAGE FLOW (enforced by button logic):
 *   Page 1 → [Next]         → Page 2
 *   Page 2 → [Next]         → Page 3
 *   Page 3 → [Next]         → Page 4
 *   Page 4 → [Get Started] → VaultSetup (marks onboarding complete)
 *
 * Swiping between pages is also supported via HorizontalPager.
 *
 * PAGES:
 * 1. Welcome       — what Void Note is
 * 2. Private       — AES-256, vault password, biometric, zero cloud
 * 3. Write         — rich text, headings, checklists, voice, images, code blocks
 * 4. Organise      — folders, tags, journal, note linking, templates, daily note
 *
 * The four pages tell a complete story:
 *   what it is → why it's safe → what you can write → how you stay organised
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
            title       = "PRIVATE\nBY DESIGN",
            description = "AES-256 encryption before anything touches storage.\nVault password you control. Biometric lock.\nZero cloud. Zero tracking. Zero compromise."
        ),
        OnboardingPage(
            symbol      = "◈",
            title       = "WRITE WITHOUT\nLIMITS",
            description = "Rich text. Headings. Numbered lists. Checklists.\nVoice notes. Images. Code blocks.\nFocus Mode for distraction-free writing."
        ),
        OnboardingPage(
            symbol      = "⊞",
            title       = "ORGANISE\nEVERYTHING",
            description = "Folders, tags, and a full Journal calendar.\nLink notes together. Daily Note shortcut.\nTemplates to start faster. Offline-first. No account. No ads."
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
            // PAGES 1–3 → Label: "Next"
            //   onClick: animate pager to next page (pagerState.animateScrollToPage)
            //   Does NOT call markOnboardingComplete — user stays in onboarding.
            //
            // PAGE 4 (last) → Label: "Get Started"
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