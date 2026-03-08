package com.greenicephoenix.voidnote.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * SwipeableNoteCard — NoteCard wrapped with swipe-to-action gestures.
 *
 * WHY THIS IS A SHARED COMPONENT (not private in one screen):
 * Both NotesListScreen and FolderNotesScreen show note cards.
 * Duplicating the swipe logic would mean two places to maintain.
 * Placing it here in `components/` means any screen can use it.
 *
 * GESTURES:
 *   Swipe RIGHT → Pin / Unpin  (green, pin icon)
 *   Swipe LEFT  → Archive       (orange, archive icon)
 *
 * HAPTIC FEEDBACK:
 * A LongPress haptic fires the moment the action threshold is crossed —
 * giving a physical confirmation that the gesture registered, before the
 * user lifts their finger. This is the same pattern used by Gmail,
 * Google Tasks, and most premium Android apps.
 *
 * @param note        The note to display.
 * @param onNoteClick Called on a normal tap.
 * @param onTogglePin Called when swiped right.
 * @param onArchive   Called when swiped left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNoteCard(
    note        : Note,
    onNoteClick : () -> Unit,
    onTogglePin : () -> Unit,
    onArchive   : () -> Unit
) {
    // Capture haptic in composable scope — safe to use inside the lambda below
    val haptic = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { newValue ->
            when (newValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Fire haptic at the moment the threshold is crossed
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePin()
                    false   // Don't dismiss — spring back after pin toggle
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onArchive()
                    false   // Room removes the card from list automatically
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        // 40% threshold — deliberate enough to not trigger accidentally,
        // easy enough to trigger intentionally
        positionalThreshold = { totalDistance -> totalDistance * 0.40f }
    )

    SwipeToDismissBox(
        state             = dismissState,
        backgroundContent = {
            SwipeBackground(
                dismissState = dismissState,
                isPinned     = note.isPinned
            )
        },
        modifier = Modifier.padding(
            horizontal = Spacing.medium,
            vertical   = Spacing.extraSmall
        )
    ) {
        NoteCard(note = note, onClick = onNoteClick)
    }
}

/**
 * The coloured background revealed behind the card during a swipe.
 *
 * StartToEnd (right) → green background, pin/unpin icon on the left
 * EndToStart (left)  → orange background, archive icon on the right
 *
 * Alpha fades from 0 → 1 as the drag progresses, giving smooth visual
 * feedback proportional to how far the user has dragged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    dismissState : SwipeToDismissBoxState,
    isPinned     : Boolean
) {
    val direction = dismissState.dismissDirection

    val (backgroundColor, icon, label) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Triple(
            Color(0xFF2E7D32),          // dark green
            Icons.Default.PushPin,
            if (isPinned) "UNPIN" else "PIN"
        )
        SwipeToDismissBoxValue.EndToStart -> Triple(
            Color(0xFFE65100),          // dark orange
            Icons.Default.Archive,
            "ARCHIVE"
        )
        else -> Triple(Color.Transparent, Icons.Default.PushPin, "")
    }

    // Fade proportional to drag distance — full opacity at 50% drag
    val progress = dismissState.progress.coerceIn(0f, 1f)
    val alpha    = (progress * 2f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor.copy(alpha = alpha)),
        contentAlignment = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            else                              -> Alignment.CenterEnd
        }
    ) {
        if (progress > 0.05f) {
            Row(
                modifier              = Modifier.padding(horizontal = Spacing.large),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(icon, label, tint = Color.White.copy(alpha = alpha), modifier = Modifier.size(20.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = alpha))
                } else {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = alpha))
                    Icon(icon, label, tint = Color.White.copy(alpha = alpha), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}