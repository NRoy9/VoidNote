package com.greenicephoenix.voidnote.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.components.FolderNotesEmptyState
import com.greenicephoenix.voidnote.presentation.components.NoteCard
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * FolderNotesScreen — shows all notes inside a specific folder.
 *
 * SPRINT 3 — all folder menu actions working:
 * - Rename: live update of the top bar title (via observeFolder Flow)
 * - Delete: notes go to trash (recoverable), folder is removed
 *
 * The delete dialog no longer has a checkbox. The behaviour is always:
 * notes → trash. This is the only safe and correct default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderNotesScreen(
    folderId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    viewModel: FolderNotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showRenameDialog by viewModel.showRenameDialog.collectAsState()
    val renameText by viewModel.renameText.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(folderId) {
        viewModel.loadFolder(folderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.folderName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${uiState.notes.size} ${if (uiState.notes.size == 1) "note" else "notes"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename Folder") },
                                leadingIcon = {
                                    Icon(Icons.Default.DriveFileRenameOutline, null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openRenameDialog()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text("Delete Folder", color = MaterialTheme.colorScheme.error)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openDeleteDialog()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createNoteInFolder(folderId, onNavigateToEditor) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create note in folder")
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.notes.isEmpty() -> {
                    FolderNotesEmptyState(
                        folderName = uiState.folderName,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    FolderNotesContent(
                        notes = uiState.notes,
                        onNoteClick = { note -> onNavigateToEditor(note.id) }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameFolderDialog(
            currentName = renameText,
            onNameChange = { viewModel.onRenameTextChange(it) },
            onConfirm = { viewModel.confirmRename() },
            onDismiss = { viewModel.dismissRenameDialog() }
        )
    }

    if (showDeleteDialog) {
        DeleteFolderDialog(
            folderName = uiState.folderName,
            noteCount = uiState.notes.size,
            onConfirm = { viewModel.confirmDelete(onNavigateBack) },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTE LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderNotesContent(
    notes: List<com.greenicephoenix.voidnote.domain.model.Note>,
    onNoteClick: (com.greenicephoenix.voidnote.domain.model.Note) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        items(notes, key = { it.id }) { note ->
            NoteCard(note = note, onClick = { onNoteClick(note) })
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RenameFolderDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
        title = { Text("Rename Folder") },
        text = {
            OutlinedTextField(
                value = currentName,
                onValueChange = onNameChange,
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = currentName.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * DeleteFolderDialog — confirms folder deletion.
 *
 * BEHAVIOUR IS ALWAYS: notes go to trash.
 * No checkbox. No choice about what happens to notes.
 *
 * WHY NO CHOICE?
 * "Permanently delete all N notes" as an option in a folder-delete dialog
 * is a data-loss trap. Users mentally model "delete folder" as deleting
 * the container, not the contents. Trash is the correct intermediate step —
 * it gives users 30 days to realise they made a mistake. Permanent delete
 * from here, with no recovery path, is an irreversible action that users
 * would not expect.
 *
 * If the user genuinely wants to permanently delete notes, the correct flow
 * is: go to TrashScreen → delete from there. That extra step is intentional
 * friction that prevents accidents.
 *
 * The dialog message tells users exactly what will happen so there are no
 * surprises. If the folder is empty, the message is simplified.
 */
@Composable
private fun DeleteFolderDialog(
    folderName: String,
    noteCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val bodyText = when {
        noteCount == 0 ->
            "\"$folderName\" is empty and will be deleted."
        noteCount == 1 ->
            "\"$folderName\" will be deleted. The 1 note inside will be moved to Trash — you can restore it from there."
        else ->
            "\"$folderName\" will be deleted. The $noteCount notes inside will be moved to Trash — you can restore them from there."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Folder?") },
        text = { Text(bodyText) },
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}