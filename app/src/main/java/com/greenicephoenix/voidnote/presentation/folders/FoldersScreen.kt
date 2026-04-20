package com.greenicephoenix.voidnote.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.presentation.components.FoldersEmptyState
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * FoldersScreen — Lists all folders. Allows creating and deleting folders.
 *
 * SPRINT 15 CHANGES:
 * - Folder cards show a lock icon when isPasswordProtected() is true.
 * - Tapping a locked folder shows UnlockFolderDialog instead of navigating.
 * - onFolderClick() is now routed through viewModel.onFolderClick() which
 *   decides: navigate directly OR show unlock dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onNavigateBack: () -> Unit,
    onFolderClick: (String) -> Unit = {},
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val uiState             by viewModel.uiState.collectAsState()
    val showDialog          by viewModel.showCreateDialog.collectAsState()
    val newFolderName       by viewModel.newFolderName.collectAsState()
    val pendingDeleteFolder by viewModel.pendingDeleteFolder.collectAsState()

    // Sprint 15 — unlock dialog state
    val pendingUnlockFolder  by viewModel.pendingUnlockFolder.collectAsState()
    val unlockPasswordInput  by viewModel.unlockPasswordInput.collectAsState()
    val wrongPassword        by viewModel.wrongPassword.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create folder")
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.folders.isEmpty() -> {
                    FoldersEmptyState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    FoldersListContent(
                        folders       = uiState.folders,
                        // Sprint 15: route through ViewModel — it decides lock vs open
                        onFolderClick = { folder ->
                            viewModel.onFolderClick(folder, onFolderClick)
                        },
                        onDeleteFolder = { folder -> viewModel.requestDeleteFolder(folder) }
                    )
                }
            }
        }
    }

    // ── Create Folder Dialog ──────────────────────────────────────────────────
    if (showDialog) {
        CreateFolderDialog(
            folderName   = newFolderName,
            onNameChange = { viewModel.onFolderNameChange(it) },
            onDismiss    = { viewModel.hideCreateDialog() },
            onCreate     = { viewModel.createFolder() }
        )
    }

    // ── Delete Confirmation Dialog ────────────────────────────────────────────
    pendingDeleteFolder?.let { folder ->
        DeleteFolderConfirmDialog(
            folder    = folder,
            onConfirm = { viewModel.confirmDeleteFolder() },
            onDismiss = { viewModel.cancelDeleteFolder() }
        )
    }

    // ── Sprint 15: Unlock Password Dialog ────────────────────────────────────
    // Shown when the user taps a locked folder
    pendingUnlockFolder?.let { folder ->
        UnlockFolderDialog(
            folderName    = folder.name,
            passwordInput = unlockPasswordInput,
            onPasswordChange = { viewModel.onUnlockPasswordChange(it) },
            isError       = wrongPassword,
            onConfirm     = { viewModel.submitUnlockPassword(onFolderClick) },
            onDismiss     = { viewModel.dismissUnlockDialog() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOLDER LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FoldersListContent(
    folders: List<Folder>,
    onFolderClick: (Folder) -> Unit,   // Sprint 15: passes whole Folder (not just ID)
    onDeleteFolder: (Folder) -> Unit
) {
    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        items(folders, key = { it.id }) { folder ->
            FolderListItem(
                folder  = folder,
                onClick = { onFolderClick(folder) },
                onDelete = { onDeleteFolder(folder) }
            )
        }
    }
}

@Composable
private fun FolderListItem(
    folder: Folder,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        android.util.Log.d("SPRINT15_UI", "FolderListItem recompose: ${folder.name} protected=${folder.isPasswordProtected()} hash=${folder.passwordHash?.take(6)}")
        Row(
            modifier            = Modifier.fillMaxWidth().padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.weight(1f)
            ) {
                // Sprint 15: lock icon replaces folder icon when protected
                Icon(
                    imageVector = if (folder.isPasswordProtected())
                        Icons.Default.Lock
                    else
                        Icons.Default.Folder,
                    contentDescription = if (folder.isPasswordProtected()) "Locked folder" else null,
                    tint     = if (folder.isPasswordProtected())
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text  = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Sprint 15: subtle "Protected" label under the folder name
                    if (folder.isPasswordProtected()) {
                        Text(
                            text  = "Protected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Delete folder",
                    tint               = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * UnlockFolderDialog — shown when the user taps a password-protected folder.
 *
 * Intentionally simple: just a password field and two buttons.
 * isError=true turns the field red and shows "Incorrect password" below it.
 */
@Composable
private fun UnlockFolderDialog(
    folderName: String,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    isError: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Unlock \"$folderName\"") },
        text = {
            Column {
                OutlinedTextField(
                    value               = passwordInput,
                    onValueChange       = onPasswordChange,
                    label               = { Text("Password") },
                    singleLine          = true,
                    isError             = isError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions     = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText      = if (isError) {
                        { Text("Incorrect password", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier            = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = onConfirm,
                enabled  = passwordInput.isNotBlank()
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteFolderConfirmDialog(
    folder: Folder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text("Delete \"${folder.name}\"?") },
        text  = {
            Text("This folder will be deleted. Any notes inside will be moved to your main notes list — nothing will be lost.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFolderDialog(
    folderName: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier         = Modifier.fillMaxWidth().padding(top = Spacing.medium, bottom = Spacing.small),
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
                .padding(horizontal = Spacing.large)
                .padding(bottom = Spacing.large)
        ) {
            Text(
                text     = "NEW FOLDER",
                style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = Spacing.medium)
            )
            OutlinedTextField(
                value         = folderName,
                onValueChange = onNameChange,
                label         = { Text("Folder name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.medium))
            Button(
                onClick  = onCreate,
                enabled  = folderName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create") }
        }
    }
}