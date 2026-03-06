package com.greenicephoenix.voidnote.presentation.tags

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.presentation.components.NoteCard
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * TagsScreen — two-level tags browser.
 *
 * LEVEL 1 — tag list:
 *   Shows every unique tag in the vault, sorted by note count.
 *   Each row shows: # symbol · tag name · note count badge.
 *
 * LEVEL 2 — filtered notes:
 *   Shown after tapping a tag. Lists all notes carrying that tag.
 *   The top bar title changes to "#tagname".
 *
 * BACK NAVIGATION:
 *   Level 2 → Level 1 : BackHandler intercepts, calls viewModel.clearTag().
 *   Level 1 → nav pop  : BackHandler is disabled, system navigates back normally.
 *
 * WHY NOT TWO SEPARATE NAV DESTINATIONS?
 *   Pushing a second destination for filtered notes would add entries to the
 *   back stack. The user would need two back presses to exit (first to tag list,
 *   then to previous screen). Using BackHandler inside one destination keeps
 *   the stack clean: one back press from filtered notes → tag list;
 *   one back press from tag list → previous screen. Simpler, more intuitive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onNavigateBack : () -> Unit,
    onNoteClick    : (String) -> Unit,
    viewModel      : TagsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Only intercept back when a tag is selected (Level 2).
    // When no tag is selected (Level 1), let system back pop the nav stack.
    BackHandler(enabled = uiState.selectedTag != null) {
        viewModel.clearTag()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.selectedTag != null)
                            "#${uiState.selectedTag}"
                        else
                            "TAGS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight   = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Back arrow mirrors BackHandler logic:
                        // inside filtered view → clear tag; at tag list → pop nav
                        if (uiState.selectedTag != null) viewModel.clearTag()
                        else onNavigateBack()
                    }) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Level 2: filtered notes for the selected tag
                uiState.selectedTag != null -> {
                    FilteredNotesContent(
                        notes       = uiState.filteredNotes,
                        onNoteClick = onNoteClick
                    )
                }

                // Level 1: no tags exist yet
                uiState.allTags.isEmpty() -> {
                    TagsEmptyState(modifier = Modifier.align(Alignment.Center))
                }

                // Level 1: tag list
                else -> {
                    TagListContent(
                        tags       = uiState.allTags,
                        onTagClick = { tag -> viewModel.selectTag(tag) }
                    )
                }
            }
        }
    }
}

// ─── Level 1: Tag list ────────────────────────────────────────────────────────

@Composable
private fun TagListContent(
    tags       : List<TagWithCount>,
    onTagClick : (String) -> Unit
) {
    LazyColumn(
        modifier             = Modifier.fillMaxSize(),
        contentPadding       = PaddingValues(Spacing.medium),
        verticalArrangement  = Arrangement.spacedBy(Spacing.small)
    ) {
        item {
            Text(
                text     = "${tags.size} TAG${if (tags.size != 1) "S" else ""}",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(
                    start   = Spacing.extraSmall,
                    bottom  = Spacing.extraSmall
                )
            )
        }

        items(tags, key = { it.name }) { tagWithCount ->
            TagRow(
                tagWithCount = tagWithCount,
                onClick      = { onTagClick(tagWithCount.name) }
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TagRow(
    tagWithCount : TagWithCount,
    onClick      : () -> Unit
) {
    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape          = RoundedCornerShape(12.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier                = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
            horizontalArrangement   = Arrangement.SpaceBetween,
            verticalAlignment       = Alignment.CenterVertically
        ) {
            // Tag name with # prefix — Nothing-style minimal labeling
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "#",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
                Text(
                    text  = tagWithCount.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Note count badge — pill shape, low contrast
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text     = "${tagWithCount.noteCount}",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─── Level 2: Filtered notes ──────────────────────────────────────────────────

@Composable
private fun FilteredNotesContent(
    notes       : List<Note>,
    onNoteClick : (String) -> Unit
) {
    if (notes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = "No notes with this tag",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        return
    }

    LazyColumn(
        modifier             = Modifier.fillMaxSize(),
        contentPadding       = PaddingValues(Spacing.medium),
        verticalArrangement  = Arrangement.spacedBy(Spacing.medium)
    ) {
        item {
            Text(
                text     = "${notes.size} NOTE${if (notes.size != 1) "S" else ""}",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(
                    start  = Spacing.extraSmall,
                    bottom = Spacing.extraSmall
                )
            )
        }

        items(notes, key = { it.id }) { note ->
            NoteCard(note = note, onClick = { onNoteClick(note.id) })
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun TagsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier              = modifier.padding(Spacing.large),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(Spacing.medium)
    ) {
        Text(
            text  = "No tags yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text  = "Add tags to your notes\nto organise them here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}