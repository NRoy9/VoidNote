package com.greenicephoenix.voidnote.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * NoteQuickActionsSheet — Modal bottom sheet shown on long-press of a note card.
 *
 * ACTIONS:
 *   Pin / Unpin  — toggles pinned state, label changes based on current state
 *   Archive      — moves note out of the main list
 *   Delete       — sends note to trash (destructive, shown in error color)
 *
 * WHY A BOTTOM SHEET (not a dropdown)?
 * Bottom sheets are the Android standard for contextual actions on list items.
 * They're easier to reach with one thumb, have larger touch targets than a
 * dropdown menu, and don't obscure the item the user is acting on.
 *
 * @param note         The note being acted on — used for display and label logic.
 * @param onDismiss    Called when the sheet is dismissed without an action.
 * @param onTogglePin  Called when Pin/Unpin is tapped.
 * @param onArchive    Called when Archive is tapped.
 * @param onDelete     Called when Delete is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteQuickActionsSheet(
    note        : Note,
    onDismiss   : () -> Unit,
    onTogglePin : () -> Unit,
    onArchive   : () -> Unit,
    onDelete    : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = MaterialTheme.colorScheme.surface,
        dragHandle        = {
            // Minimal drag handle — Nothing aesthetic
            Box(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.medium, bottom = Spacing.small),
                contentAlignment    = Alignment.Center
            ) {
                Surface(
                    modifier  = Modifier.size(width = 32.dp, height = 3.dp),
                    shape     = MaterialTheme.shapes.extraLarge,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.large)
        ) {

            // ── Note title header ─────────────────────────────────────────────
            // Gives context — the user knows exactly which note they're acting on
            Text(
                text     = note.title.ifBlank { "Untitled Note" },
                style    = MaterialTheme.typography.titleMedium.copy(
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                ),
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(
                    horizontal = Spacing.large,
                    vertical   = Spacing.small
                )
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                modifier = Modifier.padding(vertical = Spacing.small)
            )

            // ── Pin / Unpin ───────────────────────────────────────────────────
            QuickActionRow(
                icon        = Icons.Default.PushPin,
                label       = if (note.isPinned) "Unpin" else "Pin to top",
                description = if (note.isPinned) "Remove from pinned" else "Keep at the top of your list",
                onClick     = { onTogglePin(); onDismiss() }
            )

            // ── Archive ───────────────────────────────────────────────────────
            QuickActionRow(
                icon        = Icons.Default.Archive,
                label       = "Archive",
                description = "Hide from main list, keep forever",
                onClick     = { onArchive(); onDismiss() }
            )

            // ── Delete (destructive) ──────────────────────────────────────────
            QuickActionRow(
                icon        = Icons.Default.Delete,
                label       = "Move to Trash",
                description = "Deleted after 30 days",
                onClick     = { onDelete(); onDismiss() },
                isDestructive = true
            )
        }
    }
}

/**
 * A single row inside the quick actions sheet.
 *
 * Layout:  [Icon]  [Label / Description]  →
 *
 * @param isDestructive  When true, icon and label render in error color.
 */
@Composable
private fun QuickActionRow(
    icon          : androidx.compose.ui.graphics.vector.ImageVector,
    label         : String,
    description   : String,
    onClick       : () -> Unit,
    isDestructive : Boolean = false
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = contentColor.copy(alpha = if (isDestructive) 1f else 0.8f),
                modifier           = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = contentColor
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}