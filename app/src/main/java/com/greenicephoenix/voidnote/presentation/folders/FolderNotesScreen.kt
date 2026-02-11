package com.greenicephoenix.voidnote.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.components.NoteCard
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * Folder Notes Screen - Shows notes inside a specific folder
 *
 * Features:
 * - Display folder name in top bar
 * - List all notes in this folder
 * - Create new note in this folder
 * - Navigate to note editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderNotesScreen(
    folderId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit, // Simple: just noteId
    viewModel: FolderNotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load folder when screen appears
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Folder options menu */ }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
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
                onClick = {
                    viewModel.createNoteInFolder(folderId, onNavigateToEditor)
                },
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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.notes.isEmpty() -> {
                    EmptyFolderState(
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
}

/**
 * List of notes in folder
 */
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

        // Bottom spacing for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * Empty folder state
 */
@Composable
private fun EmptyFolderState(
    folderName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Text(
            text = "No notes in $folderName",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = "Tap + to create a note here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}