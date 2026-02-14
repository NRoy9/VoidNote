package com.greenicephoenix.voidnote.presentation.editor

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.components.EditableTagChip
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.domain.model.FormatType
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

/**
 * Note Editor Screen
 *
 * ✅ NATIVE text selection (double-tap, long-press work)
 * ✅ Proper cursor positioning
 * ✅ MS Word-style formatting toolbar
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHeadingMenu by remember { mutableStateOf(false) }

    // Text field states
    var titleFieldValue by remember {
        mutableStateOf(TextFieldValue(
            text = uiState.title,
            selection = TextRange(uiState.title.length)
        ))
    }

    var contentFieldValue by remember {
        mutableStateOf(TextFieldValue(
            text = uiState.content,
            selection = TextRange(uiState.content.length)
        ))
    }

    // Update from ViewModel
    LaunchedEffect(uiState.title) {
        if (titleFieldValue.text != uiState.title) {
            titleFieldValue = titleFieldValue.copy(text = uiState.title)
        }
    }

    LaunchedEffect(uiState.content) {
        if (contentFieldValue.text != uiState.content) {
            contentFieldValue = contentFieldValue.copy(text = uiState.content)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.forceSave() }
    }

    val hasSelection = contentFieldValue.selection.start != contentFieldValue.selection.end

    Scaffold(
        topBar = {
            TopBar(
                onBackClick = onNavigateBack,
                isPinned = uiState.isPinned,
                onPinClick = { viewModel.togglePin() },
                onDeleteClick = { showDeleteDialog = true },
                onShareClick = {
                    shareNote(
                        context,
                        uiState.title.ifBlank { "Untitled" },
                        uiState.content,
                        uiState.tags
                    )
                },
                lastSaved = uiState.lastSaved
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .imePadding()  // ✅ Add this - pushes bar above keyboard
                    .navigationBarsPadding()
            ) {
                // Tags ABOVE formatting bar
                TagsSection(
                    tags = uiState.tags,
                    onAddTag = { viewModel.addTag(it) },
                    onRemoveTag = { viewModel.removeTag(it) }
                )

                // Formatting toolbar
                FormattingToolbar(
                    isBoldActive = if (hasSelection) {
                        hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.BOLD)
                    } else {
                        uiState.activeBold
                    },
                    isItalicActive = if (hasSelection) {
                        hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.ITALIC)
                    } else {
                        uiState.activeItalic
                    },
                    isUnderlineActive = if (hasSelection) {
                        hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.UNDERLINE)
                    } else {
                        uiState.activeUnderline
                    },
                    activeHeading = uiState.activeHeading,
                    hasSelection = hasSelection,
                    onBoldClick = {
                        if (hasSelection) {
                            viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.BOLD
                            )
                        } else {
                            viewModel.toggleActiveBold()
                        }
                    },
                    onItalicClick = {
                        if (hasSelection) {
                            viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.ITALIC
                            )
                        } else {
                            viewModel.toggleActiveItalic()
                        }
                    },
                    onUnderlineClick = {
                        if (hasSelection) {
                            viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.UNDERLINE
                            )
                        } else {
                            viewModel.toggleActiveUnderline()
                        }
                    },
                    onHeadingClick = { showHeadingMenu = true },
                    onClearClick = { viewModel.clearAllFormatting() },
                    wordCount = contentFieldValue.text.split("\\s+".toRegex())
                        .filter { it.isNotBlank() }.size,
                    charCount = contentFieldValue.text.length
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.medium)
        ) {
            Spacer(Modifier.height(Spacing.small))

            // Title
            RichTextEditor(
                value = titleFieldValue,
                onValueChange = { newValue ->
                    if (newValue.text.length <= 100) {
                        titleFieldValue = newValue
                        viewModel.onTitleChange(newValue.text)
                    }
                },
                placeholder = "Note title",
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.medium))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Spacer(Modifier.height(Spacing.medium))

            // Content with formatting
            RichTextEditor(
                value = contentFieldValue,
                onValueChange = { newValue ->
                    contentFieldValue = newValue
                    viewModel.onContentChange(newValue.text)
                },
                placeholder = "Start writing...",
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                formats = uiState.contentFormats,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 400.dp)
            )
            /* Content with formatting + double-tap detection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                // Find word boundaries at tap position
                                val text = contentFieldValue.text
                                if (text.isEmpty()) return@detectTapGestures

                                // Convert offset to text position (approximate)
                                val position = (offset.x / 10).toInt().coerceIn(0, text.length)

                                // Find word start
                                var start = position
                                while (start > 0 && text[start - 1].isLetterOrDigit()) {
                                    start--
                                }

                                // Find word end
                                var end = position
                                while (end < text.length && text[end].isLetterOrDigit()) {
                                    end++
                                }

                                // Select the word
                                if (start < end) {
                                    contentFieldValue = contentFieldValue.copy(
                                        selection = androidx.compose.ui.text.TextRange(start, end)
                                    )
                                }
                            }
                        )
                    }
            ) {
                RichTextEditor(
                    value = contentFieldValue,
                    onValueChange = { newValue ->
                        contentFieldValue = newValue
                        viewModel.onContentChange(newValue.text)
                    },
                    placeholder = "Start writing...",
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    formats = uiState.contentFormats,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 400.dp)
                )
            }
            */

            Spacer(Modifier.height(200.dp))
        }

        // Heading size menu - positioned better
        if (showHeadingMenu) {
            // ✅ Use AlertDialog instead of DropdownMenu for better keyboard handling
            AlertDialog(
                onDismissRequest = { showHeadingMenu = false },
                title = { Text("Text Size") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Small
                        Surface(
                            onClick = {
                                if (hasSelection) {
                                    viewModel.applyFormatting(
                                        contentFieldValue.selection.start,
                                        contentFieldValue.selection.end,
                                        FormatType.HEADING_SMALL
                                    )
                                } else {
                                    viewModel.setActiveHeading(FormatType.HEADING_SMALL)
                                }
                                showHeadingMenu = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "Small",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        // Normal
                        Surface(
                            onClick = {
                                if (hasSelection) {
                                    viewModel.applyFormatting(
                                        contentFieldValue.selection.start,
                                        contentFieldValue.selection.end,
                                        FormatType.HEADING_NORMAL
                                    )
                                } else {
                                    viewModel.setActiveHeading(FormatType.HEADING_NORMAL)
                                }
                                showHeadingMenu = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Normal (Default)",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        // Large
                        Surface(
                            onClick = {
                                if (hasSelection) {
                                    viewModel.applyFormatting(
                                        contentFieldValue.selection.start,
                                        contentFieldValue.selection.end,
                                        FormatType.HEADING_LARGE
                                    )
                                } else {
                                    viewModel.setActiveHeading(FormatType.HEADING_LARGE)
                                }
                                showHeadingMenu = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "Large",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showHeadingMenu = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Note?") },
            text = { Text("\"${uiState.title.ifBlank { "Untitled" }}\" will be moved to trash.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote()
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * ✅ Formatting Toolbar with heading sizes
 */
@Composable
private fun FormattingToolbar(
    isBoldActive: Boolean,
    isItalicActive: Boolean,
    isUnderlineActive: Boolean,
    activeHeading: FormatType?,
    hasSelection: Boolean,
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onHeadingClick: () -> Unit,
    onClearClick: () -> Unit,
    wordCount: Int = 0,      // ✅ ADD
    charCount: Int = 0       // ✅ ADD
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,  // ✅ Better color
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.small)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ... existing buttons (Bold, Italic, etc.) ...

                    // Bold
                    FilledTonalIconButton(
                        onClick = onBoldClick,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isBoldActive)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Default.FormatBold,
                            "Bold",
                            tint = if (isBoldActive)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Italic
                    FilledTonalIconButton(
                        onClick = onItalicClick,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isItalicActive)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Default.FormatItalic,
                            "Italic",
                            tint = if (isItalicActive)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Underline
                    FilledTonalIconButton(
                        onClick = onUnderlineClick,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isUnderlineActive)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Default.FormatUnderlined,
                            "Underline",
                            tint = if (isUnderlineActive)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.height(40.dp).padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )

                    // Heading sizes
                    FilledTonalIconButton(
                        onClick = onHeadingClick,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (activeHeading != null)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Default.FormatSize,
                            "Text size",
                            tint = if (activeHeading != null)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Clear formatting
                    FilledTonalIconButton(
                        onClick = onClearClick,
                        enabled = hasSelection
                    ) {
                        Icon(Icons.Default.FormatClear, "Clear")
                    }
                }

                // ✅ Word & character count
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = Spacing.small)
                ) {
                    Text(
                        text = "$wordCount words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$charCount chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Top App Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBackClick: () -> Unit,
    isPinned: Boolean,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    lastSaved: Long
) {
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
                        text = formatTime(lastSaved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(if (isPinned) "Unpin" else "Pin to top")
                            }
                        },
                        onClick = {
                            showMenu = false
                            onPinClick()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Share")
                            }
                        },
                        onClick = {
                            showMenu = false
                            onShareClick()
                        }
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Delete",
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
 * Tags Section (above formatting bar)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                tags.forEach { tag ->
                    EditableTagChip(
                        tag = tag,
                        onRemove = { onRemoveTag(tag) }
                    )
                }

                if (tags.size < 5) {
                    Surface(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
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
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                "Add tag",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var tagName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Tag") },
            text = {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = {
                        if (it.length <= 20 && it.all { c -> c.isLetterOrDigit() || c.isWhitespace() }) {
                            tagName = it
                        }
                    },
                    label = { Text("Tag name") },
                    singleLine = true,
                    supportingText = { Text("${tagName.length}/20") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddTag(tagName.trim())
                        showAddDialog = false
                    },
                    enabled = tagName.trim().isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Share note
 */
private fun shareNote(
    context: android.content.Context,
    title: String,
    content: String,
    tags: List<String>
) {
    val text = buildString {
        appendLine(title)
        appendLine()
        if (tags.isNotEmpty()) {
            appendLine("Tags: ${tags.joinToString(", ")}")
            appendLine()
        }
        append(content)
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }

    context.startActivity(Intent.createChooser(intent, "Share note"))
}

/**
 * Format time
 */
private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}