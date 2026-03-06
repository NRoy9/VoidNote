package com.greenicephoenix.voidnote.presentation.notes

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.components.FolderCard
import com.greenicephoenix.voidnote.presentation.components.NoteCard
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import com.greenicephoenix.voidnote.presentation.components.ExpandableFab

/**
 * Notes List Screen — the app's home screen.
 *
 * BACK PRESS HANDLING:
 * NotesList is the root destination — there is nothing behind it in the
 * navigation back stack. Without interception, pressing back would call
 * Activity.finish(), which removes the Activity from the back stack.
 * The OS then considers the task empty and re-launches the app from scratch,
 * which shows the vault/lock screen again — wrong behaviour.
 *
 * BackHandler captures the system back press and calls moveTaskToBack(true)
 * instead. This sends the app to the background (like pressing the Home button)
 * while keeping the Activity alive. Pressing the app icon brings it back
 * to exactly where the user was — without triggering the lock screen again.
 *
 * This is the same behaviour used by Gmail, Google Keep, and Notion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onNavigateToEditor: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFolders: () -> Unit = {},
    onNavigateToFolderNotes: (String) -> Unit = {},
    onNavigateToTags: () -> Unit = {},      // ← ADD
    onCreateFolder: () -> Unit = {},
    viewModel: NotesListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsState()
    val newFolderName by viewModel.newFolderName.collectAsState()
    val context = LocalContext.current

    // ── Back press: minimise app instead of finishing the Activity ────────────
    // WHY: Without this, pressing back here finishes the Activity. The OS then
    // re-launches it cold (vault/lock screen appears). moveTaskToBack(true)
    // sends the whole task to the background instead — preserving state.
    BackHandler {
        (context as? Activity)?.moveTaskToBack(true)
    }

    Scaffold(
        topBar = {
            NotesListTopBar(
                onSearchClick = onNavigateToSearch,
                onSettingsClick = onNavigateToSettings
            )
        },
        floatingActionButton = {
            ExpandableFab(
                onCreateNote = { onNavigateToEditor("new") },
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

                // Empty state: no visible notes on main list AND no folders.
                // uiState.notes is already filtered to exclude archived notes
                // (fix applied in NotesListViewModel — see rootNotes filter).
                uiState.notes.isEmpty() && uiState.folders.isEmpty() -> {
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    NotesAndFoldersContent(
                        uiState         = uiState,
                        onNoteClick     = { note -> onNavigateToEditor(note.id) },
                        onFolderClick   = { folder -> onNavigateToFolderNotes(folder.id) },
                        onTogglePin     = { noteId -> viewModel.onTogglePin(noteId) },
                        onNavigateToTags = onNavigateToTags   // ← ADD
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

// ─────────────────────────────────────────────────────────────────────────────
// TOP APP BAR
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListTopBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text  = "VOID NOTE",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
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

// ─────────────────────────────────────────────────────────────────────────────
// NOTES + FOLDERS CONTENT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotesAndFoldersContent(
    uiState: NotesListUiState,
    onNoteClick: (com.greenicephoenix.voidnote.domain.model.Note) -> Unit,
    onFolderClick: (com.greenicephoenix.voidnote.domain.model.Folder) -> Unit,
    onTogglePin: (String) -> Unit,
    onNavigateToTags: () -> Unit = {}   // ← ADD
) {
    // uiState.notes already excludes archived (filtered in ViewModel).
    // We still guard against trashed here as a safety net.
    val pinnedNotes  = uiState.notes.filter {  it.isPinned && !it.isTrashed }
    val regularNotes = uiState.notes.filter { !it.isPinned && !it.isTrashed }

    LazyColumn(
        modifier         = Modifier.fillMaxSize(),
        contentPadding   = PaddingValues(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        if (pinnedNotes.isNotEmpty()) {
            item { SectionHeader("PINNED") }
            items(pinnedNotes, key = { it.id }) { note ->
                NoteCard(note = note, onClick = { onNoteClick(note) })
            }
            item { Spacer(Modifier.height(Spacing.small)) }
        }

        if (uiState.folders.isNotEmpty()) {
            item { SectionHeader("FOLDERS") }
            items(uiState.folders, key = { it.id }) { folder ->
                FolderCard(
                    folder    = folder,
                    noteCount = uiState.folderNoteCounts[folder.id] ?: 0,
                    onClick   = { onFolderClick(folder) }
                )
            }
            item { Spacer(Modifier.height(Spacing.small)) }
        }

        // Tags entry point — always shown so the user can always find it
        item {
            SectionHeader("TAGS")
        }
        item {
            TagsEntryRow(onClick = onNavigateToTags)
        }
        item { Spacer(Modifier.height(Spacing.small)) }

        if (regularNotes.isNotEmpty()) {
            if (pinnedNotes.isNotEmpty() || uiState.folders.isNotEmpty()) {
                item { SectionHeader("NOTES") }
            }
            items(regularNotes, key = { it.id }) { note ->
                NoteCard(note = note, onClick = { onNoteClick(note) })
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = Spacing.small, bottom = Spacing.extraSmall)
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
                    value        = folderName,
                    onValueChange = onNameChange,
                    label        = { Text("Folder name") },
                    singleLine   = true,
                    modifier     = Modifier.fillMaxWidth()
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