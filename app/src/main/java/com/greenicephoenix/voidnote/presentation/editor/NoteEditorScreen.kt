package com.greenicephoenix.voidnote.presentation.editor

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.domain.model.FormatType
import com.greenicephoenix.voidnote.domain.model.InlineBlockType
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

/**
 * Note Editor Screen
 *
 * ✅ Native text selection (double-tap, long-press)
 * ✅ MS Word-style formatting toolbar with TODO insert button
 * ✅ TODO blocks rendered below text with polished divider
 * ✅ Block section has "CHECKLISTS" label separator
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
    var showInsertSheet by remember { mutableStateOf(false) }  // controls insert bottom sheet

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

    // Sort blocks oldest-first for stable, predictable display order
    val sortedBlocks = remember(uiState.blocks) {
        uiState.blocks.values.sortedBy { it.createdAt }
    }

    Scaffold(
        topBar = {
            TopBar(
                onBackClick = onNavigateBack,
                isPinned = uiState.isPinned,
                isArchived = uiState.isArchived,
                onPinClick = { viewModel.togglePin() },
                onArchiveClick = { viewModel.archiveNote(); onNavigateBack() },
                onDeleteClick = { showDeleteDialog = true },
                onShareClick = {
                    shareNote(context, uiState.title.ifBlank { "Untitled" }, uiState.content, uiState.tags)
                },
                lastSaved = uiState.lastSaved
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Background must come BEFORE imePadding/navigationBarsPadding.
                    // Modifier order matters in Compose — the background fills the full
                    // column area including the space added by navigationBarsPadding(),
                    // which is the "gap" between toolbar and screen edge.
                    // Without this, that gap is transparent and shows the Scaffold's
                    // content background (#121212 dark), not surfaceVariant (#2A2A2A),
                    // causing the colour mismatch stripe visible at the bottom.
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                TagsSection(
                    tags = uiState.tags,
                    onAddTag = { viewModel.addTag(it) },
                    onRemoveTag = { viewModel.removeTag(it) }
                )

                FormattingToolbar(
                    isBoldActive = if (hasSelection) hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.BOLD)
                    else uiState.activeBold,
                    isItalicActive = if (hasSelection) hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.ITALIC)
                    else uiState.activeItalic,
                    isUnderlineActive = if (hasSelection) hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.UNDERLINE)
                    else uiState.activeUnderline,
                    isStrikethroughActive = if (hasSelection) hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.STRIKETHROUGH)
                    else uiState.activeStrikethrough,
                    activeHeading = uiState.activeHeading,
                    hasSelection = hasSelection,
                    showInsertSheet = showInsertSheet,
                    onInsertClick = { showInsertSheet = true },
                    onBoldClick = {
                        if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.BOLD)
                        else viewModel.toggleActiveBold()
                    },
                    onItalicClick = {
                        if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.ITALIC)
                        else viewModel.toggleActiveItalic()
                    },
                    onUnderlineClick = {
                        if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.UNDERLINE)
                        else viewModel.toggleActiveUnderline()
                    },
                    onStrikethroughClick = {
                        if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.STRIKETHROUGH)
                        else viewModel.toggleActiveStrikethrough()
                    },
                    onHeadingClick = { showHeadingMenu = true },
                    onClearClick = { viewModel.clearAllFormatting() },
                    onTodoClick = { viewModel.insertTodoBlock() },
                    wordCount = contentFieldValue.text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size,
                    charCount = contentFieldValue.text.length
                )

                // ── Insert Block Bottom Sheet ─────────────────────────────────
                // Rendered here (at screen level, inside the Column that wraps
                // the bottom bar) so it can expand full-width over the editor.
                // InsertBlockSheet is ALWAYS in composition — AnimatedVisibility
                // handles show/hide. Removing it from composition (if/else) would
                // lose the exit animation and cause a layout jump.
                InsertBlockSheet(
                    visible = showInsertSheet,
                    onDismiss = { showInsertSheet = false },
                    onChecklistClick = {
                        showInsertSheet = false
                        viewModel.insertTodoBlock()
                    }
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

            // ── Title ────────────────────────────────────────────────────────
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

            // ── Content Editor ───────────────────────────────────────────────
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
                    .then(
                        // When blocks exist: NO minimum height at all.
                        // The text field wraps its content naturally so the checklist
                        // appears immediately on the next line after the last character.
                        // Forcing any minimum height (even 48dp) creates the visible gap
                        // that prompted this fix — the field "reserves" empty space and
                        // the checklist floats far below the actual text.
                        //
                        // When no blocks: 400dp gives a generous blank writing canvas.
                        if (sortedBlocks.isEmpty()) Modifier.heightIn(min = 400.dp) else Modifier
                    )
            )

            // ── Blocks Section ───────────────────────────────────────────────
            // Only shown when there's at least one block.
            // A styled divider with "CHECKLISTS" label separates the text editor
            // from the blocks area — clear visual hierarchy without a heavy card.
            if (sortedBlocks.isNotEmpty()) {
                // Small gap between the last line of text and the first block.
                // No divider, no label — the checklist card itself provides enough
                // visual separation. Removing the "CHECKLISTS" divider keeps the
                // editor feeling like one continuous surface.
                Spacer(Modifier.height(Spacing.small))

                // Render each block
                sortedBlocks.forEach { block ->
                    when (block.type) {
                        InlineBlockType.TODO -> {
                            TodoBlockComposable(
                                block = block,
                                onToggleItem = { itemId -> viewModel.toggleTodoItem(block.id, itemId) },
                                onAddItem = { viewModel.addTodoItem(block.id) },
                                onUpdateItemText = { itemId, newText -> viewModel.updateTodoItemText(block.id, itemId, newText) },
                                onDeleteItem = { itemId -> viewModel.deleteTodoItem(block.id, itemId) },
                                onDeleteBlock = { viewModel.deleteBlock(block.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> { /* IMAGE, AUDIO — future sessions */ }
                    }
                }
            }

            Spacer(Modifier.height(200.dp))
        }

        // ── Heading Dialog ───────────────────────────────────────────────────
        if (showHeadingMenu) {
            AlertDialog(
                onDismissRequest = { showHeadingMenu = false },
                title = { Text("Text Size") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = {
                                if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.HEADING_SMALL)
                                else viewModel.setActiveHeading(FormatType.HEADING_SMALL)
                                showHeadingMenu = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) { Text("Small", fontSize = 16.sp, modifier = Modifier.padding(16.dp)) }

                        Surface(
                            onClick = {
                                if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.HEADING_NORMAL)
                                else viewModel.setActiveHeading(FormatType.HEADING_NORMAL)
                                showHeadingMenu = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text("Normal (Default)", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                        }

                        Surface(
                            onClick = {
                                if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.HEADING_LARGE)
                                else viewModel.setActiveHeading(FormatType.HEADING_LARGE)
                                showHeadingMenu = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text("Large", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showHeadingMenu = false }) { Text("Cancel") } }
            )
        }
    }

    // ── Delete Dialog ────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Note?") },
            text = { Text("\"${uiState.title.ifBlank { "Untitled" }}\" will be moved to trash.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteNote(); showDeleteDialog = false; onNavigateBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CHECKLISTS SECTION DIVIDER
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A tasteful divider that separates the text editor from the blocks area.
 *
 * Layout:  ──── CHECKLISTS ────────────────────────────────
 *
 * WHY A CUSTOM DIVIDER AND NOT JUST HorizontalDivider?
 * A plain divider gives no context. The label tells the user at a glance
 * what's below — especially useful when the note has both long text AND
 * multiple checklists. It also reinforces the structural hierarchy.
 *
 * NOTHING AESTHETIC:
 * - "CHECKLISTS" in uppercase with wide letter spacing (dot-matrix feel)
 * - Low contrast — it guides the eye without demanding attention
 * - The line extends fully across the remaining width
 */
@Composable
private fun ChecklistsSectionDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        // Short left line
        HorizontalDivider(
            modifier = Modifier.width(16.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            thickness = 1.dp
        )

        // Label
        Text(
            text = "CHECKLISTS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        // Long right line — fills remaining width
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            thickness = 1.dp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FORMATTING TOOLBAR
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Single-row compact toolbar — the modern approach used by Bear, Craft, Notion.
 *
 * DESIGN RATIONALE:
 * Two rows felt heavy and wasted screen space. A single row with a "+" insert
 * button is cleaner and more discoverable:
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  [B][I][U][S̶] │ [Aa] [✕?] │ [+ ▾]         12w  45c    │
 *  └──────────────────────────────────────────────────────────┘
 *         ↑ format      ↑ size     ↑ insert popup    ↑ count
 *
 * BEHAVIOURS:
 * - [✕] clear only appears when text is selected (contextual, not always visible)
 * - [+ ▾] opens an inline dropdown with insert options (Checklist, Image…)
 * - Format buttons highlight with primaryContainer when active
 * - Separators (│) are 1dp lines — subtle structure without visual noise
 */
@Composable
private fun FormattingToolbar(
    isBoldActive: Boolean,
    isItalicActive: Boolean,
    isUnderlineActive: Boolean,
    isStrikethroughActive: Boolean,
    activeHeading: FormatType?,
    hasSelection: Boolean,
    showInsertSheet: Boolean,       // hoisted — controlled by NoteEditorScreen
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onStrikethroughClick: () -> Unit,
    onHeadingClick: () -> Unit,
    onClearClick: () -> Unit,
    onInsertClick: () -> Unit,      // opens the insert bottom sheet
    onTodoClick: () -> Unit,
    wordCount: Int = 0,
    charCount: Int = 0
) {
    // showInsertSheet is hoisted to NoteEditorScreen so the ModalBottomSheet
    // can be rendered at the screen level (full-width, outside toolbar Surface).
    val showInsertMenu = showInsertSheet  // local alias for readability

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.small, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Format buttons ────────────────────────────────────────
                FormatButton(active = isBoldActive, onClick = onBoldClick) {
                    Icon(Icons.Default.FormatBold, "Bold", modifier = Modifier.size(18.dp),
                        tint = if (isBoldActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                FormatButton(active = isItalicActive, onClick = onItalicClick) {
                    Icon(Icons.Default.FormatItalic, "Italic", modifier = Modifier.size(18.dp),
                        tint = if (isItalicActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                FormatButton(active = isUnderlineActive, onClick = onUnderlineClick) {
                    Icon(Icons.Default.FormatUnderlined, "Underline", modifier = Modifier.size(18.dp),
                        tint = if (isUnderlineActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                FormatButton(active = isStrikethroughActive, onClick = onStrikethroughClick) {
                    Icon(Icons.Default.FormatStrikethrough, "Strikethrough", modifier = Modifier.size(18.dp),
                        tint = if (isStrikethroughActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }

                // ── Separator ─────────────────────────────────────────────
                ToolbarSeparator()

                // ── Heading size ──────────────────────────────────────────
                FormatButton(active = activeHeading != null, onClick = onHeadingClick) {
                    Icon(Icons.Default.FormatSize, "Text size", modifier = Modifier.size(18.dp),
                        tint = if (activeHeading != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }

                // Clear formatting — only when text is selected (contextual)
                if (hasSelection) {
                    FormatButton(active = false, onClick = onClearClick) {
                        Icon(Icons.Default.FormatClear, "Clear formatting", modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // ── Separator ─────────────────────────────────────────────
                ToolbarSeparator()

                // ── Insert "+" button → opens bottom sheet ────────────
                // Bottom sheet scales better than a dropdown as we add more
                // block types (Image, Audio, Code). Each type gets a large
                // tappable card — easier to hit on mobile than a menu row.
                FilledTonalIconButton(
                    onClick = onInsertClick,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (showInsertMenu) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(Icons.Default.Add, "Insert block", modifier = Modifier.size(18.dp),
                        tint = if (showInsertMenu) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
                }

                // ── Spacer pushes count to the right ─────────────────────
                Spacer(modifier = Modifier.weight(1f))

                // ── Word + char count ─────────────────────────────────────
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$wordCount w",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$charCount c",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INSERT BLOCK BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Inline insert panel — slides up from above the toolbar.
 *
 * WHY NOT ModalBottomSheet?
 * ModalBottomSheet is a system-level modal that grabs window focus when it
 * opens. This causes Android to DISMISS THE SOFT KEYBOARD immediately — even
 * if the user had the keyboard open and was typing. After selecting an option
 * the keyboard is gone and they must tap again to get it back.
 *
 * THIS APPROACH:
 * We render the panel as a regular Compose layout element that slides in with
 * AnimatedVisibility. It never takes window focus, never touches the IME, and
 * the keyboard stays open the entire time.
 *
 * DESIGN — Nothing aesthetic:
 * - Surface background with top border line (like a second toolbar row)
 * - 2-column grid of equal-sized cards
 * - Available blocks: primary accent border + full opacity
 * - Coming-soon: muted, "SOON" badge, 40% opacity, not clickable
 *
 * ADDING A NEW BLOCK TYPE (Sprint 4+):
 * Add one InsertBlockCard() call — the LazyVerticalGrid reflows automatically.
 *
 * @param visible           Drives the AnimatedVisibility — true = panel open
 * @param onDismiss         Called when user taps the × close button
 * @param onChecklistClick  Called when Checklist card is tapped
 */
@Composable
private fun InsertBlockSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onChecklistClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            expandFrom = Alignment.Bottom
        ) + fadeIn(tween(180)),
        exit = shrinkVertically(
            animationSpec = tween(160, easing = androidx.compose.animation.core.FastOutLinearInEasing),
            shrinkTowards = Alignment.Bottom
        ) + fadeOut(tween(120))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // ── Header row: label + close button ─────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INSERT BLOCK",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 2.sp,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    // Close button — tapping × dismisses the panel without inserting
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close insert panel",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Block type icons — same size as toolbar format buttons ────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InsertBlockButton(
                        icon = Icons.Default.CheckBox,
                        contentDescription = "Checklist",
                        available = true,
                        onClick = onChecklistClick
                    )
                    InsertBlockButton(
                        icon = Icons.Default.Image,
                        contentDescription = "Image (coming soon)",
                        available = false
                    )
                    InsertBlockButton(
                        icon = Icons.Default.KeyboardVoice,
                        contentDescription = "Voice (coming soon)",
                        available = false
                    )
                    InsertBlockButton(
                        icon = Icons.Default.FormatQuote,
                        contentDescription = "Code (coming soon)",
                        available = false
                    )
                }
            }
        }
    }
}

/**
 * Compact icon button for the insert panel.
 * Identical size and style to the format buttons (B, I, U…) in the toolbar.
 * Available = highlighted container; unavailable = 35% opacity, not clickable.
 */
@Composable
private fun InsertBlockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    available: Boolean,
    onClick: () -> Unit = {}
) {
    FilledTonalIconButton(
        onClick = { if (available) onClick() },
        enabled = available,
        modifier = Modifier
            .size(36.dp)
            .alpha(if (available) 1f else 0.35f),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (available) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (available) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Small square icon button for text formatting.
 * Highlights with primaryContainer background when active.
 */
@Composable
private fun FormatButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) { content() }
}

/**
 * 1dp vertical separator — subtle structure between toolbar groups.
 */
@Composable
private fun ToolbarSeparator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP APP BAR
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBackClick: () -> Unit,
    isPinned: Boolean,
    isArchived: Boolean,
    onPinClick: () -> Unit,
    onArchiveClick: () -> Unit,
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
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(if (isPinned) "Unpin" else "Pin to top")
                            }
                        },
                        onClick = { showMenu = false; onPinClick() }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                                Text("Share")
                            }
                        },
                        onClick = { showMenu = false; onShareClick() }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(if (isArchived) "Unarchive" else "Archive")
                            }
                        },
                        onClick = { showMenu = false; onArchiveClick() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        onClick = { showMenu = false; onDeleteClick() }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TAGS SECTION
// ─────────────────────────────────────────────────────────────────────────────

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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                tags.forEach { tag ->
                    EditableTagChip(tag = tag, onRemove = { onRemoveTag(tag) })
                }
                if (tags.size < 5) {
                    Surface(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("Add tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                    onValueChange = { if (it.length <= 20 && it.all { c -> c.isLetterOrDigit() || c.isWhitespace() }) tagName = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    supportingText = { Text("${tagName.length}/20") }
                )
            },
            confirmButton = {
                TextButton(onClick = { onAddTag(tagName.trim()); showAddDialog = false }, enabled = tagName.trim().isNotBlank()) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private fun shareNote(context: android.content.Context, title: String, content: String, tags: List<String>) {
    val text = buildString {
        appendLine(title); appendLine()
        if (tags.isNotEmpty()) { appendLine("Tags: ${tags.joinToString(", ")}"); appendLine() }
        append(content)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    context.startActivity(Intent.createChooser(intent, "Share note"))
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}