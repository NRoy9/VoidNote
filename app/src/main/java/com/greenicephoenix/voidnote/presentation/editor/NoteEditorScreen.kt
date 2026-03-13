package com.greenicephoenix.voidnote.presentation.editor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.domain.model.FormatType
import com.greenicephoenix.voidnote.domain.model.InlineBlockType
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.greenicephoenix.voidnote.data.storage.AudioStorageManager
import com.greenicephoenix.voidnote.data.storage.VoiceRecorderManager
import com.greenicephoenix.voidnote.data.storage.VoidNoteImageLoader
import com.greenicephoenix.voidnote.di.AudioManagerEntryPoint
import com.greenicephoenix.voidnote.di.ImageLoaderEntryPoint
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Link
import com.greenicephoenix.voidnote.domain.model.NoteColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload

/**
 * Note Editor Screen.
 *
 * PERMISSIONS MANAGED HERE:
 *   CAMERA       — for image capture (gallery pick needs no permission)
 *   RECORD_AUDIO — for voice notes
 *
 * Both follow the same 3-state smart permission flow:
 *   GRANTED              → proceed directly
 *   shouldShowRationale  → show rationale dialog → re-request
 *   permanently denied   → show "open Settings" dialog
 *
 * RECORDING FLOW:
 *   User taps voice button → permission check → startRecording()
 *   RecordingSheet slides in showing pulsing dot + elapsed timer
 *   User taps Stop → stopRecording() → AUDIO block appears in note
 *
 * SPRINT 11 · NOTE LINKING:
 *   "Link" option in the ⋮ overflow menu opens a bottom sheet showing all
 *   notes with a search box. Tapping a note links it. Linked notes appear
 *   as tappable chips just above the tags bar. onNavigateToEditor lets
 *   those chips navigate to the linked note's editor.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class,
    ExperimentalPermissionsApi::class)
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    // Sprint 11: navigate to a linked note when user taps a chip
    onNavigateToEditor: (noteId: String) -> Unit = {},
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // SPRINT 5: collect the folder list for the MoveToFolderDialog
    // collectAsState() turns the StateFlow<List<Folder>> into a Compose State —
    // the dialog will recompose whenever folders change.
    val folders by viewModel.folders.collectAsState()
    val noteColor = uiState.noteColor   // Sprint 6 — current color accent
    val context = LocalContext.current

    // ── Singletons via Hilt EntryPoints ───────────────────────────────────────
    val imageLoader: VoidNoteImageLoader = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ImageLoaderEntryPoint::class.java
        ).imageLoader()
    }
    val audioEntry = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AudioManagerEntryPoint::class.java
        )
    }
    val audioStorage: AudioStorageManager = remember { audioEntry.audioStorage() }
    val voiceRecorder: VoiceRecorderManager = remember { audioEntry.voiceRecorder() }

    // ── Camera permission ─────────────────────────────────────────────────────
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA) { isGranted ->
        if (isGranted) {
            val uri = viewModel.prepareCameraCapture()
            uri?.let { viewModel.storePendingCameraUri(it) }
        }
    }
    var showCameraRationale by remember { mutableStateOf(false) }
    var showCameraSettingsDialog by remember { mutableStateOf(false) }
    var hasRequestedCameraPermission by remember { mutableStateOf(false) }

    // ── Microphone permission ─────────────────────────────────────────────────
    val micPermissionState =
        rememberPermissionState(Manifest.permission.RECORD_AUDIO) { isGranted ->
            if (isGranted) viewModel.startRecording()
        }
    var showMicRationale by remember { mutableStateOf(false) }
    var showMicSettingsDialog by remember { mutableStateOf(false) }
    var hasRequestedMicPermission by remember { mutableStateOf(false) }

    // ── Gallery launcher ──────────────────────────────────────────────────────
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.insertImageBlock(it) }
        }

    // ── Camera launcher ───────────────────────────────────────────────────────
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val tempPath = uiState.cameraCaptureTempPath
            if (success && tempPath != null) viewModel.insertCameraImage(tempPath)
            else viewModel.clearCameraCapturePath()
        }

    // Watch for pending camera URI (set after permission callback grants access)
    LaunchedEffect(uiState.pendingCameraUri) {
        uiState.pendingCameraUri?.let { uri ->
            cameraLauncher.launch(uri)
            viewModel.clearPendingCameraUri()
        }
    }

    // ── Smart camera tap handler ──────────────────────────────────────────────
    val onCameraClick: () -> Unit = {
        when {
            cameraPermissionState.status.isGranted -> {
                val uri = viewModel.prepareCameraCapture()
                uri?.let { cameraLauncher.launch(it) }
            }

            cameraPermissionState.status.shouldShowRationale -> showCameraRationale = true
            !hasRequestedCameraPermission -> {
                hasRequestedCameraPermission = true
                cameraPermissionState.launchPermissionRequest()
            }

            else -> showCameraSettingsDialog = true
        }
    }

    // ── Smart microphone tap handler ──────────────────────────────────────────
    val onVoiceClick: () -> Unit = {
        when {
            micPermissionState.status.isGranted -> viewModel.startRecording()
            micPermissionState.status.shouldShowRationale -> showMicRationale = true
            !hasRequestedMicPermission -> {
                hasRequestedMicPermission = true
                micPermissionState.launchPermissionRequest()
            }

            else -> showMicSettingsDialog = true
        }
    }

    // ── Cancel recording if user navigates away ───────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            if (uiState.isRecording) viewModel.cancelRecording()
            viewModel.forceSave()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHeadingMenu by remember { mutableStateOf(false) }
    var showInsertSheet by remember { mutableStateOf(false) }
    // SPRINT 5: controls visibility of the "Move to folder" folder picker dialog
    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    // SPRINT 7: controls visibility of the color picker dialog (moved from bottom bar)
    var showColorDialog by remember { mutableStateOf(false) }
    // SPRINT 10 · Focus Mode
    var isFocusMode by remember { mutableStateOf(false) }
    // SPRINT 11 · Note Linking — controls the link picker bottom sheet
    var showLinkSheet by remember { mutableStateOf(false) }

    // Declared here — ABOVE onNumberedListClick — because that lambda
    // captures contentFieldValue by reference and Kotlin does not hoist vars.
    var titleFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.title,
                selection = TextRange(uiState.title.length)
            )
        )
    }
    var contentFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.content,
                selection = TextRange(uiState.content.length)
            )
        )
    }

    /**
     * Numbered list insertion — runs in the screen because it needs to
     * reposition the cursor, which lives in contentFieldValue (local state).
     *
     * Logic:
     * 1. Find the start of the line where the cursor currently sits.
     * 2. Check if that line already starts with "N. " (toggle off if so).
     * 3. If the previous line is a numbered list item, use nextNum; else "1. ".
     * 4. Insert the prefix, update contentFieldValue with the new cursor position,
     *    then call viewModel.onContentChange() to trigger format adjustment + save.
     */
    val onNumberedListClick: () -> Unit = {
        val text = contentFieldValue.text
        val cursor = contentFieldValue.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1   // 0 if no prior newline
        val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val lineText = text.substring(lineStart, lineEnd)

        val existingNumPattern = Regex("""^\d+\.\s""")

        if (existingNumPattern.containsMatchIn(lineText)) {
            // ── Toggle OFF: remove the "N. " prefix ──────────────────────────
            val prefixLen = existingNumPattern.find(lineText)!!.value.length
            val newText = text.removeRange(lineStart, lineStart + prefixLen)
            val newCursor = (cursor - prefixLen).coerceAtLeast(lineStart)
            contentFieldValue = TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
            viewModel.onContentChange(newText)
        } else {
            // ── Toggle ON: find what number to use ───────────────────────────
            val prevLineEnd = (lineStart - 1).coerceAtLeast(0)
            val prevLineStart = text.lastIndexOf('\n', prevLineEnd - 1) + 1
            val prevLine = if (lineStart > 0) text.substring(prevLineStart, prevLineEnd) else ""
            val prevNum = existingNumPattern.find(prevLine)
                ?.value?.trimEnd()?.dropLast(1)?.toIntOrNull()
            val num = if (prevNum != null) prevNum + 1 else 1
            val prefix = "$num. "
            val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
            val newCursor = cursor + prefix.length
            contentFieldValue = TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
            viewModel.onContentChange(newText)
        }
    }

    LaunchedEffect(uiState.title) {
        if (titleFieldValue.text != uiState.title) titleFieldValue =
            titleFieldValue.copy(text = uiState.title)
    }
    LaunchedEffect(uiState.content) {
        if (contentFieldValue.text != uiState.content) contentFieldValue =
            contentFieldValue.copy(text = uiState.content)
    }

    val hasSelection = contentFieldValue.selection.start != contentFieldValue.selection.end
    val sortedBlocks = remember(uiState.blocks) { uiState.blocks.values.sortedBy { it.createdAt } }

    // ── Layout ────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            // Focus Mode: TopBar slides up and out. AnimatedVisibility handles
            // the animation; when invisible Scaffold applies zero top inset so
            // the content fills the space the TopBar previously occupied.
            AnimatedVisibility(
                visible = !isFocusMode,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
            ) {
                TopBar(
                    onBackClick = onNavigateBack,
                    isPinned = uiState.isPinned,
                    isArchived = uiState.isArchived,
                    onPinClick = { viewModel.togglePin() },
                    onArchiveClick = { viewModel.archiveNote(); onNavigateBack() },
                    onDeleteClick = { showDeleteDialog = true },
                    onShareClick = {
                        shareNote(
                            context,
                            uiState.title.ifBlank { "Untitled" },
                            uiState.content,
                            uiState.tags,
                            uiState.blocks
                        )
                    },
                    lastSaved = uiState.lastSaved,
                    // SPRINT 5: pass the folder name for display and the callback to open the dialog
                    currentFolderName = uiState.currentFolderName,
                    onMoveToFolderClick = { showMoveToFolderDialog = true },
                    currentColor = noteColor,
                    onColorClick = { showColorDialog = true },
                    // Sprint 11: load picker notes then show the sheet
                    onLinkClick = {
                        viewModel.loadAllNotesForLinkPicker()
                        showLinkSheet = true
                    },
                    // SPRINT 10: Preview + Focus now live in the TopBar
                    showPreview = uiState.showPreview,
                    isFocusMode = isFocusMode,
                    onPreviewClick = { viewModel.togglePreview() },
                    onFocusToggle = { isFocusMode = !isFocusMode }
                )
            }
        },
        bottomBar = {
            // Focus Mode: entire bottom bar (tags + toolbar + sheets) slides down
            // and out, mirroring the TopBar's fade+shrink exit above.
            // When invisible, Scaffold reclaims the space so the writing canvas
            // stretches edge-to-edge. The keyboard remains open — the user is
            // still writing, just without any UI chrome.
            AnimatedVisibility(
                visible = !isFocusMode,
                enter = fadeIn(tween(220)) + expandVertically(tween(220), Alignment.Bottom),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(180), Alignment.Bottom)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    // Sprint 11: linked notes strip — hidden when no links exist.
                    // Appears just above the tags bar so it's always visible while editing.
                    if (uiState.linkedNotePreviews.isNotEmpty()) {
                        LinkedNotesStrip(
                            notes = uiState.linkedNotePreviews,
                            onNoteClick = { noteId -> onNavigateToEditor(noteId) },
                            onUnlink = { noteId -> viewModel.unlinkNote(noteId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    TagsSection(
                        tags = uiState.tags,
                        onAddTag = { viewModel.addTag(it) },
                        onRemoveTag = { viewModel.removeTag(it) }
                    )

                    FormattingToolbar(
                        isBoldActive = if (hasSelection) hasFormat(
                            uiState.contentFormats,
                            contentFieldValue.selection.start,
                            contentFieldValue.selection.end,
                            FormatType.BOLD
                        ) else uiState.activeBold,
                        isItalicActive = if (hasSelection) hasFormat(
                            uiState.contentFormats,
                            contentFieldValue.selection.start,
                            contentFieldValue.selection.end,
                            FormatType.ITALIC
                        ) else uiState.activeItalic,
                        isUnderlineActive = if (hasSelection) hasFormat(
                            uiState.contentFormats,
                            contentFieldValue.selection.start,
                            contentFieldValue.selection.end,
                            FormatType.UNDERLINE
                        ) else uiState.activeUnderline,
                        isStrikethroughActive = if (hasSelection) hasFormat(
                            uiState.contentFormats,
                            contentFieldValue.selection.start,
                            contentFieldValue.selection.end,
                            FormatType.STRIKETHROUGH
                        ) else uiState.activeStrikethrough,
                        activeHeading = uiState.activeHeading,
                        hasSelection = hasSelection,
                        showInsertSheet = showInsertSheet,
                        showPreview = uiState.showPreview,
                        onInsertClick = { showInsertSheet = true },
                        onBoldClick = {
                            if (hasSelection) viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.BOLD
                            ) else viewModel.toggleActiveBold()
                        },
                        onItalicClick = {
                            if (hasSelection) viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.ITALIC
                            ) else viewModel.toggleActiveItalic()
                        },
                        onUnderlineClick = {
                            if (hasSelection) viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.UNDERLINE
                            ) else viewModel.toggleActiveUnderline()
                        },
                        onStrikethroughClick = {
                            if (hasSelection) viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.STRIKETHROUGH
                            ) else viewModel.toggleActiveStrikethrough()
                        },
                        onHeadingClick = { showHeadingMenu = true },
                        onClearClick = { viewModel.clearAllFormatting() },
                        onTodoClick = { viewModel.insertTodoBlock() },
                        onNumberedListClick = onNumberedListClick,
                        wordCount = contentFieldValue.text.split("\\s+".toRegex())
                            .filter { it.isNotBlank() }.size,
                        charCount = contentFieldValue.text.length
                    )

                    // RecordingSheet slides in OVER InsertBlockSheet while recording.
                    // Both stay in composition so animations work correctly.
                    RecordingSheet(
                        isVisible = uiState.isRecording,
                        elapsedMs = uiState.recordingElapsedMs,
                        onStopClick = { viewModel.stopRecording() }
                    )

                    InsertBlockSheet(
                        visible = showInsertSheet && !uiState.isRecording,
                        onDismiss = { showInsertSheet = false },
                        onChecklistClick = { showInsertSheet = false; viewModel.insertTodoBlock() },
                        onGalleryClick = {
                            showInsertSheet = false
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onCameraClick = { showInsertSheet = false; onCameraClick() },
                        onVoiceClick = { showInsertSheet = false; onVoiceClick() }
                    )
                }
            }
        }
    ) { paddingValues ->
        if (uiState.showPreview) {
            // ── PREVIEW MODE — read-only styled view ─────────────────────────
            // Shows the note with all FormatRanges rendered visually.
            // Uses applyFormatting() which already lives in TextSpanUtils.kt.
            // SelectionContainer allows the user to copy text from the preview.
            NotePreviewPanel(
                title = uiState.title,
                content = uiState.content,
                contentFormats = uiState.contentFormats,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            // ── EDIT MODE ────────────────────────────────────────────────────
            // Box lets us overlay the ghost exit-focus button over the canvas
            // without affecting the scroll layout beneath it.
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.medium)
                ) {
                    Spacer(Modifier.height(Spacing.small))

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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(Modifier.height(Spacing.medium))

                    RichTextEditor(
                        value = contentFieldValue,
                        onValueChange = { newValue ->
                            val oldText = contentFieldValue.text
                            val newText = newValue.text
                            val cursor = newValue.selection.start

                            // Auto-continue numbered list when Enter is pressed.
                            // Detect: text grew by exactly 1 char AND that char is \n.
                            val handled = if (
                                newText.length == oldText.length + 1 &&
                                cursor > 0 &&
                                newText[cursor - 1] == '\n'
                            ) {
                                // Find the line that just had Enter pressed at its end
                                val insertedAt = cursor - 1
                                val prevLineStart = newText.lastIndexOf('\n', insertedAt - 1) + 1
                                val prevLine = newText.substring(prevLineStart, insertedAt)
                                val numMatch = Regex("""^(\d+)\.\s""").find(prevLine)
                                if (numMatch != null) {
                                    // Previous line was "N. something" → insert "N+1. " on new line
                                    val nextNum = (numMatch.groupValues[1].toIntOrNull() ?: 0) + 1
                                    val prefix = "$nextNum. "
                                    val finalText =
                                        newText.substring(0, cursor) + prefix + newText.substring(
                                            cursor
                                        )
                                    contentFieldValue = TextFieldValue(
                                        text = finalText,
                                        selection = TextRange(cursor + prefix.length)
                                    )
                                    viewModel.onContentChange(finalText)
                                    true
                                } else false
                            } else false

                            if (!handled) {
                                contentFieldValue = newValue
                                viewModel.onContentChange(newValue.text)
                            }
                        },
                        placeholder = "Start writing...",
                        textStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                        formats = uiState.contentFormats,
                        modifier = Modifier.fillMaxWidth().then(
                            if (sortedBlocks.isEmpty()) Modifier.heightIn(min = 400.dp) else Modifier
                        )
                    )

                    if (sortedBlocks.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.small))
                        sortedBlocks.forEach { block ->
                            when (block.type) {
                                InlineBlockType.TODO -> {
                                    TodoBlockComposable(
                                        block = block,
                                        onToggleItem = { itemId ->
                                            viewModel.toggleTodoItem(
                                                block.id,
                                                itemId
                                            )
                                        },
                                        onAddItem = { viewModel.addTodoItem(block.id) },
                                        onUpdateItemText = { itemId, newText ->
                                            viewModel.updateTodoItemText(
                                                block.id,
                                                itemId,
                                                newText
                                            )
                                        },
                                        onDeleteItem = { itemId ->
                                            viewModel.deleteTodoItem(
                                                block.id,
                                                itemId
                                            )
                                        },
                                        onDeleteBlock = { viewModel.deleteBlock(block.id) },
                                        onPasteLines = { itemId, firstLine, remainingLines ->
                                            viewModel.pasteTodoLines(
                                                blockId = block.id,
                                                afterItemId = itemId,
                                                firstLineText = firstLine,
                                                remainingLines = remainingLines
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                InlineBlockType.IMAGE -> {
                                    ImageBlockComposable(
                                        block = block,
                                        voidNoteImageLoader = imageLoader,
                                        onCaptionChange = {
                                            viewModel.updateImageCaption(
                                                block.id,
                                                it
                                            )
                                        },
                                        onDeleteBlock = { viewModel.deleteBlock(block.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                InlineBlockType.AUDIO -> {
                                    // AudioBlockComposable manages its own playback state locally.
                                    // It gets audioStorage and voiceRecorder from EntryPoint (passed as params)
                                    // so it can decrypt and create a MediaPlayer without a ViewModel reference.
                                    AudioBlockComposable(
                                        block = block,
                                        audioStorage = audioStorage,
                                        voiceRecorder = voiceRecorder,
                                        onDeleteBlock = { viewModel.deleteBlock(block.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                else -> {
                                    // DRAWING and any future block types — not yet implemented.
                                    // The else branch is required because InlineBlockType is an enum
                                    // and Kotlin's when must be exhaustive when used as an expression.
                                }
                            }
                            Spacer(Modifier.height(Spacing.small))
                        }
                    }

                    Spacer(Modifier.height(200.dp))
                }  // end Column

                // ── Ghost exit button (Focus Mode only) ───────────────────────
                // When focus mode is active, all chrome is hidden. This tiny pill
                // floats at the bottom-right of the canvas so the user has a clear,
                // discoverable way to exit without hunting for a gesture.
                //
                // Design choices:
                //   • 10% opacity at rest → barely visible, non-distracting
                //   • "EXIT FOCUS" label in labelSmall so it's text, not just an icon
                //   • Placed 24dp from bottom-right — clear of the system nav area
                //     because the Scaffold's paddingValues already accounts for system bars
                //   • AnimatedVisibility: same fade timing as TopBar/bottomBar enter/exit
                AnimatedVisibility(
                    visible = isFocusMode,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(paddingValues)
                        .padding(bottom = Spacing.large, end = Spacing.medium)
                ) {
                    Surface(
                        onClick = { isFocusMode = false },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Exit focus mode",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "EXIT FOCUS",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }  // end Box
        }

        // ── Heading bottom sheet ──────────────────────────────────────────────────
        if (showHeadingMenu) {
            ModalBottomSheet(
                onDismissRequest = { showHeadingMenu = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { SheetDragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.large)
                        .padding(bottom = Spacing.extraLarge)
                ) {
                    Text(
                        text = "TEXT SIZE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = Spacing.medium)
                    )
                    Surface(
                        onClick = {
                            if (hasSelection) viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.HEADING_SMALL
                            ) else viewModel.setActiveHeading(FormatType.HEADING_SMALL); showHeadingMenu =
                            false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) { Text("Small", fontSize = 16.sp, modifier = Modifier.padding(16.dp)) }
                    Spacer(Modifier.height(Spacing.small))
                    Surface(
                        onClick = {
                            if (hasSelection) viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.HEADING_NORMAL
                            ) else viewModel.setActiveHeading(FormatType.HEADING_NORMAL); showHeadingMenu =
                            false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Normal (Default)",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(Modifier.height(Spacing.small))
                    Surface(
                        onClick = {
                            if (hasSelection) viewModel.applyFormatting(
                                contentFieldValue.selection.start,
                                contentFieldValue.selection.end,
                                FormatType.HEADING_LARGE
                            ) else viewModel.setActiveHeading(FormatType.HEADING_LARGE); showHeadingMenu =
                            false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
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
            }
        }

        // ── Delete dialog ─────────────────────────────────────────────────────────
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete Note?") },
                text = { Text("\"${uiState.title.ifBlank { "Untitled" }}\" will be moved to trash.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteNote(); showDeleteDialog = false; onNavigateBack()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                    }) { Text("Cancel") }
                }
            )
        }

        // Shows a list of all folders. Tapping a folder moves the note there.
        // "No folder" option at the top removes the note from any folder → root level.
        if (showMoveToFolderDialog) {
            MoveToFolderDialog(
                folders = folders,
                currentFolderName = uiState.currentFolderName,
                onFolderSelected = { folderId ->
                    viewModel.moveToFolder(folderId)
                    showMoveToFolderDialog = false
                },
                onDismiss = { showMoveToFolderDialog = false }
            )
        }

        // ── Color picker bottom sheet ─────────────────────────────────────────────
        // Replaces the old AlertDialog — a sheet gives the color dots more room
        // and feels more natural for a picker triggered from the overflow menu.
        if (showColorDialog) {
            ModalBottomSheet(
                onDismissRequest = { showColorDialog = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { SheetDragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.large)
                        .padding(bottom = Spacing.extraLarge)
                ) {
                    Text(
                        text = "NOTE COLOR",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = Spacing.medium)
                    )
                    NoteColorPicker(
                        currentColor = noteColor,
                        onColorSelected = { color ->
                            viewModel.updateNoteColor(color)
                            showColorDialog = false
                        }
                    )
                    Spacer(Modifier.height(Spacing.large))
                    // "Clear" as a text row — cleaner than a button in a sheet
                    Surface(
                        onClick = { viewModel.updateNoteColor(null); showColorDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Clear color",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // ── Sprint 11 · Link Picker Sheet ─────────────────────────────────────────
        if (showLinkSheet) {
            LinkPickerSheet(
                allNotes = uiState.allNotesForPicker,
                linkedNoteIds = uiState.linkedNoteIds,
                onLink = { noteId -> viewModel.linkNote(noteId) },
                onUnlink = { noteId -> viewModel.unlinkNote(noteId) },
                onDismiss = { showLinkSheet = false }
            )
        }

        // ── Camera rationale dialog ───────────────────────────────────────────────
        if (showCameraRationale) {
            AlertDialog(
                onDismissRequest = { showCameraRationale = false },
                icon = {
                    Icon(
                        Icons.Default.CameraAlt,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Camera Access") },
                text = { Text("Void Note needs camera access to capture photos.\n\nPhotos are encrypted immediately and never saved to your gallery.") },
                confirmButton = {
                    TextButton(onClick = {
                        showCameraRationale = false; hasRequestedCameraPermission =
                        true; cameraPermissionState.launchPermissionRequest()
                    }) { Text("Allow") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCameraRationale = false
                    }) { Text("Not now") }
                }
            )
        }

        // ── Camera permanently denied dialog ─────────────────────────────────────
        if (showCameraSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showCameraSettingsDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Camera Permission Required") },
                text = { Text("Camera access was denied.\n\nTo enable: Settings → Permissions → Camera → Allow") },
                confirmButton = {
                    TextButton(onClick = {
                        showCameraSettingsDialog =
                            false; context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                    }) { Text("Open Settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showCameraSettingsDialog = false }) {
                        Text(
                            "Cancel"
                        )
                    }
                }
            )
        }

        // ── Microphone rationale dialog ───────────────────────────────────────────
        if (showMicRationale) {
            AlertDialog(
                onDismissRequest = { showMicRationale = false },
                icon = { Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Microphone Access") },
                text = { Text("Void Note needs microphone access to record voice notes.\n\nVoice notes are encrypted immediately — no audio is ever stored without encryption.") },
                confirmButton = {
                    TextButton(onClick = {
                        showMicRationale = false; hasRequestedMicPermission =
                        true; micPermissionState.launchPermissionRequest()
                    }) { Text("Allow") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showMicRationale = false
                    }) { Text("Not now") }
                }
            )
        }

        // ── Microphone permanently denied dialog ──────────────────────────────────
        if (showMicSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showMicSettingsDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Microphone Permission Required") },
                text = { Text("Microphone access was denied.\n\nTo enable: Settings → Permissions → Microphone → Allow") },
                confirmButton = {
                    TextButton(onClick = {
                        showMicSettingsDialog =
                            false; context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                    }) { Text("Open Settings") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showMicSettingsDialog = false
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INSERT BLOCK PANEL
// ─────────────────────────────────────────────────────────────────────────────

    @Composable
    private fun InsertBlockSheet(
        visible: Boolean,
        onDismiss: () -> Unit,
        onChecklistClick: () -> Unit,
        onGalleryClick: () -> Unit,
        onCameraClick: () -> Unit,
        onVoiceClick: () -> Unit          // ← VOICE NOW ACTIVE
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing), Alignment.Bottom) + fadeIn(tween(180)),
            exit  = shrinkVertically(tween(160, easing = androidx.compose.animation.core.FastOutLinearInEasing), Alignment.Bottom) + fadeOut(tween(120))
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("INSERT BLOCK", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        InsertBlockButton(Icons.Default.CheckBox,     "Checklist", true,  onChecklistClick)
                        InsertBlockButton(Icons.Default.Image,        "Gallery",   true,  onGalleryClick)
                        InsertBlockButton(Icons.Default.CameraAlt,    "Camera",    true,  onCameraClick)
                        InsertBlockButton(Icons.Default.Mic,          "Voice",     true,  onVoiceClick)  // ← NOW ACTIVE
                    }
                }
            }
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// SHARED BUTTON COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

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
            modifier = Modifier.size(36.dp).alpha(if (available) 1f else 0.35f),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )
        ) {
            Icon(icon, contentDescription, modifier = Modifier.size(18.dp),
                tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }

    @Composable
    private fun FormatButton(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )) { content() }
    }

    @Composable
    private fun ToolbarSeparator() {
        Box(modifier = Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)))
    }

// ─────────────────────────────────────────────────────────────────────────────
// FORMATTING TOOLBAR
// ─────────────────────────────────────────────────────────────────────────────

    @Composable
    private fun FormattingToolbar(
        isBoldActive: Boolean, isItalicActive: Boolean, isUnderlineActive: Boolean,
        isStrikethroughActive: Boolean, activeHeading: FormatType?, hasSelection: Boolean,
        showInsertSheet: Boolean,
        // showPreview retained so the formatting group can hide itself in preview mode,
        // but preview/focus toggle buttons have moved to the TopBar.
        showPreview: Boolean,
        onBoldClick: () -> Unit, onItalicClick: () -> Unit,
        onUnderlineClick: () -> Unit, onStrikethroughClick: () -> Unit, onHeadingClick: () -> Unit,
        onClearClick: () -> Unit, onInsertClick: () -> Unit, onTodoClick: () -> Unit,
        onNumberedListClick: () -> Unit,
        wordCount: Int = 0, charCount: Int = 0
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 0.dp) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.small, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Formatting buttons hidden in preview mode — nothing to format
                    if (!showPreview) {
                        // ── Group 1: Basic formatting (B / I / U / S) ──────────────────────
                        // Wrapped in a Row with spacedBy(3.dp) so the icons breathe a little.
                        // Without this they were flush against each other — hard to tap precisely.
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            FormatButton(isBoldActive, onBoldClick) { Icon(Icons.Default.FormatBold, "Bold", Modifier.size(18.dp), tint = if (isBoldActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                            FormatButton(isItalicActive, onItalicClick) { Icon(Icons.Default.FormatItalic, "Italic", Modifier.size(18.dp), tint = if (isItalicActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                            FormatButton(isUnderlineActive, onUnderlineClick) { Icon(Icons.Default.FormatUnderlined, "Underline", Modifier.size(18.dp), tint = if (isUnderlineActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                            FormatButton(isStrikethroughActive, onStrikethroughClick) { Icon(Icons.Default.FormatStrikethrough, "Strikethrough", Modifier.size(18.dp), tint = if (isStrikethroughActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                        }
                        ToolbarSeparator()
                        // ── Group 2: Heading / Numbered list / Clear ────────────────────────
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            FormatButton(activeHeading != null, onHeadingClick) { Icon(Icons.Default.FormatSize, "Text size", Modifier.size(18.dp), tint = if (activeHeading != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                            // Numbered list button — inserts "N. " prefix on current line
                            FormatButton(false, onNumberedListClick) { Icon(Icons.Default.FormatListNumbered, "Numbered list", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) }
                            if (hasSelection) { FormatButton(false, onClearClick) { Icon(Icons.Default.FormatClear, "Clear", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) } }
                        }
                        ToolbarSeparator()
                        // ── Insert block button ─────────────────────────────────────────────
                        FilledTonalIconButton(
                            onClick = onInsertClick,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (showInsertSheet) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Icon(Icons.Default.Add, "Insert", Modifier.size(18.dp),
                                tint = if (showInsertSheet) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Word/char count and reading time — hidden in preview mode
                    if (!showPreview) {
                        val readTimeMin = (wordCount / 200.0).let {
                            if (it < 1.0) 1 else kotlin.math.ceil(it).toInt()
                        }
                        Column(
                            modifier              = Modifier.padding(end = 4.dp),
                            horizontalAlignment   = Alignment.End,
                            verticalArrangement   = Arrangement.Center
                        ) {
                            Text(
                                text  = "${wordCount}w · ${charCount}c",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                            )
                            Text(
                                text  = "~${readTimeMin} min read",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
            }
        }
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
        lastSaved: Long,
        currentFolderName: String? = null,
        onMoveToFolderClick: () -> Unit = {},
        currentColor: NoteColor? = null,
        onColorClick: () -> Unit = {},
        // Sprint 11: opens the link picker sheet
        onLinkClick: () -> Unit = {},
        // SPRINT 10: Preview + Focus Mode moved here from FormattingToolbar
        showPreview: Boolean = false,
        isFocusMode: Boolean = false,
        onPreviewClick: () -> Unit = {},
        onFocusToggle: () -> Unit = {}
    ) {
        var showMenu by remember { mutableStateOf(false) }
        TopAppBar(
            title = {
                Column {
                    Text(
                        if (lastSaved > 0) "Saved" else "Not saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    if (lastSaved > 0) Text(
                        formatTime(lastSaved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                // Preview toggle — tinted primary when active
                IconButton(onClick = onPreviewClick) {
                    Icon(
                        imageVector        = if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPreview) "Exit preview" else "Preview formatting",
                        tint               = if (showPreview) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                // Focus mode toggle — always reachable here, never crowded
                IconButton(onClick = onFocusToggle) {
                    Icon(
                        imageVector        = if (isFocusMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFocusMode) "Exit focus mode" else "Enter focus mode",
                        tint               = if (isFocusMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {

                        // Pin / Unpin
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isPinned) Icons.Filled.PushPin else Icons.Default.PushPin,
                                        null,
                                        tint = if (isPinned) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(if (isPinned) "Unpin" else "Pin to top")
                                }
                            },
                            onClick = { showMenu = false; onPinClick() }
                        )

                        // Share
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                                    Text("Share")
                                }
                            },
                            onClick = { showMenu = false; onShareClick() }
                        )

                        // Archive / Unarchive
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(if (isArchived) "Unarchive" else "Archive")
                                }
                            },
                            onClick = { showMenu = false; onArchiveClick() }
                        )

                        // SPRINT 5: Move to folder
                        // Shows the current folder name as a subtitle so the user knows
                        // where the note currently lives before tapping.
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("Move to folder")
                                        // Show current location as a subtle hint
                                        Text(
                                            text  = if (currentFolderName != null) "In: $currentFolderName"
                                            else "Not in a folder",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            },
                            onClick = { showMenu = false; onMoveToFolderClick() }
                        )

                        // SPRINT 7: Color / category — opens the color picker dialog
                        // Shows a small colored dot (or empty ring for "none") next to the label
                        // so the user can see at a glance what color is currently set.
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    // Dot preview — filled circle if color set, outlined ring if none
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (currentColor != null)
                                                    Modifier.background(currentColor.pickerColor)
                                                else
                                                    Modifier.border(
                                                        1.5.dp,
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                        CircleShape
                                                    )
                                            )
                                    )
                                    Column {
                                        Text("Color")
                                        Text(
                                            text  = currentColor?.label ?: "None",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            },
                            onClick = { showMenu = false; onColorClick() }
                        )

                        HorizontalDivider()

                        // Sprint 11: Link — opens the note picker bottom sheet
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Link, null, modifier = Modifier.size(20.dp))
                                    Text("Link note")
                                }
                            },
                            onClick = { showMenu = false; onLinkClick() }
                        )

                        HorizontalDivider()

                        // Delete (destructive — shown in error color)
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Delete, null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp))
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            onClick = { showMenu = false; onDeleteClick() }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// TAGS SECTION
// ─────────────────────────────────────────────────────────────────────────────

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
    @Composable
    private fun TagsSection(tags: List<String>, onAddTag: (String) -> Unit, onRemoveTag: (String) -> Unit) {
        var showAddDialog by remember { mutableStateOf(false) }
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 0.dp) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                FlowRow(modifier = Modifier.fillMaxWidth().padding(Spacing.medium), horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    tags.forEach { EditableTagChip(tag = it, onRemove = { onRemoveTag(it) }) }
                    // Show "Add tag" button when under the limit.
                    // When AT the limit (5 tags), show a subtle "Max 5" label instead
                    // so the user understands WHY the button is gone.
                    if (tags.size < 5) {
                        Surface(
                            onClick        = { showAddDialog = true },
                            modifier       = Modifier.height(32.dp),
                            shape          = RoundedCornerShape(16.dp),
                            color          = MaterialTheme.colorScheme.surfaceVariant,
                            border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier              = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text("Add tag", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    } else {
                        // Max tags reached — show a quiet indicator instead of disappearing
                        Text(
                            text     = "Max 5 tags",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.padding(vertical = Spacing.extraSmall)
                        )
                    }
                }
            }
        }
        if (showAddDialog) {
            var tagName by remember { mutableStateOf("") }
            // ModalBottomSheet — keyboard pushes the sheet up naturally via imePadding.
            // This feels much more native than an AlertDialog with a TextField inside.
            ModalBottomSheet(
                onDismissRequest = { showAddDialog = false },
                sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor   = MaterialTheme.colorScheme.surface,
                dragHandle       = { SheetDragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = Spacing.large)
                        .padding(bottom = Spacing.large)
                ) {
                    Text(
                        text     = "ADD TAG",
                        style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = Spacing.medium)
                    )
                    OutlinedTextField(
                        value         = tagName,
                        onValueChange = { if (it.length <= 20 && it.all { c -> c.isLetterOrDigit() || c.isWhitespace() }) tagName = it },
                        label         = { Text("Tag name") },
                        singleLine    = true,
                        supportingText = { Text("${tagName.length}/20") },
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.medium))
                    Button(
                        onClick  = { onAddTag(tagName.trim()); showAddDialog = false },
                        enabled  = tagName.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Tag")
                    }
                }
            }
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// FORMAT PREVIEW PANEL
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * Read-only preview of the note with all FormatRanges rendered visually.
     *
     * WHY NO MARKDOWN LIBRARY:
     * The existing FormatRange system already tracks every bold/italic/heading.
     * applyFormatting() converts those ranges to an AnnotatedString — the same
     * AnnotatedString the editor renders. The preview just shows it without
     * the editing affordances. No markdown parser, no new dependency, no conflict.
     *
     * SelectionContainer lets the user copy text from the preview.
     */
    @Composable
    private fun NotePreviewPanel(
        title: String,
        content: String,
        contentFormats: List<FormatRange>,
        modifier: Modifier = Modifier
    ) {
        val annotatedContent = remember(content, contentFormats) {
            if (contentFormats.isEmpty())
                androidx.compose.ui.text.AnnotatedString(content)
            else
                applyFormatting(content, contentFormats)
        }

        SelectionContainer {
            Column(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.medium)
            ) {
                Spacer(Modifier.height(Spacing.medium))

                Text(
                    text  = title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color    = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Spacing.medium))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(Spacing.medium))

                if (content.isBlank()) {
                    Text(
                        text  = "Nothing written yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                    )
                } else {
                    Text(
                        text  = annotatedContent,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(Modifier.height(200.dp))
            }
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * shareNote — formats a note as plain text and fires the system share sheet.
     *
     * ROOT CAUSE OF PREVIOUS BUG:
     * uiState.content holds the *logical* content — DocumentParser.extractLogicalContent()
     * strips everything from the first ⟦block:...⟧ marker onwards when loading the note.
     * So parsing uiState.content directly produced zero Block nodes; checklists were invisible.
     *
     * THE FIX:
     * Reconstruct the full raw content string with DocumentParser.buildRawContent() before
     * calling parse(). This re-appends the block marker tokens so parse() can find them.
     *
     * CHECKLIST RENDERING:
     *   ☑ Buy milk        (isChecked = true)
     *   ☐ Buy eggs        (isChecked = false)
     *
     * IMAGE / AUDIO blocks:
     *   [Image]  /  [Voice note]  — noted but not shareable as text
     */
    private fun shareNote(
        context : android.content.Context,
        title   : String,
        content : String,
        tags    : List<String>,
        blocks  : Map<String, InlineBlock>
    ) {
        val body = buildString {
            // ── Header ────────────────────────────────────────────────
            appendLine(title)
            appendLine()
            if (tags.isNotEmpty()) {
                appendLine("Tags: ${tags.joinToString(", ")}")
                appendLine()
            }

            // ── Reconstruct raw content so markers are present ────────
            // content is already logicalContent (markers stripped on load).
            // buildRawContent() re-appends ⟦block:TODO:uuid⟧ etc. so
            // parse() can find and walk them.
            val rawContent = DocumentParser.buildRawContent(content, blocks.values.toList())
            val nodes = DocumentParser.parse(rawContent)

            nodes.forEach { node ->
                when (node) {
                    is DocumentNode.Text -> {
                        append(node.text)
                    }
                    is DocumentNode.Block -> {
                        when (node.blockType) {
                            InlineBlockType.TODO -> {
                                val block = blocks[node.blockId] ?: return@forEach
                                val payload = block.payload as? InlineBlockPayload.Todo
                                    ?: return@forEach
                                // Render each checklist item as ☑ / ☐ + text
                                payload.items
                                    .sortedBy { it.sortOrder }
                                    .forEach { item ->
                                        val tick = if (item.isChecked) "☑" else "☐"
                                        appendLine("$tick ${item.text}")
                                    }
                            }
                            InlineBlockType.IMAGE   -> appendLine("[Image]")
                            InlineBlockType.AUDIO   -> appendLine("[Voice note]")
                            InlineBlockType.DRAWING -> appendLine("[Drawing]")
                        }
                    }
                }
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body.trimEnd())
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

    /**
     * MoveToFolderDialog — bottom sheet for moving the current note to a folder.
     *
     * First row is always "No folder" (clears the folder assignment).
     * Currently assigned folder is highlighted with a checkmark.
     * Selecting any row auto-dismisses — no confirm button needed.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MoveToFolderDialog(
        folders: List<com.greenicephoenix.voidnote.domain.model.Folder>,
        currentFolderName: String?,
        onFolderSelected: (String?) -> Unit,
        onDismiss: () -> Unit
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor   = MaterialTheme.colorScheme.surface,
            dragHandle       = { SheetDragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.large)
                    .padding(bottom = Spacing.extraLarge)
            ) {
                Text(
                    text     = "MOVE TO FOLDER",
                    style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = Spacing.small)
                )

                // "No folder" — always first, removes folder assignment
                FolderPickerRow(
                    name       = "No folder",
                    isSelected = currentFolderName == null,
                    onClick    = { onFolderSelected(null) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )

                if (folders.isEmpty()) {
                    Text(
                        text     = "No folders yet — create one from the main screen.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = Spacing.medium)
                    )
                } else {
                    folders.forEach { folder ->
                        FolderPickerRow(
                            name       = folder.name,
                            isSelected = currentFolderName == folder.name,
                            onClick    = { onFolderSelected(folder.id) }
                        )
                    }
                }
            }
        }
    }

    /**
     * Single row inside MoveToFolderDialog.
     * Shows a folder icon, name, and a checkmark if currently selected.
     */
    @Composable
    private fun FolderPickerRow(
        name: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = Icons.Default.Folder,
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp),
                    tint               = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text  = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            // Checkmark on the currently selected folder
            if (isSelected) {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = "Current folder",
                    modifier           = Modifier.size(18.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    /**
     * NoteColorPicker — a horizontal row of colored dots the user can tap to
     * assign a color accent to the current note.
     *
     * DESIGN (Nothing aesthetic):
     * - 7 dots: one "none" (clear) + 6 NoteColor options
     * - Selected dot has a white ring border; unselected dots have no border
     * - The "none" dot is a small outlined circle (○) — minimal, clear
     * - Dots are 28dp each with 12dp spacing
     *
     * @param currentColor    The currently assigned color, or null for none.
     * @param onColorSelected Called when the user taps a dot. null = clear color.
     */
    @Composable
    fun NoteColorPicker(
        currentColor: NoteColor?,
        onColorSelected: (NoteColor?) -> Unit,
        modifier: Modifier = Modifier   // Sprint 6: allow callers to pass layout constraints
    ) {
        val isDark = isSystemInDarkTheme()

        Column(modifier = modifier) {
            // Section label
            Text(
                text     = "COLOR",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // ── "No color" dot ────────────────────────────────────────────────
                // Shown as an outlined circle (ring) — tapping it clears the color
                val noneSelected = currentColor == null
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(
                            width  = if (noneSelected) 2.dp else 1.dp,
                            color  = if (noneSelected)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            shape  = CircleShape
                        )
                        .clickable { onColorSelected(null) },
                    contentAlignment = Alignment.Center
                ) {
                    // "X" mark inside the no-color circle so it's clearly "remove color"
                    if (noneSelected) {
                        Icon(
                            imageVector        = Icons.Default.Close,
                            contentDescription = "No color",
                            modifier           = Modifier.size(12.dp),
                            tint               = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ── Color dots ────────────────────────────────────────────────────
                NoteColor.entries.forEach { color ->
                    val isSelected = currentColor == color
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            // White selection ring around the active color dot
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .padding(if (isSelected) 3.dp else 0.dp)  // Inset so ring doesn't clip
                            .clip(CircleShape)
                            .background(color.pickerColor)
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        }
    }

    /**
     * SheetDragHandle — shared drag handle for all ModalBottomSheets in this file.
     *
     * Matches the Nothing aesthetic: minimal 32×3dp pill, low-contrast.
     * Defined once here rather than inline in every sheet.
     */
    @Composable
    private fun SheetDragHandle() {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.medium, bottom = Spacing.small),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(width = 32.dp, height = 3.dp),
                shape    = MaterialTheme.shapes.extraLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            ) {}
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// LINKED NOTES STRIP  (Sprint 11)
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * LinkedNotesStrip — horizontal row of chips for notes already linked to this one.
     *
     * Shown just above the tags bar whenever linkedNotePreviews is non-empty.
     * Hidden entirely when there are no links — no wasted space.
     *
     * Each chip shows the linked note's title.
     *   • Tap chip         → navigate to that note's editor
     *   • Long-press chip  → shows an "Unlink" confirmation via a simple dialog
     *     (we avoid a swipe gesture here — it would conflict with horizontal scroll)
     *
     * @param notes       Resolved previews (id + title) from NoteEditorUiState.linkedNotePreviews
     * @param onNoteClick Navigate to the linked note
     * @param onUnlink    Remove this link
     */
    @OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
    @Composable
    private fun LinkedNotesStrip(
        notes: List<LinkedNotePreview>,
        onNoteClick: (noteId: String) -> Unit,
        onUnlink: (noteId: String) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // Track which note ID the unlink confirmation is showing for (null = hidden)
        var unlinkTargetId by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall)
        ) {
            // Section label
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Link,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier           = Modifier.size(12.dp)
                )
                Text(
                    text  = "LINKED NOTES",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Spacer(Modifier.height(Spacing.extraSmall))

            // Chips — wrap to next line if many links
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalArrangement   = Arrangement.spacedBy(Spacing.extraSmall),
                modifier              = Modifier.padding(bottom = Spacing.extraSmall)
            ) {
                notes.forEach { note ->
                    SuggestionChip(
                        onClick      = { onNoteClick(note.id) },
                        label        = {
                            Text(
                                text  = note.title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        },
                        // Long-press triggers unlink confirmation
                        modifier = Modifier.combinedClickable(
                            onClick      = { onNoteClick(note.id) },
                            onLongClick  = { unlinkTargetId = note.id }
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled     = true,
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // Unlink confirmation dialog — shown on long-press
        unlinkTargetId?.let { id ->
            val note = notes.find { it.id == id }
            AlertDialog(
                onDismissRequest = { unlinkTargetId = null },
                title            = { Text("Remove link?") },
                text             = { Text("Unlink \"${note?.title ?: "this note"}\"?") },
                confirmButton    = {
                    TextButton(onClick = { onUnlink(id); unlinkTargetId = null }) {
                        Text("Unlink", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton    = {
                    TextButton(onClick = { unlinkTargetId = null }) { Text("Cancel") }
                }
            )
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// LINK PICKER SHEET  (Sprint 11)
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * LinkPickerSheet — ModalBottomSheet listing all notes so the user can
     * link or unlink them from the current note.
     *
     * DESIGN:
     *   • Search box at top — filters by title, instant, no debounce needed
     *   • Scrollable list of all notes (excluding current note, already filtered in VM)
     *   • Each row shows the note title + a checkbox / checkmark indicating link state
     *   • Tapping a row toggles the link immediately (no confirm needed — linking
     *     is reversible via long-press on the chip in the strip)
     *   • Empty state if no notes exist yet
     *
     * @param allNotes      All notes available to link (pre-filtered by ViewModel)
     * @param linkedNoteIds IDs currently linked — used to show checkmarks
     * @param onLink        Called when user taps an unlinked note
     * @param onUnlink      Called when user taps an already-linked note
     * @param onDismiss     Close the sheet
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LinkPickerSheet(
        allNotes: List<LinkedNotePreview>,
        linkedNoteIds: List<String>,
        onLink: (noteId: String) -> Unit,
        onUnlink: (noteId: String) -> Unit,
        onDismiss: () -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }

        // Filter the list reactively as the user types — case-insensitive title match
        val filtered = remember(allNotes, searchQuery) {
            if (searchQuery.isBlank()) allNotes
            else allNotes.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor   = MaterialTheme.colorScheme.surface,
            dragHandle       = { SheetDragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // ── Header ────────────────────────────────────────────────────────
                Text(
                    text     = "LINK NOTE",
                    style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small)
                )

                // ── Search box ───────────────────────────────────────────────────
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder   = { Text("Search notes…") },
                    leadingIcon   = {
                        Icon(Icons.Default.Search, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    },
                    trailingIcon  = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "Clear search") } }
                    } else null,
                    singleLine    = true,
                    shape         = MaterialTheme.shapes.medium,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium, vertical = Spacing.small)
                )

                // ── Note list ────────────────────────────────────────────────────
                if (allNotes.isEmpty()) {
                    // No other notes exist yet
                    Box(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment  = Alignment.Center
                    ) {
                        Text(
                            text  = "No other notes to link",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else if (filtered.isEmpty()) {
                    // Search returned no results
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "No notes match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    LazyColumnForLinks(
                        notes         = filtered,
                        linkedNoteIds = linkedNoteIds,
                        onLink        = onLink,
                        onUnlink      = onUnlink
                    )
                }
            }
        }
    }

    /**
     * Extracted into its own composable so the @Composable lazy list works
     * correctly inside the ModalBottomSheet Column.
     */
    @Composable
    private fun LazyColumnForLinks(
        notes: List<LinkedNotePreview>,
        linkedNoteIds: List<String>,
        onLink: (noteId: String) -> Unit,
        onUnlink: (noteId: String) -> Unit
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier          = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),   // cap height so sheet doesn't fill screen
            contentPadding    = PaddingValues(
                bottom = Spacing.large
            )
        ) {
            items(notes, key = { it.id }) { note ->
                val isLinked = note.id in linkedNoteIds
                LinkPickerRow(
                    note     = note,
                    isLinked = isLinked,
                    onToggle = {
                        if (isLinked) onUnlink(note.id) else onLink(note.id)
                    }
                )
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    modifier  = Modifier.padding(horizontal = Spacing.medium)
                )
            }
        }
    }

    /**
     * Single row in the link picker list.
     * Shows the note title on the left and a checkmark on the right when linked.
     */
    @Composable
    private fun LinkPickerRow(
        note: LinkedNotePreview,
        isLinked: Boolean,
        onToggle: () -> Unit
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = Spacing.large, vertical = Spacing.medium),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Note title — left side
            Text(
                text     = note.title,
                style    = MaterialTheme.typography.bodyMedium,
                color    = if (isLinked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            // Linked indicator — right side
            if (isLinked) {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = "Linked",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier
                        .padding(start = Spacing.medium)
                        .size(20.dp)
                )
            }
        }
    }