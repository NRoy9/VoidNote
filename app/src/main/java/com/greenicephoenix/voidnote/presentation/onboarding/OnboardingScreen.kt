package com.greenicephoenix.voidnote.presentation.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.greenicephoenix.voidnote.presentation.theme.VoidNoteTheme
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    VoidNoteTheme(
        darkTheme = isSystemInDarkTheme(),
        extraDark  = false
    ) {
        OnboardingContent(onCompleted = onCompleted, viewModel = viewModel)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OnboardingContent(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel
) {
    val pages = listOf(
        OnboardingPage(
            symbol      = "○",
            title       = "WELCOME TO\nVOID NOTE",
            description = "A notes app built around one idea: your thoughts are yours alone. Minimal by design. Private by default."
            //description = "A notes app built around one idea:\nyour thoughts are yours alone.\nMinimal by design. Private by default."
        ),
        OnboardingPage(
            symbol      = "⬡",
            title       = "PRIVATE\nBY DESIGN",
            description = "AES-256 encryption before anything touches storage. Biometric lock. Vault password you control. Password-protected folders for extra privacy. Zero cloud. Zero tracking. Zero compromise."
            //description = "AES-256 encryption before anything touches storage. Biometric lock.\nVault password you control. Password-protected folders for extra privacy.\nZero cloud. Zero tracking. Zero compromise."
        ),
        OnboardingPage(
            symbol      = "◈",
            title       = "WRITE WITHOUT\nLIMITS",
            description = "Rich text. Numbered lists. Checklists. Voice notes. Images. Code blocks. Focus Mode for distraction-free writing."
            //description = "Rich text. Numbered lists. Checklists.\nVoice notes. Images. Code blocks.\nFocus Mode for distraction-free writing."
        ),
        OnboardingPage(
            symbol      = "⊞",
            title       = "CAPTURE\nANYWHERE",
            description = "Home screen widget for instant notes. Folders, tags, and a full Journal calendar. Link notes together. Templates to start faster. Offline-first. No account. No ads."
            //description = "Home screen widget for instant notes.\nFolders, tags, and a full Journal calendar.\nLink notes together. Templates to start faster.\nOffline-first. No account. No ads."
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope      = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 160.dp)
        ) { pageIndex ->
            OnboardingPageContent(page = pages[pageIndex])
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.extraLarge)
                .padding(horizontal = Spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
            // ── Pill dot indicators ────────────────────────────────────────────
            // Active dot: VoidAccent (primary). Inactive: primary at 20% opacity.
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
                                // primary = VoidAccent (#FF3B30) in your theme
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            // ── Next / Get Started button ──────────────────────────────────────
            // containerColor = primary (VoidAccent red), contentColor = white
            Button(
                onClick = {
                    if (isLastPage) {
                        viewModel.markOnboardingComplete(onCompleted)
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,   // VoidAccent red
                    contentColor   = MaterialTheme.colorScheme.onPrimary  // White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                AnimatedContent(
                    targetState    = isLastPage,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label          = "button_label"
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

private data class OnboardingPage(
    val symbol:      String,
    val title:       String,
    val description: String
)

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
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
        // Symbol tinted with primary (VoidAccent red)
        Text(
            text  = page.symbol,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Spacing.extraLarge))

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

        Text(
            text      = page.description,
            style     = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}