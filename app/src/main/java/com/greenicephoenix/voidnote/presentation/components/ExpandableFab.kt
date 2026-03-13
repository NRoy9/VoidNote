package com.greenicephoenix.voidnote.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * ExpandableFab — Speed Dial FAB menu.
 *
 * Tapping the main FAB expands a vertical stack of mini-FABs:
 *   • New Note       — navigate to blank editor
 *   • Templates      — open the template picker sheet  (Sprint 11)
 *   • Daily Note     — open/create today's dated note  (Sprint 11)
 *   • New Folder     — create a folder
 *
 * Items are listed bottom-to-top (the main FAB is at the bottom), so the
 * order in the Column is: Folder → Daily Note → Templates → New Note (top).
 *
 * @param onCreateNote     Navigate to a blank new note
 * @param onCreateFolder   Show the create-folder dialog
 * @param onOpenTemplates  Open the template picker bottom sheet
 * @param onDailyNote      Open or create today's Daily Note
 */
@Composable
fun ExpandableFab(
    onCreateNote: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenTemplates: () -> Unit = {},
    onDailyNote: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // The main FAB icon rotates 45° (+ → ×) when expanded
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label       = "fab_rotation"
    )

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        // ── Expanded menu items (animate in/out together) ─────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit    = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                // New Folder — at top of the expanded list (furthest from FAB)
                FabMenuItem(
                    text    = "New Folder",
                    icon    = Icons.Default.CreateNewFolder,
                    onClick = { expanded = false; onCreateFolder() }
                )

                // Daily Note — one tap opens/creates today's dated note
                FabMenuItem(
                    text    = "Daily Note",
                    icon    = Icons.Default.Today,
                    onClick = { expanded = false; onDailyNote() }
                )

                // Templates — opens the template picker sheet
                FabMenuItem(
                    text    = "Templates",
                    icon    = Icons.Default.Article,
                    onClick = { expanded = false; onOpenTemplates() }
                )

                // New Note — closest to the main FAB, most common action
                FabMenuItem(
                    text    = "New Note",
                    icon    = Icons.Default.NoteAdd,
                    onClick = { expanded = false; onCreateNote() }
                )
            }
        }

        // ── Main FAB ──────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick        = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector        = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "Close menu" else "Open menu",
                modifier           = Modifier.rotate(rotationAngle)
            )
        }
    }
}

/**
 * FabMenuItem — a single row inside the expanded FAB menu.
 * Shows a label pill on the left and a SmallFloatingActionButton on the right.
 */
@Composable
private fun FabMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Label pill
        Surface(
            shape          = RoundedCornerShape(8.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                text     = text,
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = Spacing.medium,
                    vertical   = Spacing.small
                )
            )
        }

        // Small FAB icon
        SmallFloatingActionButton(
            onClick        = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(imageVector = icon, contentDescription = text)
        }
    }
}