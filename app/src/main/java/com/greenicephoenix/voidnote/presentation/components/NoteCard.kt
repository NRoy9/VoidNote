package com.greenicephoenix.voidnote.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import java.text.SimpleDateFormat
import java.util.*

/**
 * Note Card — Displays a single note in the list screen.
 *
 * NOTHING DESIGN:
 * - Minimal card with subtle elevation
 * - Clean typography hierarchy
 * - Checklist badge in footer when note has TODO blocks
 *
 * CONTENT PREVIEW (important):
 * note.content is RAW — it may contain ⟦block:TODO:uuid⟧ marker tokens.
 * We always call note.getContentPreview() which strips markers internally.
 * Never display note.content directly in the UI.
 *
 * CHECKLIST BADGE:
 * When a note has checklist blocks, a small "☑ N lists" badge appears
 * in the footer next to tags. This tells the user at a glance that the
 * note has interactive content beyond plain text.
 * The count comes from note.checklistBlockCount() — a cheap regex scan
 * on the already-loaded content string, no extra DB queries.
 *
 * @param note     The note to display.
 * @param onClick  Called when the card is tapped.
 * @param modifier Optional modifier.
 */
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Compute these once — both are cheap string operations
    val contentPreview = note.getContentPreview(150)   // Markers stripped
    val hasChecklists = note.hasChecklists()
    val checklistCount = if (hasChecklists) note.checklistBlockCount() else 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {

            // ── Header: Title + Pin indicator ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (note.isPinned) {
                    Spacer(modifier = Modifier.width(Spacing.extraSmall))
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Content preview ───────────────────────────────────────────
            // Only shown if the note has logical text content.
            // A note with ONLY checklists (no text) will have an empty
            // contentPreview — we skip this block and let the checklist
            // badge in the footer communicate the content type instead.
            if (contentPreview.isNotBlank()) {
                Text(
                    text = contentPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.small))
            }

            // ── Footer: Tags + Checklist badge + Timestamp ────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: tags and/or checklist badge
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tags (up to 2 shown, +N for the rest)
                    note.tags.take(2).forEach { tag ->
                        ReadOnlyTagChip(tag = tag)
                    }
                    if (note.tags.size > 2) {
                        Text(
                            text = "+${note.tags.size - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    // ── Checklist badge ───────────────────────────────────
                    // Shown when the note has at least one TODO block.
                    // Visually echoes the "CHECKLIST" label in the editor —
                    // same dot-matrix uppercase letter-spacing style.
                    if (hasChecklists) {
                        ChecklistBadge(count = checklistCount)
                    }
                }

                // Timestamp — always on the right
                Text(
                    text = formatTimestamp(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Small checklist badge shown in the note card footer.
 *
 * DESIGN:
 *   [ ☑  2 LISTS ]  ← when count > 1
 *   [ ☑  LIST    ]  ← when count == 1
 *
 * Styled as a subtle pill — matches ReadOnlyTagChip visually but
 * uses the checklist icon instead of a # symbol.
 * Low-contrast, monochromatic — Nothing aesthetic.
 *
 * @param count Number of checklist blocks in the note.
 */
@Composable
private fun ChecklistBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckBox,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                // "2 LISTS" for multiple, "LIST" for one — matches Nothing's terse labeling style
                text = if (count > 1) "$count LISTS" else "LIST",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Formats a Unix timestamp into a human-readable relative or absolute string.
 *
 * Examples:
 *   < 1 min ago  → "Just now"
 *   < 1 hour     → "5 min ago"
 *   < 1 day      → "14:30"
 *   < 2 days     → "Yesterday"
 *   < 7 days     → "3d ago"
 *   older        → "Jan 15"
 */
private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L       -> "Just now"
        diff < 3_600_000L    -> "${diff / 60_000} min ago"
        diff < 86_400_000L   -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 172_800_000L  -> "Yesterday"
        diff < 604_800_000L  -> "${diff / 86_400_000}d ago"
        else                 -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}