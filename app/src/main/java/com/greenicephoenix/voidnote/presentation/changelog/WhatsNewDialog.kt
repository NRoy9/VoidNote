package com.greenicephoenix.voidnote.presentation.changelog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenicephoenix.voidnote.data.changelog.ChangeType
import com.greenicephoenix.voidnote.data.changelog.ChangelogData
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * WhatsNewDialog — shown once per version on first launch after an update.
 *
 * TRIGGER LOGIC (in MainActivity):
 *   lastSeenVersion (DataStore) != currentVersion (BuildConfig / PackageInfo)
 *   → show this dialog
 *   → on dismiss: call markVersionSeen(currentVersion)
 *
 * DESIGN — Nothing aesthetic:
 * - Full-width dialog (no narrow Material default width)
 * - Version number large, dot-matrix letter-spacing
 * - Changes grouped by type, each with a colour-coded dot
 * - Single "Got it" CTA — no cancel, user must acknowledge
 *
 * @param onDismiss  Called when user taps "Got it". Caller saves the version.
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    // We always show the LATEST entry — this dialog is for the current update
    val entry = ChangelogData.entries.firstOrNull() ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false  // lets us make the dialog wider
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // ── Header ───────────────────────────────────────────────────
                Text(
                    text = "WHAT'S NEW",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 3.sp,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = entry.version,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = entry.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )

                Spacer(modifier = Modifier.height(Spacing.large))

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                // ── Change list (scrollable if many items) ────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    entry.changes.forEach { change ->
                        ChangeRow(change.type, change.description)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.large))

                // ── CTA ───────────────────────────────────────────────────────
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

/**
 * A single row in the changelog — coloured dot + description text.
 *
 * Dot colours:
 *   NEW      → primary (accent red in Nothing theme)
 *   IMPROVED → secondary / blue-ish
 *   FIXED    → green
 *   SECURITY → amber / orange
 */
@Composable
private fun ChangeRow(type: ChangeType, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Coloured dot — type indicator
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .background(
                    color = dotColor(type),
                    shape = CircleShape
                )
        )

        Column {
            Text(
                text = type.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.8.sp,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = dotColor(type).copy(alpha = 0.85f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}

/** Maps ChangeType to a colour. Uses Material colorScheme so it respects dark/light. */
@Composable
private fun dotColor(type: ChangeType): Color = when (type) {
    ChangeType.NEW      -> MaterialTheme.colorScheme.primary          // accent (red in Nothing theme)
    ChangeType.IMPROVED -> MaterialTheme.colorScheme.secondary        // secondary
    ChangeType.FIXED    -> Color(0xFF4CAF50)                          // green — universally "fixed"
    ChangeType.SECURITY -> Color(0xFFFF9800)                          // amber — security attention
}