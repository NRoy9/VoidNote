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
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

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
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class,
    ExperimentalPermissionsApi::class)
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // SPRINT 5: collect the folder list for the MoveToFolderDialog
    // collectAsState() turns the StateFlow<List<Folder>> into a Compose State —
    // the dialog will recompose whenever folders change.
    val folders by viewModel.folders.collectAsState()
    val context = LocalContext.current

    // ── Singletons via Hilt EntryPoints ───────────────────────────────────────
    val imageLoader: VoidNoteImageLoader = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, ImageLoaderEntryPoint::class.java).imageLoader()
    }
    val audioEntry = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AudioManagerEntryPoint::class.java)
    }
    val audioStorage: AudioStorageManager   = remember { audioEntry.audioStorage() }
    val voiceRecorder: VoiceRecorderManager = remember { audioEntry.voiceRecorder() }

    // ── Camera permission ─────────────────────────────────────────────────────
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA) { isGranted ->
        if (isGranted) {
            val uri = viewModel.prepareCameraCapture()
            uri?.let { viewModel.storePendingCameraUri(it) }
        }
    }
    var showCameraRationale        by remember { mutableStateOf(false) }
    var showCameraSettingsDialog   by remember { mutableStateOf(false) }
    var hasRequestedCameraPermission by remember { mutableStateOf(false) }

    // ── Microphone permission ─────────────────────────────────────────────────
    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO) { isGranted ->
        if (isGranted) viewModel.startRecording()
    }
    var showMicRationale        by remember { mutableStateOf(false) }
    var showMicSettingsDialog   by remember { mutableStateOf(false) }
    var hasRequestedMicPermission by remember { mutableStateOf(false) }

    // ── Gallery launcher ──────────────────────────────────────────────────────
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.insertImageBlock(it) }
    }

    // ── Camera launcher ───────────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
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

    var showDeleteDialog       by remember { mutableStateOf(false) }
    var showHeadingMenu        by remember { mutableStateOf(false) }
    var showInsertSheet        by remember { mutableStateOf(false) }
    // SPRINT 5: controls visibility of the "Move to folder" folder picker dialog
    var showMoveToFolderDialog by remember { mutableStateOf(false) }

    // Declared here — ABOVE onNumberedListClick — because that lambda
    // captures contentFieldValue by reference and Kotlin does not hoist vars.
    var titleFieldValue by remember {
        mutableStateOf(TextFieldValue(text = uiState.title, selection = TextRange(uiState.title.length)))
    }
    var contentFieldValue by remember {
        mutableStateOf(TextFieldValue(text = uiState.content, selection = TextRange(uiState.content.length)))
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
        val text      = contentFieldValue.text
        val cursor    = contentFieldValue.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1   // 0 if no prior newline
        val lineEnd   = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val lineText  = text.substring(lineStart, lineEnd)

        val existingNumPattern = Regex("""^\d+\.\s""")

        if (existingNumPattern.containsMatchIn(lineText)) {
            // ── Toggle OFF: remove the "N. " prefix ──────────────────────────
            val prefixLen  = existingNumPattern.find(lineText)!!.value.length
            val newText    = text.removeRange(lineStart, lineStart + prefixLen)
            val newCursor  = (cursor - prefixLen).coerceAtLeast(lineStart)
            contentFieldValue = TextFieldValue(
                text      = newText,
                selection = TextRange(newCursor)
            )
            viewModel.onContentChange(newText)
        } else {
            // ── Toggle ON: find what number to use ───────────────────────────
            val prevLineEnd   = (lineStart - 1).coerceAtLeast(0)
            val prevLineStart = text.lastIndexOf('\n', prevLineEnd - 1) + 1
            val prevLine      = if (lineStart > 0) text.substring(prevLineStart, prevLineEnd) else ""
            val prevNum       = existingNumPattern.find(prevLine)
                ?.value?.trimEnd()?.dropLast(1)?.toIntOrNull()
            val num    = if (prevNum != null) prevNum + 1 else 1
            val prefix = "$num. "
            val newText   = text.substring(0, lineStart) + prefix + text.substring(lineStart)
            val newCursor = cursor + prefix.length
            contentFieldValue = TextFieldValue(
                text      = newText,
                selection = TextRange(newCursor)
            )
            viewModel.onContentChange(newText)
        }
    }

    LaunchedEffect(uiState.title) {
        if (titleFieldValue.text != uiState.title) titleFieldValue = titleFieldValue.copy(text = uiState.title)
    }
    LaunchedEffect(uiState.content) {
        if (contentFieldValue.text != uiState.content) contentFieldValue = contentFieldValue.copy(text = uiState.content)
    }

    val hasSelection = contentFieldValue.selection.start != contentFieldValue.selection.end
    val sortedBlocks = remember(uiState.blocks) { uiState.blocks.values.sortedBy { it.createdAt } }

    // ── Layout ────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopBar(
                onBackClick         = onNavigateBack,
                isPinned            = uiState.isPinned,
                isArchived          = uiState.isArchived,
                onPinClick          = { viewModel.togglePin() },
                onArchiveClick      = { viewModel.archiveNote(); onNavigateBack() },
                onDeleteClick       = { showDeleteDialog = true },
                onShareClick        = {
                    shareNote(context, uiState.title.ifBlank { "Untitled" }, uiState.content, uiState.tags)
                },
                lastSaved           = uiState.lastSaved,
                // SPRINT 5: pass the folder name for display and the callback to open the dialog
                currentFolderName   = uiState.currentFolderName,
                onMoveToFolderClick = { showMoveToFolderDialog = true }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                TagsSection(
                    tags       = uiState.tags,
                    onAddTag   = { viewModel.addTag(it) },
                    onRemoveTag = { viewModel.removeTag(it) }
                )

                FormattingToolbar(
                    isBoldActive = if (hasSelection) hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.BOLD) else uiState.activeBold,
                    isItalicActive = if (hasSelection) hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.ITALIC) else uiState.activeItalic,
                    isUnderlineActive = if (hasSelection) hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.UNDERLINE) else uiState.activeUnderline,
                    isStrikethroughActive = if (hasSelection) hasFormat(uiState.contentFormats, contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.STRIKETHROUGH) else uiState.activeStrikethrough,
                    activeHeading  = uiState.activeHeading,
                    hasSelection   = hasSelection,
                    showInsertSheet = showInsertSheet,
                    showPreview    = uiState.showPreview,
                    onInsertClick  = { showInsertSheet = true },
                    onBoldClick    = { if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.BOLD) else viewModel.toggleActiveBold() },
                    onItalicClick  = { if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.ITALIC) else viewModel.toggleActiveItalic() },
                    onUnderlineClick = { if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.UNDERLINE) else viewModel.toggleActiveUnderline() },
                    onStrikethroughClick = { if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.STRIKETHROUGH) else viewModel.toggleActiveStrikethrough() },
                    onHeadingClick = { showHeadingMenu = true },
                    onClearClick   = { viewModel.clearAllFormatting() },
                    onTodoClick    = { viewModel.insertTodoBlock() },
                    onNumberedListClick = onNumberedListClick,
                    onPreviewClick = { viewModel.togglePreview() },
                    wordCount      = contentFieldValue.text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size,
                    charCount      = contentFieldValue.text.length
                )

                // RecordingSheet slides in OVER InsertBlockSheet while recording.
                // Both stay in composition so animations work correctly.
                RecordingSheet(
                    isVisible    = uiState.isRecording,
                    elapsedMs    = uiState.recordingElapsedMs,
                    onStopClick  = { viewModel.stopRecording() }
                )

                InsertBlockSheet(
                    visible          = showInsertSheet && !uiState.isRecording,
                    onDismiss        = { showInsertSheet = false },
                    onChecklistClick = { showInsertSheet = false; viewModel.insertTodoBlock() },
                    onGalleryClick   = {
                        showInsertSheet = false
                        imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onCameraClick    = { showInsertSheet = false; onCameraClick() },
                    onVoiceClick     = { showInsertSheet = false; onVoiceClick() }
                )
            }
        }
    ) { paddingValues ->
        if (uiState.showPreview) {
            // ── PREVIEW MODE — read-only styled view ─────────────────────────
            // Shows the note with all FormatRanges rendered visually.
            // Uses applyFormatting() which already lives in TextSpanUtils.kt.
            // SelectionContainer allows the user to copy text from the preview.
            NotePreviewPanel(
                title          = uiState.title,
                content        = uiState.content,
                contentFormats = uiState.contentFormats,
                modifier       = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            // ── EDIT MODE (existing editor) ───────────────────────────────────────
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
                        val cursor  = newValue.selection.start

                        // Auto-continue numbered list when Enter is pressed.
                        // Detect: text grew by exactly 1 char AND that char is \n.
                        val handled = if (
                            newText.length == oldText.length + 1 &&
                            cursor > 0 &&
                            newText[cursor - 1] == '\n'
                        ) {
                            // Find the line that just had Enter pressed at its end
                            val insertedAt    = cursor - 1
                            val prevLineStart = newText.lastIndexOf('\n', insertedAt - 1) + 1
                            val prevLine      = newText.substring(prevLineStart, insertedAt)
                            val numMatch      = Regex("""^(\d+)\.\s""").find(prevLine)
                            if (numMatch != null) {
                                // Previous line was "N. something" → insert "N+1. " on new line
                                val nextNum = (numMatch.groupValues[1].toIntOrNull() ?: 0) + 1
                                val prefix  = "$nextNum. "
                                val finalText = newText.substring(0, cursor) + prefix + newText.substring(cursor)
                                contentFieldValue = TextFieldValue(
                                    text      = finalText,
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
            }
        }

        // ── Heading dialog ────────────────────────────────────────────────────
        if (showHeadingMenu) {
            AlertDialog(
                onDismissRequest = { showHeadingMenu = false },
                title = { Text("Text Size") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = { if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.HEADING_SMALL) else viewModel.setActiveHeading(FormatType.HEADING_SMALL); showHeadingMenu = false }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) { Text("Small", fontSize = 16.sp, modifier = Modifier.padding(16.dp)) }
                        Surface(onClick = { if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.HEADING_NORMAL) else viewModel.setActiveHeading(FormatType.HEADING_NORMAL); showHeadingMenu = false }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) { Text("Normal (Default)", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                        Surface(onClick = { if (hasSelection) viewModel.applyFormatting(contentFieldValue.selection.start, contentFieldValue.selection.end, FormatType.HEADING_LARGE) else viewModel.setActiveHeading(FormatType.HEADING_LARGE); showHeadingMenu = false }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) { Text("Large", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showHeadingMenu = false }) { Text("Cancel") } }
            )
        }
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Note?") },
            text  = { Text("\"${uiState.title.ifBlank { "Untitled" }}\" will be moved to trash.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteNote(); showDeleteDialog = false; onNavigateBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    // Shows a list of all folders. Tapping a folder moves the note there.
    // "No folder" option at the top removes the note from any folder → root level.
    if (showMoveToFolderDialog) {
        MoveToFolderDialog(
            folders           = folders,
            currentFolderName = uiState.currentFolderName,
            onFolderSelected  = { folderId ->
                viewModel.moveToFolder(folderId)
                showMoveToFolderDialog = false
            },
            onDismiss = { showMoveToFolderDialog = false }
        )
    }

    // ── Camera rationale dialog ───────────────────────────────────────────────
    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            icon    = { Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title   = { Text("Camera Access") },
            text    = { Text("Void Note needs camera access to capture photos.\n\nPhotos are encrypted immediately and never saved to your gallery.") },
            confirmButton = { TextButton(onClick = { showCameraRationale = false; hasRequestedCameraPermission = true; cameraPermissionState.launchPermissionRequest() }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { showCameraRationale = false }) { Text("Not now") } }
        )
    }

    // ── Camera permanently denied dialog ─────────────────────────────────────
    if (showCameraSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showCameraSettingsDialog = false },
            icon    = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) },
            title   = { Text("Camera Permission Required") },
            text    = { Text("Camera access was denied.\n\nTo enable: Settings → Permissions → Camera → Allow") },
            confirmButton = { TextButton(onClick = { showCameraSettingsDialog = false; context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }) }) { Text("Open Settings") } },
            dismissButton = { TextButton(onClick = { showCameraSettingsDialog = false }) { Text("Cancel") } }
        )
    }

    // ── Microphone rationale dialog ───────────────────────────────────────────
    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            icon    = { Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary) },
            title   = { Text("Microphone Access") },
            text    = { Text("Void Note needs microphone access to record voice notes.\n\nVoice notes are encrypted immediately — no audio is ever stored without encryption.") },
            confirmButton = { TextButton(onClick = { showMicRationale = false; hasRequestedMicPermission = true; micPermissionState.launchPermissionRequest() }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { showMicRationale = false }) { Text("Not now") } }
        )
    }

    // ── Microphone permanently denied dialog ──────────────────────────────────
    if (showMicSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showMicSettingsDialog = false },
            icon    = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) },
            title   = { Text("Microphone Permission Required") },
            text    = { Text("Microphone access was denied.\n\nTo enable: Settings → Permissions → Microphone → Allow") },
            confirmButton = { TextButton(onClick = { showMicSettingsDialog = false; context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }) }) { Text("Open Settings") } },
            dismissButton = { TextButton(onClick = { showMicSettingsDialog = false }) { Text("Cancel") } }
        )
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
    showInsertSheet: Boolean, showPreview: Boolean,
    onBoldClick: () -> Unit, onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit, onStrikethroughClick: () -> Unit, onHeadingClick: () -> Unit,
    onClearClick: () -> Unit, onInsertClick: () -> Unit, onTodoClick: () -> Unit,
    onNumberedListClick: () -> Unit, onPreviewClick: () -> Unit,
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
                // Word/char count hidden in preview mode
                // Stats: word/char count top line, reading time below.
                // Both are labelSmall (~11sp), so two lines ≈ 24sp — well within the
                // 36dp button height that already drives the Row's height. No bar growth.
                //
                // Reading time formula: average adult reads ~200 words per minute.
                // coerceAtLeast(1) means a blank note shows "~1m" not "~0m".
                if (!showPreview) {
                    val readTimeMin = (wordCount / 200.0).let {
                        if (it < 1.0) 1 else kotlin.math.ceil(it).toInt()
                    }
                    Column(
                        modifier              = Modifier.padding(end = 4.dp),
                        horizontalAlignment   = Alignment.End,
                        verticalArrangement   = Arrangement.Center
                    ) {
                        // Line 1 — word count · character count
                        Text(
                            text  = "${wordCount}w · ${charCount}c",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                        )
                        // Line 2 — estimated reading time
                        Text(
                            text  = "~${readTimeMin} min read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                }
                // Preview toggle — always visible so user can enter/exit preview
                IconButton(onClick = onPreviewClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPreview) "Exit preview" else "Preview formatting",
                        modifier = Modifier.size(18.dp),
                        tint = if (showPreview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
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
    // SPRINT 5: folder info for display + callback to open the dialog
    currentFolderName: String? = null,
    onMoveToFolderClick: () -> Unit = {}
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

@OptIn(ExperimentalLayoutApi::class)
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
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Add Tag") },
            text = { OutlinedTextField(value = tagName, onValueChange = { if (it.length <= 20 && it.all { c -> c.isLetterOrDigit() || c.isWhitespace() }) tagName = it }, label = { Text("Tag name") }, singleLine = true, supportingText = { Text("${tagName.length}/20") }) },
            confirmButton = { TextButton(onClick = { onAddTag(tagName.trim()); showAddDialog = false }, enabled = tagName.trim().isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
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
        if (contentFormats.isEmpty()) androidx.compose.ui.text.AnnotatedString(content)
        else applyFormatting(content, contentFormats)
    }

    SelectionContainer {
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.medium)
        ) {
            Spacer(Modifier.height(Spacing.medium))

            // Title
            Text(
                text  = title.ifBlank { "Untitled Note" },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.medium))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(Spacing.medium))

            // Formatted content
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

private fun shareNote(context: android.content.Context, title: String, content: String, tags: List<String>) {
    val text = buildString {
        appendLine(title); appendLine()
        if (tags.isNotEmpty()) { appendLine("Tags: ${tags.joinToString(", ")}"); appendLine() }
        append(content)
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, title) }, "Share note"))
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
 * MoveToFolderDialog — lets the user move the current note to a folder.
 *
 * ─── HOW IT WORKS ─────────────────────────────────────────────────────────
 * Shows an AlertDialog with a scrollable list of all folders.
 * The first item is always "No folder" — selecting it removes the note from
 * any folder and puts it at root level (folderId = null).
 * The remaining items are the user's actual folders.
 *
 * The currently assigned folder is highlighted with a checkmark icon.
 *
 * ─── DATA FLOW ───────────────────────────────────────────────────────────
 * onFolderSelected(folderId: String?) is called with:
 *   null  → user tapped "No folder"
 *   id    → user tapped a specific folder
 *
 * The ViewModel's moveToFolder() receives this value, calls
 * noteRepository.moveNoteToFolder(), and updates currentFolderName in uiState.
 *
 * @param folders           All available folders (from viewModel.folders StateFlow)
 * @param currentFolderName Name of the folder the note is currently in (null = root)
 * @param onFolderSelected  Called with the chosen folderId (null = root)
 * @param onDismiss         Called when dialog is dismissed without selection
 */
@Composable
private fun MoveToFolderDialog(
    folders: List<com.greenicephoenix.voidnote.domain.model.Folder>,
    currentFolderName: String?,
    onFolderSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Constrain height so the dialog doesn't grow taller than the screen
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ── "No folder" option — always first ────────────────────────
                FolderPickerRow(
                    name      = "No folder",
                    isSelected = currentFolderName == null,
                    onClick   = { onFolderSelected(null) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                if (folders.isEmpty()) {
                    // Edge case: user has no folders yet
                    Text(
                        text     = "No folders created yet.\nCreate one from the main screen.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    folders.forEach { folder ->
                        FolderPickerRow(
                            name      = folder.name,
                            isSelected = currentFolderName == folder.name,
                            onClick   = { onFolderSelected(folder.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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