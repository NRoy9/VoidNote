package com.greenicephoenix.voidnote.presentation.changelog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.data.changelog.ChangeType
import com.greenicephoenix.voidnote.data.changelog.ChangelogData
import com.greenicephoenix.voidnote.data.changelog.VersionEntry
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * ChangelogScreen — full scrollable version history.
 *
 * Accessed from: Settings → About → "What's New"
 *
 * Shows ALL entries from ChangelogData.entries (newest first).
 * Each entry has:
 *   - Version number + release date
 *   - Tagline
 *   - List of changes with colour-coded type dots
 *
 * Adding a new release: just add to ChangelogData.entries — this screen
 * picks it up automatically with no UI changes needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "What's New",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(
                horizontal = Spacing.large,
                vertical = Spacing.medium
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
            items(ChangelogData.entries) { entry ->
                VersionCard(entry = entry)
            }

            // Bottom padding so last card isn't flush with nav bar
            item { Spacer(modifier = Modifier.height(Spacing.large)) }
        }
    }
}

/**
 * Card for a single version's release notes.
 *
 * Layout:
 *   ┌──────────────────────────────────┐
 *   │  0.0.1-alpha        28 Feb 2026  │  ← version + date
 *   │  First alpha — the void opens.   │  ← tagline
 *   │  ─────────────────────────────   │
 *   │  ● NEW   Rich text editor…       │  ← changes
 *   │  ● NEW   Checklists…             │
 *   │  ● FIXED Cursor no longer…       │
 *   └──────────────────────────────────┘
 */
@Composable
private fun VersionCard(entry: VersionEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            // ── Version header row ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.version,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = entry.releaseDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // ── Tagline ───────────────────────────────────────────────────
            Text(
                text = entry.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            Spacer(modifier = Modifier.height(4.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Change rows ───────────────────────────────────────────────
            entry.changes.forEach { change ->
                ChangelogRow(type = change.type, description = change.description)
            }
        }
    }
}

/**
 * One change item: coloured dot + type label + description.
 * Same visual design as WhatsNewDialog.ChangeRow for consistency.
 */
@Composable
private fun ChangelogRow(type: ChangeType, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        // Type dot
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .background(color = dotColor(type), shape = CircleShape)
        )

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun dotColor(type: ChangeType): Color = when (type) {
    ChangeType.NEW      -> MaterialTheme.colorScheme.primary
    ChangeType.IMPROVED -> MaterialTheme.colorScheme.secondary
    ChangeType.FIXED    -> Color(0xFF4CAF50)
    ChangeType.SECURITY -> Color(0xFFFF9800)
}