package com.greenicephoenix.voidnote.presentation.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * Trash Screen - View and manage deleted notes
 *
 * Features:
 * - List all trashed notes
 * - Restore notes (undo delete)
 * - Permanently delete notes
 * - Empty entire trash
 * - Empty state with helpful message
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Dialog state for empty trash confirmation
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    // Dialog state for permanent delete confirmation
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TrashTopBar(
                noteCount = uiState.count,
                onBackClick = onNavigateBack,
                onEmptyTrashClick = { showEmptyTrashDialog = true },
                isEmpty = uiState.isEmpty
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
                    // Loading state
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.isEmpty -> {
                    // Empty trash state
                    EmptyTrashState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    // List of trashed notes
                    TrashNotesContent(
                        notes = uiState.trashedNotes,
                        onRestoreClick = { note ->
                            viewModel.restoreNote(note.id)
                        },
                        onDeleteClick = { note ->
                            noteToDelete = note
                        }
                    )
                }
            }

            // Loading overlay when emptying trash
            if (uiState.isEmptyingTrash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Empty trash confirmation dialog
    if (showEmptyTrashDialog) {
        EmptyTrashDialog(
            noteCount = uiState.count,
            onConfirm = {
                viewModel.emptyTrash()
                showEmptyTrashDialog = false
            },
            onDismiss = { showEmptyTrashDialog = false }
        )
    }

    // Permanent delete confirmation dialog
    noteToDelete?.let { note ->
        PermanentDeleteDialog(
            note = note,
            onConfirm = {
                viewModel.permanentlyDeleteNote(note.id)
                noteToDelete = null
            },
            onDismiss = { noteToDelete = null }
        )
    }
}

/**
 * Top App Bar for Trash Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashTopBar(
    noteCount: Int,
    onBackClick: () -> Unit,
    onEmptyTrashClick: () -> Unit,
    isEmpty: Boolean
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Trash",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                if (noteCount > 0) {
                    Text(
                        text = "$noteCount ${if (noteCount == 1) "note" else "notes"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            // Empty trash button (only show if trash is not empty)
            if (!isEmpty) {
                TextButton(onClick = onEmptyTrashClick) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Empty trash",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Empty")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/**
 * List of trashed notes
 */
@Composable
private fun TrashNotesContent(
    notes: List<Note>,
    onRestoreClick: (Note) -> Unit,
    onDeleteClick: (Note) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        items(notes, key = { it.id }) { note ->
            TrashNoteCard(
                note = note,
                onRestoreClick = { onRestoreClick(note) },
                onDeleteClick = { onDeleteClick(note) }
            )
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(Spacing.large))
        }
    }
}

/**
 * Individual note card in trash
 */
@Composable
private fun TrashNoteCard(
    note: Note,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Note title
            Text(
                text = note.title.ifBlank { "Untitled Note" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Note content preview
            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.small))
                Text(
                    text = note.getContentPreview(100),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Restore button
                OutlinedButton(
                    onClick = onRestoreClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestoreFromTrash,
                        contentDescription = "Restore",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore")
                }

                // Permanent delete button
                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Forever",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

/**
 * Empty trash state
 */
@Composable
private fun EmptyTrashState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )
        Text(
            text = "Trash is empty",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = "Deleted notes will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

/**
 * Empty trash confirmation dialog
 */
@Composable
private fun EmptyTrashDialog(
    noteCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Empty Trash?") },
        text = {
            Text(
                "This will permanently delete $noteCount ${if (noteCount == 1) "note" else "notes"}. " +
                        "This action cannot be undone."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Empty Trash")
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
 * Permanent delete confirmation dialog
 */
@Composable
private fun PermanentDeleteDialog(
    note: Note,
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
        title = { Text("Delete Forever?") },
        text = {
            Text(
                "\"${note.title.ifBlank { "Untitled Note" }}\" will be permanently deleted. " +
                        "This action cannot be undone."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete Forever")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}