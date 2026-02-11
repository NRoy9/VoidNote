package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect

/**
 * Note Editor Screen - Create and edit notes
 *
 * Features:
 * - Full-screen writing experience
 * - Rich text formatting toolbar
 * - Auto-save indicator
 * - Word/character count
 * - Minimal, distraction-free design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

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
}

/**
 * Top App Bar for Note Editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorTopBar(
    onBackClick: () -> Unit,
    isPinned: Boolean,
    onPinClick: () -> Unit,
    lastSaved: Long
) {
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
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = { /* TODO: More options */ }) {
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