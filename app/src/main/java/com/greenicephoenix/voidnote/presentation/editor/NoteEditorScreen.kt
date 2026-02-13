package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.DisposableEffect
import com.greenicephoenix.voidnote.presentation.components.EditableTagChip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height

/**
 * Note Editor Screen - Create and edit notes
 *
 * Features:
 * - Full-screen writing experience
 * - Rich text formatting toolbar
 * - Auto-save indicator
 * - Word/character count
 * - Minimal, distraction-free design
 * - Delete functionality (move to trash)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // State for delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Force save when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.forceSave()
        }
    }

    Scaffold(
        topBar = {
            NoteEditorTopBar(
                onBackClick = onNavigateBack,
                isPinned = uiState.isPinned,
                onPinClick = { viewModel.togglePin() },
                onDeleteClick = { showDeleteDialog = true },  // ✅ NEW: Delete button
                lastSaved = uiState.lastSaved
            )
        },
        bottomBar = {
            FormattingToolbar(
                isBoldActive = uiState.isBoldActive,
                isItalicActive = uiState.isItalicActive,
                isUnderlineActive = uiState.isUnderlineActive,
                onBoldClick = { viewModel.toggleBold() },
                onItalicClick = { viewModel.toggleItalic() },
                onUnderlineClick = { viewModel.toggleUnderline() },
                wordCount = uiState.content.split("\\s+".toRegex()).size,
                charCount = uiState.content.length
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = Spacing.medium)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(Spacing.small))

            // Title field
            BasicTextField(
                value = uiState.title,
                onValueChange = { viewModel.onTitleChange(it) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (uiState.title.isEmpty()) {
                            Text(
                                text = "Title",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // Tags Section
            TagsSection(
                tags = uiState.tags,
                onAddTag = { viewModel.addTag(it) },
                onRemoveTag = { viewModel.removeTag(it) }
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Content field
            BasicTextField(
                value = uiState.content,
                onValueChange = { viewModel.onContentChange(it) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (uiState.content.isEmpty()) {
                            Text(
                                text = "Start writing...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 400.dp)
            )

            Spacer(modifier = Modifier.height(100.dp)) // Space for toolbar
        }
    }

    // ✅ NEW: Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteNoteDialog(
            noteTitle = uiState.title.ifBlank { "Untitled Note" },
            onConfirm = {
                viewModel.deleteNote()
                showDeleteDialog = false
                onNavigateBack() // Go back after delete
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * Top App Bar for Note Editor
 * ✅ UPDATED: Added delete button in dropdown menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorTopBar(
    onBackClick: () -> Unit,
    isPinned: Boolean,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,  // ✅ NEW: Delete callback
    lastSaved: Long
) {
    // State for dropdown menu
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = if (lastSaved > 0) "Saved" else "Not saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                if (lastSaved > 0) {
                    Text(
                        text = formatLastSaved(lastSaved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
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
            // Pin button
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }

            // ✅ NEW: More options menu (three dots)
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                }

                // Dropdown menu
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Delete option
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Delete",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/**
 * Formatting Toolbar - Bottom bar with text formatting options
 */
@Composable
private fun FormattingToolbar(
    isBoldActive: Boolean,
    isItalicActive: Boolean,
    isUnderlineActive: Boolean,
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    wordCount: Int,
    charCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column {
            HorizontalDivider(
                Modifier,
                DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.small, vertical = Spacing.small)
                    // Add bottom padding for navigation bar
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Formatting buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FormattingButton(
                        icon = Icons.Default.FormatBold,
                        contentDescription = "Bold",
                        isActive = isBoldActive,
                        onClick = onBoldClick
                    )
                    FormattingButton(
                        icon = Icons.Default.FormatItalic,
                        contentDescription = "Italic",
                        isActive = isItalicActive,
                        onClick = onItalicClick
                    )
                    FormattingButton(
                        icon = Icons.Default.FormatUnderlined,
                        contentDescription = "Underline",
                        isActive = isUnderlineActive,
                        onClick = onUnderlineClick
                    )

                    // Divider
                    VerticalDivider(
                        modifier = Modifier
                            .height(24.dp)
                            .padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // Additional formatting (we'll implement these later)
                    IconButton(onClick = { /* TODO: Heading */ }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Title,
                            contentDescription = "Heading",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { /* TODO: List */ }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = "List",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { /* TODO: Checkbox */ }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.CheckBox,
                            contentDescription = "Checkbox",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Word and character count
                Text(
                    text = "$wordCount words • $charCount chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Individual formatting button
 */
@Composable
private fun FormattingButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Format last saved time
 */
private fun formatLastSaved(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            "at ${dateFormat.format(Date(timestamp))}"
        }
        else -> {
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

/**
 * Tags Section - Add and manage tags
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    var showAddTagDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Tags display
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                tags.forEach { tag ->
                    EditableTagChip(
                        tag = tag,
                        onRemove = { onRemoveTag(tag) }
                    )
                }

                // Add tag button
                AddTagButton(onClick = { showAddTagDialog = true })
            }
        } else {
            // No tags - show add button
            AddTagButton(onClick = { showAddTagDialog = true })
        }
    }

    // Add tag dialog
    if (showAddTagDialog) {
        AddTagDialog(
            onDismiss = { showAddTagDialog = false },
            onConfirm = { tag ->
                onAddTag(tag)
                showAddTagDialog = false
            }
        )
    }
}

/**
 * Add tag button
 */
@Composable
private fun AddTagButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.medium,
                vertical = Spacing.extraSmall
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add tag",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Add tag",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Add tag dialog
 */
@Composable
private fun AddTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tagName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tag") },
        text = {
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text("Tag name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tagName) },
                enabled = tagName.isNotBlank()
            ) {
                Text("Add")
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
 * ✅ NEW: Delete note confirmation dialog
 */
@Composable
private fun DeleteNoteDialog(
    noteTitle: String,
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
        title = { Text("Delete Note?") },
        text = {
            Text(
                "\"$noteTitle\" will be moved to trash. You can restore it later from the trash."
            )
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