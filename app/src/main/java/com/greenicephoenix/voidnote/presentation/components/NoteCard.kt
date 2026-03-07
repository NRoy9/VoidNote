package com.greenicephoenix.voidnote.presentation.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import java.text.SimpleDateFormat
import java.util.*

/**
 * NoteCard — Displays a single note in the list screen.
 *
 * NOTHING DESIGN:
 * - Minimal card, always dark surface background regardless of note colour
 * - Only the left accent strip changes per note colour
 *
 * FIX — WHY THE STRIP WASN'T SHOWING:
 * The strip Box used fillMaxHeight() but the parent Row wraps its content,
 * giving it no bounded height for children to fill against. The strip
 * rendered at 0px height — invisible.
 *
 * The fix: add height(IntrinsicSize.Min) to the Row. This tells Compose to
 * measure the Row's intrinsic minimum height first (driven by the content
 * Column), then use that as the bounded height. Now fillMaxHeight() on the
 * strip Box resolves correctly — it fills to match the Column height.
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
    val contentPreview = note.getContentPreview(150)
    val hasChecklists  = note.hasChecklists()
    val checklistCount = if (hasChecklists) note.checklistBlockCount() else 0

    // Use the vivid pickerColor for the strip — muted tints are too subtle on a thin strip
    val accentColor: Color? = note.color?.pickerColor

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            // Always default surface — no tinted card backgrounds
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // ── THE KEY FIX ──────────────────────────────────────────────
                // IntrinsicSize.Min gives the Row a bounded height equal to its
                // tallest child's intrinsic height. Without this, the Row height
                // is "wrap content" which is unbounded — fillMaxHeight() on the
                // accent strip Box resolves to 0px (nothing renders).
                // With this, the strip fills the full card height correctly.
                .height(IntrinsicSize.Min)
        ) {

            // ── Left colour accent strip ──────────────────────────────────────
            // Only rendered when a colour is assigned to the note.
            // 4dp wide. Clips to the card's leading rounded corners.
            if (accentColor != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()   // Works now because Row has IntrinsicSize.Min
                        .clip(
                            RoundedCornerShape(
                                topStart    = 12.dp,
                                bottomStart = 12.dp
                            )
                        )
                        .background(accentColor.copy(alpha = 0.9f))
                )
            }

            // ── Card content ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {

                // ── Header: Title + Pin indicator ─────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text     = note.title.ifBlank { "Untitled Note" },
                        style    = MaterialTheme.typography.titleMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.isPinned) {
                        Spacer(modifier = Modifier.width(Spacing.extraSmall))
                        Icon(
                            imageVector        = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.small))

                // ── Content preview ───────────────────────────────────────────
                if (contentPreview.isNotBlank()) {
                    Text(
                        text     = contentPreview,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                }

                // ── Footer: Tags + Checklist badge + Timestamp ────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        modifier              = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        note.tags.take(2).forEach { tag ->
                            ReadOnlyTagChip(tag = tag)
                        }
                        if (note.tags.size > 2) {
                            Text(
                                text  = "+${note.tags.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        if (hasChecklists) {
                            ChecklistBadge(count = checklistCount)
                        }
                    }
                    Text(
                        text  = formatTimestamp(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}


/**
 * Small checklist badge shown in the note card footer.
 */
@Composable
private fun ChecklistBadge(count: Int) {
    Surface(
        shape          = RoundedCornerShape(4.dp),
        color          = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.CheckBox,
                contentDescription = null,
                modifier           = Modifier.size(10.dp),
                tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text  = if (count > 1) "$count LISTS" else "LIST",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}


/**
 * Formats a Unix timestamp into a human-readable relative string.
 */
private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L      -> "Just now"
        diff < 3_600_000L   -> "${diff / 60_000} min ago"
        diff < 86_400_000L  -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 172_800_000L -> "Yesterday"
        diff < 604_800_000L -> "${diff / 86_400_000}d ago"
        else                -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}