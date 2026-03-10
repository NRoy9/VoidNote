package com.greenicephoenix.voidnote.presentation.changelog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
 * ChangelogScreen — scrollable version history with expand/collapse.
 *
 * BEHAVIOUR:
 *   • Latest entry (index 0) is ALWAYS fully expanded, no collapse toggle.
 *     Users land here from "What’s New" and should see the new stuff immediately.
 *   • All older entries start collapsed (header + tagline only).
 *     Tapping the header row expands/collapses the change list.
 *   • A chevron icon on the header row signals the collapsed/expanded state.
 *
 * Adding a new release: add to ChangelogData.entries — this screen
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
        // Track which older entries are expanded. Latest is always expanded
        // so it never enters this list — we just skip the check for index 0.
        // mutableStateListOf is used instead of mutableStateSetOf because it is
        // guaranteed to be in scope across all Compose versions via runtime.*
        val expandedVersions = remember { mutableStateListOf<String>() }

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
            itemsIndexed(ChangelogData.entries) { index, entry ->
                val isLatest   = index == 0
                // Latest is always expanded; older entries toggle via state set
                val isExpanded = isLatest || expandedVersions.contains(entry.version)

                VersionCard(
                    entry      = entry,
                    isLatest   = isLatest,
                    isExpanded = isExpanded,
                    onToggle   = {
                        if (!isLatest) {
                            if (expandedVersions.contains(entry.version))
                                expandedVersions.remove(entry.version)
                            else
                                expandedVersions.add(entry.version)
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }
        }
    }
}

/**
 * Card for a single version’s release notes.
 *
 * @param isLatest   True for index 0 — always expanded, no toggle affordance.
 * @param isExpanded Whether the change list is currently visible.
 * @param onToggle   Called when header row is tapped (no-op when isLatest).
 */
@Composable
private fun VersionCard(
    entry: VersionEntry,
    isLatest: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header row — always visible ──────────────────────────────────────
            // The latest entry has no click handler (no collapse). Older entries
            // are fully clickable and show a chevron to indicate the state.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isLatest) Modifier.clickable(onClick = onToggle)
                        else Modifier
                    )
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Version + date on same line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = entry.version,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // "LATEST" badge on the newest entry
                            if (isLatest) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "LATEST",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = entry.releaseDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Tagline — always visible even when collapsed
                    Text(
                        text = entry.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                // Chevron — only shown on older (collapsible) entries
                if (!isLatest) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Change list — animated expand/collapse ──────────────────────────
            // expandVertically/shrinkVertically clips from the top for a natural
            // accordion feel. 200ms is snappy without feeling rushed.
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(200)),
                exit  = shrinkVertically(tween(160))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    entry.changes.forEach { change ->
                        ChangelogRow(type = change.type, description = change.description)
                    }
                }
            }
        }
    }
}

/**
 * One change item: coloured dot + type label + description.
 */
@Composable
private fun ChangelogRow(type: ChangeType, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
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
    //ChangeType.IMPROVED -> MaterialTheme.colorScheme.secondary #00BCD4
    ChangeType.IMPROVED -> Color(0xFF7C4DFF)
    ChangeType.FIXED    -> Color(0xFF00C853)//Color(0xFF4CAF50)
    ChangeType.SECURITY -> Color(0xFFFFAB00)//Color(0xFFFF9800)
}