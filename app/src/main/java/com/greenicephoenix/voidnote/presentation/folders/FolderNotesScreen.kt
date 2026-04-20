// app/src/main/java/com/greenicephoenix/voidnote/presentation/folders/FolderNotesScreen.kt

package com.greenicephoenix.voidnote.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.presentation.components.FolderNotesEmptyState
import com.greenicephoenix.voidnote.presentation.components.NoteQuickActionsSheet
import com.greenicephoenix.voidnote.presentation.components.SwipeableNoteCard
import kotlinx.coroutines.launch

/**
 * FolderNotesScreen — shows all notes inside a specific folder.
 *
 * SPRINT 15 CHANGES:
 * The ⋮ menu now has password-related items that appear based on whether
 * the folder is currently password-protected:
 *
 * If NOT protected:
 *   - Set Password
 *
 * If protected:
 *   - Lock Folder  (navigates back, folder re-locks)
 *   - Change Password
 *   - Remove Password
 *
 * Three bottom sheets handle the password operations:
 *   - SetPasswordSheet    — new password + confirm
 *   - ChangePasswordSheet — current + new + confirm
 *   - RemovePasswordSheet — current password only (just to verify)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderNotesScreen(
    folderId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    viewModel: FolderNotesViewModel = hiltViewModel()
) {
    val uiState              by viewModel.uiState.collectAsState()
    val showRenameDialog     by viewModel.showRenameDialog.collectAsState()
    val renameText           by viewModel.renameText.collectAsState()
    val showDeleteDialog     by viewModel.showDeleteDialog.collectAsState()

    // Sprint 15 — password sheet visibility
    val showSetPasswordSheet    by viewModel.showSetPasswordSheet.collectAsState()
    val showChangePasswordSheet by viewModel.showChangePasswordSheet.collectAsState()
    val showRemovePasswordSheet by viewModel.showRemovePasswordSheet.collectAsState()
    val passwordError           by viewModel.passwordError.collectAsState()

    var quickActionNote   by remember { mutableStateOf<Note?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope    = rememberCoroutineScope()
    var menuExpanded      by remember { mutableStateOf(false) }

    LaunchedEffect(folderId) { viewModel.loadFolder(folderId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text  = uiState.folderName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text  = "${uiState.notes.size} ${if (uiState.notes.size == 1) "note" else "notes"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
                        }
                        DropdownMenu(
                            expanded          = menuExpanded,
                            onDismissRequest  = { menuExpanded = false }
                        ) {
                            // ── Always visible ──────────────────────────────
                            DropdownMenuItem(
                                text         = { Text("Rename Folder") },
                                leadingIcon  = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                onClick      = { menuExpanded = false; viewModel.openRenameDialog() }
                            )

                            HorizontalDivider()

                            // ── Sprint 15: Password items (context-sensitive) ─
                            if (!uiState.isPasswordProtected) {
                                // Folder has no password — offer to set one
                                DropdownMenuItem(
                                    text        = { Text("Set Password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    onClick     = { menuExpanded = false; viewModel.openSetPasswordSheet() }
                                )
                            } else {
                                // Folder has a password — offer lock, change, remove
                                DropdownMenuItem(
                                    text        = { Text("Lock Folder") },
                                    leadingIcon = { Icon(Icons.Default.LockOpen, null) },
                                    onClick     = {
                                        menuExpanded = false
                                        viewModel.lockFolder(onNavigateBack)
                                    }
                                )
                                DropdownMenuItem(
                                    text        = { Text("Change Password") },
                                    leadingIcon = { Icon(Icons.Default.Password, null) },
                                    onClick     = { menuExpanded = false; viewModel.openChangePasswordSheet() }
                                )
                                DropdownMenuItem(
                                    text        = {
                                        Text("Remove Password", color = MaterialTheme.colorScheme.error)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick     = { menuExpanded = false; viewModel.openRemovePasswordSheet() }
                                )
                                HorizontalDivider()
                            }

                            // ── Always visible ──────────────────────────────
                            DropdownMenuItem(
                                text        = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick     = { menuExpanded = false; viewModel.openDeleteDialog() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { viewModel.createNoteInFolder(folderId, onNavigateToEditor) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create note in folder")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading   -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.notes.isEmpty() -> FolderNotesEmptyState(
                    folderName = uiState.folderName,
                    modifier   = Modifier.align(Alignment.Center)
                )
                else -> FolderNotesContent(
                    notes       = uiState.notes,
                    onNoteClick = { note -> onNavigateToEditor(note.id) },
                    onTogglePin = { noteId -> viewModel.togglePin(noteId) },
                    onArchive   = { note ->
                        viewModel.archiveNote(note.id)
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message     = "\"${note.title.ifBlank { "Note" }}\" archived",
                                actionLabel = "Undo",
                                duration    = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.undoArchive(note.id)
                        }
                    },
                    onLongPress = { note -> quickActionNote = note }
                )
            }
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    if (showRenameDialog) {
        RenameFolderDialog(
            currentName  = renameText,
            onNameChange = { viewModel.onRenameTextChange(it) },
            onConfirm    = { viewModel.confirmRename() },
            onDismiss    = { viewModel.dismissRenameDialog() }
        )
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        DeleteFolderDialog(
            folderName = uiState.folderName,
            noteCount  = uiState.notes.size,
            onConfirm  = { viewModel.confirmDelete(onNavigateBack) },
            onDismiss  = { viewModel.dismissDeleteDialog() }
        )
    }

    // ── Sprint 15: Password bottom sheets ─────────────────────────────────────
    if (showSetPasswordSheet) {
        SetPasswordSheet(
            error     = passwordError,
            onConfirm = { new, confirm -> viewModel.setPassword(new, confirm) },
            onDismiss = { viewModel.dismissSetPasswordSheet() }
        )
    }

    if (showChangePasswordSheet) {
        ChangePasswordSheet(
            error     = passwordError,
            onConfirm = { current, new, confirm -> viewModel.changePassword(current, new, confirm) },
            onDismiss = { viewModel.dismissChangePasswordSheet() }
        )
    }

    if (showRemovePasswordSheet) {
        RemovePasswordSheet(
            error     = passwordError,
            onConfirm = { current -> viewModel.removePassword(current) },
            onDismiss = { viewModel.dismissRemovePasswordSheet() }
        )
    }

    // ── Quick actions sheet ───────────────────────────────────────────────────
    quickActionNote?.let { note ->
        NoteQuickActionsSheet(
            note        = note,
            onDismiss   = { quickActionNote = null },
            onTogglePin = { viewModel.togglePin(note.id); quickActionNote = null },
            onArchive   = {
                viewModel.archiveNote(note.id)
                quickActionNote = null
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message     = "\"${note.title.ifBlank { "Note" }}\" archived",
                        actionLabel = "Undo",
                        duration    = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoArchive(note.id)
                }
            },
            onDelete = { viewModel.moveToTrash(note.id); quickActionNote = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTE LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderNotesContent(
    notes: List<Note>,
    onNoteClick: (Note) -> Unit,
    onTogglePin: (String) -> Unit,
    onArchive: (Note) -> Unit,
    onLongPress: (Note) -> Unit
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(notes, key = { it.id }) { note ->
            SwipeableNoteCard(
                note        = note,
                onNoteClick = { onNoteClick(note) },
                onTogglePin = { onTogglePin(note.id) },
                onArchive   = { onArchive(note) },
                onLongClick = { onLongPress(note) }
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOGS (unchanged from before)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameFolderDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier         = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 3.dp),
                    shape    = MaterialTheme.shapes.extraLarge,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)
        ) {
            Text(
                text     = "RENAME FOLDER",
                style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedTextField(
                value         = currentName,
                onValueChange = onNameChange,
                label         = { Text("Folder name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConfirm, enabled = currentName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Rename")
            }
        }
    }
}

@Composable
private fun DeleteFolderDialog(
    folderName: String,
    noteCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val bodyText = when {
        noteCount == 0 -> "\"$folderName\" is empty and will be deleted."
        noteCount == 1 -> "\"$folderName\" will be deleted. The 1 note inside will be moved to Trash — you can restore it from there."
        else           -> "\"$folderName\" will be deleted. The $noteCount notes inside will be moved to Trash — you can restore them from there."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete Folder?") },
        text  = { Text(bodyText) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SPRINT 15: PASSWORD BOTTOM SHEETS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * SetPasswordSheet — used when a folder has no password yet.
 * Takes a new password + confirmation field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetPasswordSheet(
    error: String?,
    onConfirm: (newPassword: String, confirmPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPassword     by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    PasswordBottomSheet(
        title   = "SET PASSWORD",
        error   = error,
        onDismiss = onDismiss,
        confirmLabel = "Set Password",
        onConfirm = { onConfirm(newPassword, confirmPassword) },
        confirmEnabled = newPassword.isNotBlank() && confirmPassword.isNotBlank()
    ) {
        PasswordField(
            value       = newPassword,
            onValueChange = { newPassword = it },
            label       = "New password"
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            value       = confirmPassword,
            onValueChange = { confirmPassword = it },
            label       = "Confirm password"
        )
    }
}

/**
 * ChangePasswordSheet — verifies current password before setting a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordSheet(
    error: String?,
    onConfirm: (currentPassword: String, newPassword: String, confirmPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword     by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    PasswordBottomSheet(
        title   = "CHANGE PASSWORD",
        error   = error,
        onDismiss = onDismiss,
        confirmLabel = "Change Password",
        onConfirm = { onConfirm(currentPassword, newPassword, confirmPassword) },
        confirmEnabled = currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank()
    ) {
        PasswordField(
            value         = currentPassword,
            onValueChange = { currentPassword = it },
            label         = "Current password"
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            value         = newPassword,
            onValueChange = { newPassword = it },
            label         = "New password"
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            value         = confirmPassword,
            onValueChange = { confirmPassword = it },
            label         = "Confirm new password"
        )
    }
}

/**
 * RemovePasswordSheet — just verifies the current password before clearing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovePasswordSheet(
    error: String?,
    onConfirm: (currentPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }

    PasswordBottomSheet(
        title   = "REMOVE PASSWORD",
        error   = error,
        onDismiss = onDismiss,
        confirmLabel = "Remove Password",
        onConfirm = { onConfirm(currentPassword) },
        confirmEnabled = currentPassword.isNotBlank()
    ) {
        Text(
            text  = "Enter your current folder password to confirm removal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        PasswordField(
            value         = currentPassword,
            onValueChange = { currentPassword = it },
            label         = "Current password"
        )
    }
}

/**
 * PasswordBottomSheet — shared shell used by all three password sheets above.
 * Keeps the header, drag handle, error display, and button layout consistent.
 *
 * @param title          Spaced-caps label at the top (e.g. "SET PASSWORD")
 * @param error          Non-null = show red error text above the confirm button
 * @param confirmLabel   Text on the confirm button
 * @param confirmEnabled Enables the confirm button when all required fields are filled
 * @param content        The password fields — provided by each specific sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordBottomSheet(
    title: String,
    error: String?,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier         = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 3.dp),
                    shape    = MaterialTheme.shapes.extraLarge,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text     = title,
                style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // The specific fields for each sheet type
            content()

            // Error message — shown when ViewModel sets a non-null passwordError
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = onConfirm,
                enabled  = confirmEnabled,
                modifier = Modifier.fillMaxWidth()
            ) { Text(confirmLabel) }
        }
    }
}

/**
 * PasswordField — reusable obscured text field for all password inputs.
 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value                = value,
        onValueChange        = onValueChange,
        label                = { Text(label) },
        singleLine           = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier             = Modifier.fillMaxWidth()
    )
}