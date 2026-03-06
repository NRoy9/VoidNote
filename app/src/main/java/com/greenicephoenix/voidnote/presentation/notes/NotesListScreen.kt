package com.greenicephoenix.voidnote.presentation.notes

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.domain.model.NoteSort
import com.greenicephoenix.voidnote.presentation.components.FolderCard
import com.greenicephoenix.voidnote.presentation.components.NoteCard
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import com.greenicephoenix.voidnote.presentation.components.ExpandableFab
import com.greenicephoenix.voidnote.util.UpdateInfo
import androidx.core.net.toUri

/**
 * Notes List Screen — the app's home screen.
 *
 * BACK PRESS HANDLING:
 * NotesList is the root destination — BackHandler sends the app to background
 * using moveTaskToBack(true) rather than finishing the Activity.
 *
 * SPRINT 6 ADDITIONS:
 *
 * 1. SORT BUTTON — A sort icon in the TopBar opens a dropdown menu with
 *    four sort options (last modified, date created, title A→Z, title Z→A).
 *    The selected sort is persisted to DataStore via the ViewModel.
 *
 * 2. UPDATE BANNER — When the ViewModel detects a newer version on GitHub,
 *    a dismissible banner slides in at the top of the content area (below the
 *    TopBar). The banner shows the version number and a "Download" button that
 *    opens the GitHub release page in the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onNavigateToEditor: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFolders: () -> Unit = {},
    onNavigateToFolderNotes: (String) -> Unit = {},
    onNavigateToTags: () -> Unit = {},
    onCreateFolder: () -> Unit = {},
    viewModel: NotesListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsState()
    val newFolderName by viewModel.newFolderName.collectAsState()
    val noteSort by viewModel.noteSort.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val context = LocalContext.current

    // ── Back press: minimise app instead of finishing the Activity ────────────
    BackHandler {
        (context as? Activity)?.moveTaskToBack(true)
    }

    Scaffold(
        topBar = {
            NotesListTopBar(
                onSearchClick   = onNavigateToSearch,
                onSettingsClick = onNavigateToSettings,
                currentSort     = noteSort,
                onSortSelected  = { viewModel.onSortSelected(it) }
            )
        },
        floatingActionButton = {
            ExpandableFab(
                onCreateNote   = { onNavigateToEditor("new") },
                onCreateFolder = { viewModel.showCreateFolderDialog() }
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

                uiState.notes.isEmpty() && uiState.folders.isEmpty() -> {
                    // Still show the update banner even on empty state
                    Column(modifier = Modifier.fillMaxSize()) {
                        UpdateBanner(
                            updateInfo = updateInfo,
                            onDismiss  = { viewModel.onUpdateDismissed() },
                            context    = context
                        )
                        EmptyState(modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center))
                    }
                }

                else -> {
                    NotesAndFoldersContent(
                        uiState         = uiState,
                        updateInfo      = updateInfo,
                        onNoteClick     = { note -> onNavigateToEditor(note.id) },
                        onFolderClick   = { folder -> onNavigateToFolderNotes(folder.id) },
                        onTogglePin     = { noteId -> viewModel.onTogglePin(noteId) },
                        onNavigateToTags = onNavigateToTags,
                        onUpdateDismiss = { viewModel.onUpdateDismissed() },
                        context         = context
                    )
                }
            }
        }
    }

    CreateFolderDialog(
        showDialog   = showCreateFolderDialog,
        folderName   = newFolderName,
        onNameChange = { viewModel.onNewFolderNameChange(it) },
        onDismiss    = { viewModel.hideCreateFolderDialog() },
        onCreate     = { viewModel.createFolder() }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// TOP APP BAR
// ──────────────────────────────────────────────────────────────────────────────

/**
 * NotesListTopBar — home screen app bar with search, sort, and settings.
 *
 * SORT MENU:
 * Tapping the sort icon (↕) opens a DropdownMenu anchored below the icon.
 * Each item in the menu corresponds to a NoteSort value.
 * The currently active sort has a checkmark (✓) and bold text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListTopBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentSort: NoteSort,
    onSortSelected: (NoteSort) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text  = "VOID NOTE",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
        },
        actions = {
            // Search button
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }

            // Sort button + dropdown
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort notes"
                    )
                }

                DropdownMenu(
                    expanded         = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    // Section label — not tappable
                    Text(
                        text     = "SORT BY",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    NoteSort.entries.forEach { sort ->
                        val isSelected = sort == currentSort
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text       = sort.label,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            // Checkmark on the right for the active sort
                            trailingIcon = if (isSelected) {
                                { Text("✓", color = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = {
                                onSortSelected(sort)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Settings button
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// UPDATE BANNER
// ──────────────────────────────────────────────────────────────────────────────

/**
 * UpdateBanner — a dismissible banner shown when a newer version is available.
 *
 * DESIGN (Nothing aesthetic):
 * - Full-width surface with subtle elevation
 * - Monochrome with a thin left accent strip using primary color
 * - Two actions: "Download" (opens browser) and "✕" (dismiss)
 *
 * ANIMATION:
 * Slides in from the top using AnimatedVisibility with a slide + fade combo.
 * Slides out when dismissed.
 *
 * @param updateInfo  The update details, or null if no update is available.
 * @param onDismiss   Called when the user taps the ✕ button.
 * @param context     Used to open the GitHub release URL in the browser.
 */
@Composable
private fun UpdateBanner(
    updateInfo: UpdateInfo?,
    onDismiss: () -> Unit,
    context: android.content.Context
) {
    AnimatedVisibility(
        visible = updateInfo != null,
        enter   = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit    = slideOutVertically(targetOffsetY  = { -it }) + fadeOut()
    ) {
        if (updateInfo == null) return@AnimatedVisibility

        Surface(
            modifier       = Modifier.fillMaxWidth(),
            color          = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: version info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Update available",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text  = "v${updateInfo.latestVersion} is ready to download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.small))

                // Download button — opens GitHub release page in the browser
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, updateInfo.downloadUrl.toUri())
                            context.startActivity(intent)
                        } catch (_: Exception) { /* Browser not available — ignore */ }
                    }
                ) {
                    Icon(
                        imageVector        = Icons.Default.Download,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Get it")
                }

                // Dismiss button
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Dismiss update banner",
                        modifier           = Modifier.size(16.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// NOTES + FOLDERS CONTENT
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotesAndFoldersContent(
    uiState: NotesListUiState,
    updateInfo: UpdateInfo?,
    onNoteClick: (com.greenicephoenix.voidnote.domain.model.Note) -> Unit,
    onFolderClick: (com.greenicephoenix.voidnote.domain.model.Folder) -> Unit,
    onTogglePin: (String) -> Unit,
    onNavigateToTags: () -> Unit = {},
    onUpdateDismiss: () -> Unit,
    context: android.content.Context
) {
    // Notes are pre-sorted by NotesListViewModel — pinned first within each group
    val pinnedNotes  = uiState.notes.filter {  it.isPinned && !it.isTrashed }
    val regularNotes = uiState.notes.filter { !it.isPinned && !it.isTrashed }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(bottom = 80.dp),   // Space for FAB
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Update banner (first item if visible) ─────────────────────────────
        item(key = "update_banner") {
            UpdateBanner(
                updateInfo = updateInfo,
                onDismiss  = onUpdateDismiss,
                context    = context
            )
        }

        // ── Padding between banner and content ────────────────────────────────
        item { Spacer(Modifier.height(Spacing.medium)) }

        if (pinnedNotes.isNotEmpty()) {
            item(key = "pinned_header") { SectionHeader("PINNED") }
            items(pinnedNotes, key = { "pin_${it.id}" }) { note ->
                Box(modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall)) {
                    NoteCard(note = note, onClick = { onNoteClick(note) })
                }
            }
            item(key = "pinned_spacer") { Spacer(Modifier.height(Spacing.small)) }
        }

        if (uiState.folders.isNotEmpty()) {
            item(key = "folders_header") { SectionHeader("FOLDERS") }
            items(uiState.folders, key = { "folder_${it.id}" }) { folder ->
                Box(modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall)) {
                    FolderCard(
                        folder    = folder,
                        noteCount = uiState.folderNoteCounts[folder.id] ?: 0,
                        onClick   = { onFolderClick(folder) }
                    )
                }
            }
            item(key = "folders_spacer") { Spacer(Modifier.height(Spacing.small)) }
        }

        // Tags entry point — always visible
        item(key = "tags_header")    { SectionHeader("TAGS") }
        item(key = "tags_entry_row") {
            Box(modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall)) {
                TagsEntryRow(onClick = onNavigateToTags)
            }
        }
        item(key = "tags_spacer") { Spacer(Modifier.height(Spacing.small)) }

        if (regularNotes.isNotEmpty()) {
            if (pinnedNotes.isNotEmpty() || uiState.folders.isNotEmpty()) {
                item(key = "notes_header") { SectionHeader("NOTES") }
            }
            items(regularNotes, key = { "note_${it.id}" }) { note ->
                Box(modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall)) {
                    NoteCard(note = note, onClick = { onNoteClick(note) })
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// SHARED COMPOSABLES
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(
            start  = Spacing.medium + Spacing.small,
            bottom = Spacing.extraSmall,
            top    = Spacing.extraSmall
        )
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier              = modifier.padding(Spacing.large),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(Spacing.medium)
    ) {
        Text(
            text  = "Nothing here yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text  = "Tap + to create a note\nTap 📁 to create a folder",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun TagsEntryRow(onClick: () -> Unit) {
    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape          = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text  = "Browse all tags",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier           = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CreateFolderDialog(
    showDialog: Boolean,
    folderName: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value         = folderName,
                    onValueChange = onNameChange,
                    label         = { Text("Folder name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = onCreate, enabled = folderName.isNotBlank()) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}