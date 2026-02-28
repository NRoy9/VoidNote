package com.greenicephoenix.voidnote.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * SPRINT 3 FIXES applied here:
 *
 * Fix #1 — Live rename:
 * The top bar title now updates instantly after rename because the ViewModel
 * uses combine(observeFolder, getNotesByFolder). No code change needed in this
 * screen for fix #1 — the ViewModel handles it. The title just reads
 * uiState.folderName which is now always current.
 *
 * Fix #2 — Delete with note choice:
 * DeleteFolderDialog now contains a checkbox "Also permanently delete all notes".
 * - Unchecked (default): notes moved to main list — nothing lost
 * - Checked: notes permanently deleted along with the folder
 * The checkbox state is local to the dialog (it resets each time the dialog opens).
 * confirmDelete(deleteNotes, onNavigateBack) passes the choice to the ViewModel.
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

    // Local UI-only state — controls the ⋮ dropdown visibility
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(folderId) {
        viewModel.loadFolder(folderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // uiState.folderName now updates live after rename
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Folder options"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename Folder") },
                                leadingIcon = {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openRenameDialog()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete Folder",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
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

    // ── Rename Dialog ─────────────────────────────────────────────────────
    if (showRenameDialog) {
        RenameFolderDialog(
            currentName = renameText,
            onNameChange = { viewModel.onRenameTextChange(it) },
            onConfirm = { viewModel.confirmRename() },
            onDismiss = { viewModel.dismissRenameDialog() }
        )
    }

    // ── Delete Dialog ─────────────────────────────────────────────────────
    if (showDeleteDialog) {
        DeleteFolderDialog(
            folderName = uiState.folderName,
            noteCount = uiState.notes.size,
            onConfirm = { deleteNotes ->
                viewModel.confirmDelete(
                    deleteNotes = deleteNotes,
                    onNavigateBack = onNavigateBack
                )
            },
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
            NoteCard(
                note = note,
                onClick = { onNoteClick(note) }
            )
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
        icon = {
            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
        },
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
            TextButton(
                onClick = onConfirm,
                enabled = currentName.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * DeleteFolderDialog — confirms folder deletion and lets the user choose
 * what happens to notes inside.
 *
 * DESIGN DECISION — checkbox vs two buttons:
 * A checkbox ("Also delete all notes") is clearer than two confirm buttons
 * ("Delete folder only" vs "Delete folder and notes"). With two buttons,
 * users often miss the distinction. A checkbox makes the default (safe)
 * behaviour obvious and the destructive option opt-in.
 *
 * The checkbox state is local to this composable — it resets to false every
 * time the dialog opens, which is the right default (safe > destructive).
 *
 * When noteCount is 0, the checkbox is hidden — there are no notes to
 * act on, so the choice is meaningless.
 *
 * @param folderName  Name shown in the dialog title
 * @param noteCount   Number of notes currently in the folder
 * @param onConfirm   Called with deleteNotes=true or false based on checkbox
 * @param onDismiss   Called when user cancels
 */
@Composable
private fun DeleteFolderDialog(
    folderName: String,
    noteCount: Int,
    onConfirm: (deleteNotes: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // Checkbox state — local because it resets every time the dialog opens.
    // Defaults to false = safe behaviour (move notes, don't delete them).
    var deleteNotesChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete \"$folderName\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {

                // Main explanation — always shown
                if (noteCount > 0) {
                    val noteWord = if (noteCount == 1) "note" else "notes"
                    Text("This folder contains $noteCount $noteWord. Choose what to do with them:")
                } else {
                    Text("This folder is empty and will be deleted.")
                }

                // Checkbox — only shown when there are notes to act on
                if (noteCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = deleteNotesChecked,
                            onCheckedChange = { deleteNotesChecked = it },
                            // Red when checked — signals destructive action
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.error
                            )
                        )
                        // Tapping the label also toggles the checkbox
                        // (standard accessibility pattern)
                        Text(
                            text = "Also permanently delete all $noteCount ${if (noteCount == 1) "note" else "notes"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (deleteNotesChecked)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                // Make the text tappable too
                                .then(
                                    Modifier.clickable(
                                        indication = null,
                                        interactionSource = remember {
                                            androidx.compose.foundation.interaction.MutableInteractionSource()
                                        }
                                    ) { deleteNotesChecked = !deleteNotesChecked }
                                )
                        )
                    }

                    // Contextual explanation — updates based on checkbox state
                    Text(
                        text = if (deleteNotesChecked)
                            "⚠ Notes will be permanently deleted. This cannot be undone."
                        else
                            "Notes will be moved to your main notes list — nothing will be lost.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deleteNotesChecked)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(deleteNotesChecked) },
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