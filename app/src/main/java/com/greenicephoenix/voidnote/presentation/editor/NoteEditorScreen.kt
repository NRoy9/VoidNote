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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.domain.model.FormatType
import com.greenicephoenix.voidnote.domain.model.InlineBlockType
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.greenicephoenix.voidnote.data.storage.VoidNoteImageLoader
import com.greenicephoenix.voidnote.di.ImageLoaderEntryPoint
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dagger.hilt.android.EntryPointAccessors

/**
 * Note Editor Screen
 *
 * PERMISSION ARCHITECTURE:
 * Camera is a "dangerous" permission — it must be requested at runtime even if
 * declared in the manifest. Accompanist PermissionState tracks three states:
 *
 *   GRANTED          → launch camera immediately
 *   NOT GRANTED      → one of two sub-cases:
 *     shouldShowRationale = true  → denied once — show rationale dialog first,
 *                                   then request again on "OK"
 *     shouldShowRationale = false → permanently denied (or first time) — if first
 *                                   time: request directly; if permanently denied:
 *                                   must send user to App Settings (system dialog
 *                                   won't appear again)
 *
 * We distinguish "first time" vs "permanently denied" by tracking whether we've
 * ever asked in this session via a remembered Boolean.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class,
    ExperimentalPermissionsApi::class)
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // ── VoidNoteImageLoader via Hilt EntryPoint ──────────────────────────────
    val imageLoader: VoidNoteImageLoader = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ImageLoaderEntryPoint::class.java
        ).imageLoader()
    }

    // ── Camera permission state (Accompanist) ────────────────────────────────
    // rememberPermissionState tracks: isGranted, shouldShowRationale.
    // The lambda fires after the system permission dialog closes.
    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    ) { isGranted ->
        // Called when the system dialog closes.
        // If the user just granted → immediately launch the camera.
        if (isGranted) {
            val captureUri = viewModel.prepareCameraCapture()
            captureUri?.let { pendingCameraUri ->
                // We can't launch directly here (no launcher reference in this scope).
                // Store the URI in UiState; a LaunchedEffect below watches for it.
                viewModel.storePendingCameraUri(pendingCameraUri)
            }
        }
        // If denied: cameraPermissionState.status.shouldShowRationale will be true
        // on the next tap, and we show the rationale dialog then.
    }

    // Track whether we should show our rationale dialog.
    // This is separate from shouldShowRationale — it's our own UI state.
    var showCameraRationale by remember { mutableStateOf(false) }

    // Track whether we should show the "go to settings" dialog.
    // Shown when: permission is permanently denied (shouldShowRationale = false
    // AND we've already attempted a request this session).
    var showCameraSettingsDialog by remember { mutableStateOf(false) }

    // Track if we have ever launched a permission request this session.
    // Distinguishes "first time (ask directly)" from "permanently denied (open Settings)".
    var hasRequestedCameraPermission by remember { mutableStateOf(false) }

    // ── Gallery photo picker ─────────────────────────────────────────────────
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.insertImageBlock(it) }
    }

    // ── Camera capture launcher ──────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captureSuccess ->
        val tempPath = uiState.cameraCaptureTempPath
        if (captureSuccess && tempPath != null) {
            viewModel.insertCameraImage(tempPath)
        } else {
            viewModel.clearCameraCapturePath()
        }
    }

    // ── Watch for a pending camera URI set after permission granted ──────────
    // When cameraPermissionState callback grants permission, prepareCameraCapture()
    // stores the URI in uiState.pendingCameraUri. We launch here where we have
    // access to cameraLauncher.
    LaunchedEffect(uiState.pendingCameraUri) {
        uiState.pendingCameraUri?.let { uri ->
            cameraLauncher.launch(uri)
            viewModel.clearPendingCameraUri()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHeadingMenu by remember { mutableStateOf(false) }
    var showInsertSheet by remember { mutableStateOf(false) }

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
    val sortedBlocks = remember(uiState.blocks) {
        uiState.blocks.values.sortedBy { it.createdAt }
    }

    // ── Camera tap handler — the smart permission flow ───────────────────────
    // Called when user taps the Camera button in InsertBlockSheet.
    // Decides what to do based on current permission state:
    //
    //   GRANTED                                → launch camera directly
    //   NOT GRANTED + shouldShowRationale=true → show our rationale dialog
    //   NOT GRANTED + shouldShowRationale=false:
    //       first time asking                  → ask system directly
    //       already asked before               → show "go to settings" dialog
    val onCameraClick: () -> Unit = {
        when {
            // Already granted — skip all dialogs, launch immediately
            cameraPermissionState.status.isGranted -> {
                val captureUri = viewModel.prepareCameraCapture()
                captureUri?.let { cameraLauncher.launch(it) }
            }

            // Denied once — shouldShowRationale = true.
            // Android sets this after first denial to prompt you to explain
            // WHY you need the permission before asking again.
            cameraPermissionState.status.shouldShowRationale -> {
                showCameraRationale = true
            }

            // Not granted, rationale not required.
            // Two sub-cases: first time (ask directly) or permanently denied.
            else -> {
                if (!hasRequestedCameraPermission) {
                    // First time — ask the system directly
                    hasRequestedCameraPermission = true
                    cameraPermissionState.launchPermissionRequest()
                } else {
                    // Permanently denied — system dialog won't appear.
                    // Must guide user to App Settings.
                    showCameraSettingsDialog = true
                }
            }
        }
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

                InsertBlockSheet(
                    visible = showInsertSheet,
                    onDismiss = { showInsertSheet = false },
                    onChecklistClick = {
                        showInsertSheet = false
                        viewModel.insertTodoBlock()
                    },
                    onGalleryClick = {
                        showInsertSheet = false
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCameraClick = {
                        showInsertSheet = false
                        onCameraClick()
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
                    contentFieldValue = newValue
                    viewModel.onContentChange(newValue.text)
                },
                placeholder = "Start writing...",
                textStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                formats = uiState.contentFormats,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
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
                                onToggleItem = { itemId -> viewModel.toggleTodoItem(block.id, itemId) },
                                onAddItem = { viewModel.addTodoItem(block.id) },
                                onUpdateItemText = { itemId, newText -> viewModel.updateTodoItemText(block.id, itemId, newText) },
                                onDeleteItem = { itemId -> viewModel.deleteTodoItem(block.id, itemId) },
                                onDeleteBlock = { viewModel.deleteBlock(block.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        InlineBlockType.IMAGE -> {
                            ImageBlockComposable(
                                block = block,
                                voidNoteImageLoader = imageLoader,
                                onCaptionChange = { newCaption ->
                                    viewModel.updateImageCaption(block.id, newCaption)
                                },
                                onDeleteBlock = { viewModel.deleteBlock(block.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> { /* AUDIO — next session */ }
                    }
                }
            }

            Spacer(Modifier.height(200.dp))
        }

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

    // ── Delete dialog ────────────────────────────────────────────────────────
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

    // ── Camera rationale dialog ──────────────────────────────────────────────
    // Shown when user denied permission once. Explains WHY camera is needed
    // (note it's for private capture that never goes to gallery), then
    // re-requests on "Allow". User can tap "Not now" to dismiss.
    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Camera Access") },
            text = {
                Text(
                    "Void Note needs camera access to capture photos directly into your notes.\n\n" +
                            "Photos are encrypted immediately and never saved to your gallery — " +
                            "your captures stay private."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraRationale = false
                        hasRequestedCameraPermission = true
                        cameraPermissionState.launchPermissionRequest()
                    }
                ) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showCameraRationale = false }) { Text("Not now") }
            }
        )
    }

    // ── Permanently denied dialog ────────────────────────────────────────────
    // Shown when the system will no longer show the permission dialog.
    // The only option is to open App Settings where the user can manually grant.
    if (showCameraSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showCameraSettingsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Camera Permission Required") },
            text = {
                Text(
                    "Camera access was denied. To capture photos into notes, " +
                            "please enable Camera permission in App Settings.\n\n" +
                            "Settings → Permissions → Camera → Allow"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraSettingsDialog = false
                        // Open this app's page in Android Settings
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showCameraSettingsDialog = false }) { Text("Cancel") }
            }
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
    onCameraClick: () -> Unit
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
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close insert panel",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

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
                        contentDescription = "Gallery",
                        available = true,
                        onClick = onGalleryClick
                    )
                    InsertBlockButton(
                        icon = Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        available = true,
                        onClick = onCameraClick
                    )
                    InsertBlockButton(
                        icon = Icons.Default.KeyboardVoice,
                        contentDescription = "Voice (coming soon)",
                        available = false
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED COMPOSABLES
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
// FORMATTING TOOLBAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormattingToolbar(
    isBoldActive: Boolean,
    isItalicActive: Boolean,
    isUnderlineActive: Boolean,
    isStrikethroughActive: Boolean,
    activeHeading: FormatType?,
    hasSelection: Boolean,
    showInsertSheet: Boolean,
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onStrikethroughClick: () -> Unit,
    onHeadingClick: () -> Unit,
    onClearClick: () -> Unit,
    onInsertClick: () -> Unit,
    onTodoClick: () -> Unit,
    wordCount: Int = 0,
    charCount: Int = 0
) {
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
                ToolbarSeparator()
                FormatButton(active = activeHeading != null, onClick = onHeadingClick) {
                    Icon(Icons.Default.FormatSize, "Text size", modifier = Modifier.size(18.dp),
                        tint = if (activeHeading != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                if (hasSelection) {
                    FormatButton(active = false, onClick = onClearClick) {
                        Icon(Icons.Default.FormatClear, "Clear formatting", modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                ToolbarSeparator()
                FilledTonalIconButton(
                    onClick = onInsertClick,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (showInsertSheet) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(Icons.Default.Add, "Insert block",
                        modifier = Modifier.size(18.dp),
                        tint = if (showInsertSheet) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${wordCount}w  ${charCount}c",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.padding(end = 4.dp)
                )
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