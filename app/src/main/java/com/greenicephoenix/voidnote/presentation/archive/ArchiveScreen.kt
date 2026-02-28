package com.greenicephoenix.voidnote.presentation.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import com.greenicephoenix.voidnote.presentation.components.ArchiveEmptyState

/**
 * Archive Screen — View and manage archived notes.
 *
 * WHAT IS THE ARCHIVE?
 * Notes the user wants to keep but not see every day.
 * Think of it as a filing cabinet — out of sight, always accessible,
 * never auto-deleted (unlike Trash which deletes after 30 days).
 *
 * ACTIONS AVAILABLE ON EACH NOTE:
 * - Unarchive (restore to main list)  ← primary action
 * - Move to trash                     ← secondary, destructive
 *
 * Notes in archive cannot be edited directly — user must unarchive first.
 * This is intentional: archived = "done with this, just keeping it".
 *
 * DESIGN:
 * Follows the same Nothing aesthetic as TrashScreen.
 * Two-button action row per note (Restore | Delete) using OutlinedButtons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {},   // tap card → open in editor
    viewModel: ArchiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Confirmation dialog state — holds the note pending a "move to trash" action
    var noteToTrash by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            ArchiveTopBar(
                noteCount = uiState.count,
                onBackClick = onNavigateBack
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

                uiState.isEmpty -> {
                    ArchiveEmptyState(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    ArchiveContent(
                        notes = uiState.archivedNotes,
                        onNoteClick = { note -> onNavigateToEditor(note.id) },
                        onRestoreClick = { note -> viewModel.restoreNote(note.id) },
                        onTrashClick = { note -> noteToTrash = note }
                    )
                }
            }
        }
    }

    // Confirmation dialog before moving to trash
    noteToTrash?.let { note ->
        MoveToTrashDialog(
            note = note,
            onConfirm = {
                viewModel.moveToTrash(note.id)
                noteToTrash = null
            },
            onDismiss = { noteToTrash = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveTopBar(
    noteCount: Int,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Archive",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CONTENT LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ArchiveContent(
    notes: List<Note>,
    onNoteClick: (Note) -> Unit,
    onRestoreClick: (Note) -> Unit,
    onTrashClick: (Note) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        items(notes, key = { it.id }) { note ->
            ArchiveNoteCard(
                note = note,
                onNoteClick = { onNoteClick(note) },
                onRestoreClick = { onRestoreClick(note) },
                onTrashClick = { onTrashClick(note) }
            )
        }
        item { Spacer(modifier = Modifier.height(Spacing.large)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ARCHIVE NOTE CARD
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Card for a single archived note.
 *
 * Layout:
 *   Title
 *   Content preview (up to 2 lines, markers stripped)
 *   [ ↑ Unarchive ]  [ 🗑 Delete ]
 *
 * The Unarchive button is the primary action — it's on the left, uses the
 * default outlined style. Delete is secondary — on the right, error color.
 */
@Composable
private fun ArchiveNoteCard(
    note: Note,
    onNoteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onTrashClick: () -> Unit
) {
    Card(
        onClick = onNoteClick,  // tap anywhere on card → read/edit the note
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Title
            Text(
                text = note.title.ifBlank { "Untitled Note" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Content preview — always use getContentPreview() to strip markers
            val preview = note.getContentPreview(120)
            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.extraSmall))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Checklist badge for notes with blocks
            if (note.hasChecklists()) {
                Spacer(modifier = Modifier.height(Spacing.extraSmall))
                Text(
                    text = "☑ ${note.checklistBlockCount()} ${if (note.checklistBlockCount() == 1) "checklist" else "checklists"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.extraSmall))

            // "ARCHIVED" label — subtle hint so user knows this is the archive context
            Text(
                text = "ARCHIVED",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    fontSize = 9.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Unarchive — primary action, left
                OutlinedButton(
                    onClick = onRestoreClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Unarchive,
                        contentDescription = "Unarchive",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unarchive")
                }

                // Move to trash — secondary, destructive, right
                OutlinedButton(
                    onClick = onTrashClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Move to trash",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MoveToTrashDialog(
    note: Note,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text("Move to Trash?") },
        text = {
            Text(
                "\"${note.title.ifBlank { "Untitled Note" }}\" will be moved to trash " +
                        "and deleted after 30 days."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Move to Trash") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}