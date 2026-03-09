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
 * SwipeableNoteCard — NoteCard with swipe gestures and long-press support.
 *
 * GESTURES:
 *   Swipe RIGHT  → Pin / Unpin  (green)
 *   Swipe LEFT   → Archive      (orange)
 *   Long press   → Quick actions sheet (pin, archive, delete)
 *
 * HAPTICS:
 *   Swipe threshold crossed → LongPress haptic
 *   Long press              → LongPress haptic (handled inside NoteCard)
 *
 * UNDO:
 *   onArchive returns the noteId to the caller so the screen can show
 *   a snackbar with an Undo action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNoteCard(
    note        : Note,
    onNoteClick : () -> Unit,
    onTogglePin : () -> Unit,
    onArchive   : () -> Unit,
    onLongClick : (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { newValue ->
            when (newValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePin()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onArchive()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.40f }
    )

    SwipeToDismissBox(
        state             = dismissState,
        backgroundContent = {
            SwipeBackground(dismissState = dismissState, isPinned = note.isPinned)
        },
        modifier = Modifier.padding(
            horizontal = Spacing.medium,
            vertical   = Spacing.extraSmall
        )
    ) {
        NoteCard(
            note        = note,
            onClick     = onNoteClick,
            onLongClick = onLongClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    dismissState : SwipeToDismissBoxState,
    isPinned     : Boolean
) {
    val direction = dismissState.dismissDirection

    val (backgroundColor, icon, label) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Triple(
            Color(0xFF2E7D32),
            Icons.Default.PushPin,
            if (isPinned) "UNPIN" else "PIN"
        )
        SwipeToDismissBoxValue.EndToStart -> Triple(
            Color(0xFFE65100),
            Icons.Default.Archive,
            "ARCHIVE"
        )
        else -> Triple(Color.Transparent, Icons.Default.PushPin, "")
    }

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