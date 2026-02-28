package com.greenicephoenix.voidnote.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.presentation.components.FoldersEmptyState
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * FoldersScreen — Lists all folders. Allows creating and deleting folders.
 *
 * SPRINT 3 CHANGE:
 * Tapping the delete icon on a folder now opens a confirmation dialog
 * (DeleteFolderConfirmDialog) before proceeding. The dialog tells the user:
 * - Which folder will be deleted
 * - That their notes will be moved to the main list (not lost)
 *
 * Previously the trash icon called deleteFolder() immediately with no
 * confirmation and no note-rescue logic — notes inside became orphaned.
 *
 * HOW THE CONFIRMATION WORKS:
 * - User taps trash icon → viewModel.requestDeleteFolder(folder) is called
 * - This sets pendingDeleteFolder to that folder in the ViewModel
 * - The screen observes pendingDeleteFolder — when non-null, shows the dialog
 * - Confirm → viewModel.confirmDeleteFolder() (moves notes + deletes folder)
 * - Cancel → viewModel.cancelDeleteFolder() (clears pendingDeleteFolder, dialog closes)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onNavigateBack: () -> Unit,
    onFolderClick: (String) -> Unit = {},
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showDialog by viewModel.showCreateDialog.collectAsState()
    val newFolderName by viewModel.newFolderName.collectAsState()

    // pendingDeleteFolder: non-null means "show delete confirmation for this folder"
    // null means no confirmation dialog is showing
    val pendingDeleteFolder by viewModel.pendingDeleteFolder.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create folder")
            }
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

                uiState.folders.isEmpty() -> {
                    // Polished empty state from EmptyStateView.kt (Sprint 2)
                    FoldersEmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    FoldersListContent(
                        folders = uiState.folders,
                        onFolderClick = onFolderClick,
                        // SPRINT 3: request confirmation instead of deleting directly
                        onDeleteFolder = { folder -> viewModel.requestDeleteFolder(folder) }
                    )
                }
            }
        }
    }

    // ── Create Folder Dialog ──────────────────────────────────────────────
    if (showDialog) {
        CreateFolderDialog(
            folderName = newFolderName,
            onNameChange = { viewModel.onFolderNameChange(it) },
            onDismiss = { viewModel.hideCreateDialog() },
            onCreate = { viewModel.createFolder() }
        )
    }

    // ── Delete Confirmation Dialog ────────────────────────────────────────
    // Only shown when pendingDeleteFolder is non-null (user tapped a trash icon)
    pendingDeleteFolder?.let { folder ->
        DeleteFolderConfirmDialog(
            folder = folder,
            onConfirm = { viewModel.confirmDeleteFolder() },
            onDismiss = { viewModel.cancelDeleteFolder() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOLDER LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FoldersListContent(
    folders: List<Folder>,
    onFolderClick: (String) -> Unit,
    onDeleteFolder: (Folder) -> Unit       // SPRINT 3: takes whole Folder, not just ID
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        items(folders, key = { it.id }) { folder ->
            FolderListItem(
                folder = folder,
                onClick = { onFolderClick(folder.id) },
                onDelete = { onDeleteFolder(folder) }
            )
        }
    }
}

@Composable
private fun FolderListItem(
    folder: Folder,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Trash icon — tapping opens confirmation dialog (not immediate delete)
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete folder",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * DeleteFolderConfirmDialog — shown before any folder deletion from the list.
 *
 * Tells the user:
 * - The folder name
 * - That notes will be moved (not deleted)
 *
 * This prevents accidental deletions and avoids panic about lost notes.
 */
@Composable
private fun DeleteFolderConfirmDialog(
    folder: Folder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete \"${folder.name}\"?") },
        text = {
            Text("This folder will be deleted. Any notes inside will be moved to your main notes list — nothing will be lost.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * CreateFolderDialog — inline dialog for naming a new folder.
 */
@Composable
private fun CreateFolderDialog(
    folderName: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
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