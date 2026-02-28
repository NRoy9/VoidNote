package com.greenicephoenix.voidnote.presentation.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.components.FolderCard
import com.greenicephoenix.voidnote.presentation.components.NoteCard
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import com.greenicephoenix.voidnote.presentation.components.ExpandableFab
import com.greenicephoenix.voidnote.presentation.components.NotesEmptyState

/**
 * Notes List Screen - Main screen of the app
 *
 * NOW SHOWS FOLDERS AND NOTES TOGETHER!
 *
 * Layout:
 * - PINNED notes (if any)
 * - FOLDERS (if any)
 * - NOTES (root level notes without folders)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onNavigateToEditor: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFolders: () -> Unit = {},
    onNavigateToFolderNotes: (String) -> Unit = {},
    onCreateFolder: () -> Unit = {},
    viewModel: NotesListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsState()
    val newFolderName by viewModel.newFolderName.collectAsState()

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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.notes.isEmpty() && uiState.folders.isEmpty() -> {
                    NotesEmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    NotesAndFoldersContent(
                        uiState = uiState,
                        onNoteClick = { note -> onNavigateToEditor(note.id) },
                        onFolderClick = { folder -> onNavigateToFolderNotes(folder.id) },
                        onTogglePin = { noteId -> viewModel.onTogglePin(noteId) }
                    )
                }
            }
        }
    }

    // Create Folder Dialog
    CreateFolderDialog(
        showDialog = showCreateFolderDialog,
        folderName = newFolderName,
        onNameChange = { viewModel.onNewFolderNameChange(it) },
        onDismiss = { viewModel.hideCreateFolderDialog() },
        onCreate = { viewModel.createFolder() }
    )
}

/**
 * Top App Bar - Simplified (removed Folders icon, added Settings)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListTopBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "VOID NOTE",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

/**
 * Main content - Shows folders and notes together
 */
@Composable
private fun NotesAndFoldersContent(
    uiState: NotesListUiState,
    onNoteClick: (com.greenicephoenix.voidnote.domain.model.Note) -> Unit,
    onFolderClick: (com.greenicephoenix.voidnote.domain.model.Folder) -> Unit,
    onTogglePin: (String) -> Unit
) {
    // Separate pinned and regular notes
    val pinnedNotes = uiState.notes.filter { it.isPinned && !it.isTrashed && !it.isArchived }
    val regularNotes = uiState.notes.filter { !it.isPinned && !it.isTrashed && !it.isArchived }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        // PINNED NOTES SECTION
        if (pinnedNotes.isNotEmpty()) {
            item {
                SectionHeader(text = "PINNED")
            }

            items(pinnedNotes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onNoteClick(note) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.small))
            }
        }

        // FOLDERS SECTION
        if (uiState.folders.isNotEmpty()) {
            item {
                SectionHeader(text = "FOLDERS")
            }

            items(uiState.folders, key = { it.id }) { folder ->
                FolderCard(
                    folder = folder,
                    noteCount = uiState.folderNoteCounts[folder.id] ?: 0,
                    onClick = { onFolderClick(folder) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.small))
            }
        }

        // NOTES SECTION (root level notes)
        if (regularNotes.isNotEmpty()) {
            if (pinnedNotes.isNotEmpty() || uiState.folders.isNotEmpty()) {
                item {
                    SectionHeader(text = "NOTES")
                }
            }

            items(regularNotes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onNoteClick(note) }
                )
            }
        }

        // Bottom spacing for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * Section Header Component
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = Spacing.small, bottom = Spacing.extraSmall)
    )
}

/**
 * Create Folder Dialog - Inline on Notes List Screen
 */
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
                    value = folderName,
                    onValueChange = onNameChange,
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onCreate,
                    enabled = folderName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}