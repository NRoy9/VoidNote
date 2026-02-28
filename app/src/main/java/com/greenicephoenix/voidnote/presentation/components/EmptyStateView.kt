package com.greenicephoenix.voidnote.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * EmptyStateView — Reusable composable for screens with no content.
 *
 * SPRINT 3 — new. Used by every list screen when there is nothing to show.
 *
 * WHY DO EMPTY STATES MATTER?
 * An empty list is one of the most critical moments in a user's journey.
 * A blank white screen with no guidance causes confusion and drop-off.
 * A polished empty state:
 * - Tells the user WHY it's empty (context)
 * - Tells the user WHAT TO DO (call to action hint)
 * - Maintains the app's visual language (Nothing aesthetic — even empty looks good)
 *
 * DESIGN APPROACH:
 * Each empty state has a large symbolic character (ASCII/Unicode) that visually
 * represents the content type. This is consistent with Nothing's dot-matrix,
 * symbolic aesthetic. No complex illustrations — just type and geometry.
 *
 * FADE-IN ANIMATION:
 * The empty state fades in over 600ms. Without this, it "pops" on screen when
 * data loads and there's nothing to show — jarring. The fade is subtle but
 * makes the transition feel intentional and polished.
 *
 * USAGE EXAMPLE:
 * ```kotlin
 * // In NotesListScreen when notes list is empty:
 * if (notes.isEmpty()) {
 *     EmptyStateView(
 *         symbol = "◎",
 *         title = "The void awaits",
 *         subtitle = "Tap + to write your first note.\nYour thoughts, encrypted and yours."
 *     )
 * }
 * ```
 *
 * @param symbol    Large Unicode/ASCII character — visual identity of the empty state
 * @param title     Short, evocative headline — 3-5 words
 * @param subtitle  1-2 sentences explaining the empty state + what to do
 * @param modifier  Standard Compose modifier for layout customisation from caller
 */
@Composable
fun EmptyStateView(
    symbol: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    // ── Fade-in animation ─────────────────────────────────────────────────
    // We start with alpha 0 and animate to 1 when this composable enters.
    // This prevents the jarring "pop" when data loads and nothing is there.
    var visible by remember { mutableStateOf(false) }

    // Animate alpha from 0 → 1 over 600ms using a simple linear curve.
    // FastOutLinearInEasing is good for exits; LinearEasing for neutral fades.
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "empty_state_alpha"
    )

    // LaunchedEffect(Unit) runs once when this composable enters the composition.
    // Setting visible = true triggers the alpha animation above.
    LaunchedEffect(Unit) {
        visible = true
    }

    // ── Layout ────────────────────────────────────────────────────────────
    // The empty state is centred in the available space.
    // The caller's modifier controls where "available space" is — typically
    // it's fillMaxSize() to centre in the whole screen.
    Box(
        modifier = modifier.alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            modifier = Modifier.padding(horizontal = Spacing.extraLarge)
        ) {

            // ── Symbol ────────────────────────────────────────────────────
            // Very large text — acts as an icon. Unicode characters scale
            // perfectly to any resolution with zero asset management.
            Text(
                text = symbol,
                fontSize = 80.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Title ─────────────────────────────────────────────────────
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            // ── Subtitle ──────────────────────────────────────────────────
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PRE-DEFINED EMPTY STATES
// ─────────────────────────────────────────────────────────────────────────────
// These are convenience composables for each screen in the app.
// They wrap EmptyStateView with the correct symbol, title, and subtitle.
// This centralises the copy so changing a message is one-line edit.

/**
 * Empty state for the main Notes List screen (no notes at all).
 * Shown on fresh install after onboarding completes.
 */
@Composable
fun NotesEmptyState(modifier: Modifier = Modifier) {
    EmptyStateView(
        symbol = "◎",
        title = "The void awaits",
        subtitle = "Tap + to write your first note.\nYour thoughts, encrypted and yours.",
        modifier = modifier
    )
}

/**
 * Empty state for the Notes List screen when no notes match the current filter.
 * Shown when user filters by a tag or folder that has no notes.
 */
@Composable
fun FilteredNotesEmptyState(modifier: Modifier = Modifier) {
    EmptyStateView(
        symbol = "⊘",
        title = "Nothing here",
        subtitle = "No notes match the current filter.\nTry a different tag or folder.",
        modifier = modifier
    )
}

/**
 * Empty state for the Search screen (no results found).
 * Shown when the user's query returns no matches.
 */
@Composable
fun SearchEmptyState(modifier: Modifier = Modifier) {
    EmptyStateView(
        symbol = "⌖",
        title = "Into the void",
        subtitle = "No notes, folders, or tags match your search.\nTry different keywords.",
        modifier = modifier
    )
}

/**
 * Empty state for the Search screen before the user types anything.
 * Shown when the search bar is focused but the query is empty.
 */
@Composable
fun SearchIdleState(modifier: Modifier = Modifier) {
    EmptyStateView(
        symbol = "⌕",
        title = "Search the void",
        subtitle = "Search by note title, content,\ntags, or folder names.",
        modifier = modifier
    )
}

/**
 * Empty state for the Folders screen (no folders created yet).
 */
@Composable
fun FoldersEmptyState(modifier: Modifier = Modifier) {
    EmptyStateView(
        symbol = "□",
        title = "No folders yet",
        subtitle = "Create folders to organise your notes.\nTap + to create your first folder.",
        modifier = modifier
    )
}

/**
 * Empty state for the Folder Notes screen (folder exists but has no notes).
 */
@Composable
fun FolderNotesEmptyState(folderName: String, modifier: Modifier = Modifier) {
    EmptyStateView(
        symbol = "▣",
        title = "$folderName is empty",
        subtitle = "No notes in this folder yet.\nOpen a note and assign it to this folder.",
        modifier = modifier
    )
}

/**
 * Empty state for the Trash screen (trash is empty).
 */
@Composable
fun TrashEmptyState(modifier: Modifier = Modifier) {
    EmptyStateView(
        symbol = "∅",
        title = "Nothing to discard",
        subtitle = "Deleted notes appear here.\nThey're automatically removed after 30 days.",
        modifier = modifier
    )
}

/**
 * Empty state for the Archive screen (nothing archived).
 */
@Composable
fun ArchiveEmptyState(modifier: Modifier = Modifier) {
    EmptyStateView(
        symbol = "◫",
        title = "Archive is empty",
        subtitle = "Archived notes are hidden from the main list\nbut never auto-deleted. Archive from a note's menu.",
        modifier = modifier
    )
}