package com.greenicephoenix.voidnote.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * ImportBackupScreen — Settings → Data Management → Import Backup
 *
 * Flow B: merges a .vnbackup into an already-unlocked vault.
 *
 * ─── SCREEN STATES ────────────────────────────────────────────────────────────
 *
 * The entire screen is driven by ImportBackupUiState from ImportBackupViewModel.
 * There are NO separate Boolean flags here — the sealed class handles transitions.
 *
 *   Idle          → "Choose Backup File" button, instructions text
 *   ReadingHeader → full-screen progress indicator (reading ZIP header)
 *   FileReady     → backup info card + password field + Import button
 *                   (errorMessage shows inline if password is wrong)
 *                   (isVerifying shows spinner in Import button)
 *   Importing     → non-dismissible progress dialog
 *                   ("Do not close the app" message)
 *   Success       → success card with counts, "Done" button → popBackStack
 *   Error         → error card, "Try Another File" button resets to Idle
 *
 * ─── IMPORTANT: ACTIVITYRESULTLAUNCHER ────────────────────────────────────────
 *
 * The OpenDocument launcher MUST be created in a Composable — it cannot be
 * called from the ViewModel. We register it here with rememberLauncherForActivityResult
 * and trigger it when the user taps "Choose Backup File".
 * The result URI is passed to viewModel.onFileSelected().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportBackupViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsState()
    val password    by viewModel.password.collectAsState()
    val showPassword by viewModel.showPassword.collectAsState()
    val context = LocalContext.current

    // ── File picker launcher ──────────────────────────────────────────────────
    // "application/octet-stream" matches .vnbackup files.
    // We also pass an array of MIME types for broader compatibility across
    // file managers — some may not recognise the custom extension.
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Try to get a human-readable filename from the URI
            val displayName = try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (it.moveToFirst() && nameIndex >= 0) it.getString(nameIndex) else null
                }
            } catch (e: Exception) { null }

            viewModel.onFileSelected(
                uri             = uri,
                contentResolver = context.contentResolver,
                displayName     = displayName
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Backup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {

            when (val state = uiState) {

                // ── IDLE: no file chosen yet ──────────────────────────────────
                is ImportBackupUiState.Idle -> {
                    IdleContent(
                        onChooseFile = {
                            fileLauncher.launch(
                                // Both MIME types ensure maximum file manager compatibility
                                arrayOf("application/octet-stream", "*/*")
                            )
                        }
                    )
                }

                // ── READING HEADER: spinner while ZIP header is read ──────────
                is ImportBackupUiState.ReadingHeader -> {
                    Box(
                        modifier          = Modifier.fillMaxSize(),
                        contentAlignment  = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text  = "Reading backup file…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // ── FILE READY: info card + password entry ────────────────────
                is ImportBackupUiState.FileReady -> {
                    FileReadyContent(
                        state        = state,
                        password     = password,
                        showPassword = showPassword,
                        onChooseFile = {
                            fileLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        onPasswordChange    = viewModel::onPasswordChange,
                        onToggleShowPassword = viewModel::toggleShowPassword,
                        onImport = {
                            viewModel.confirmImport(context.contentResolver)
                        }
                    )
                }

                // ── SUCCESS: show import summary ──────────────────────────────
                is ImportBackupUiState.Success -> {
                    SuccessContent(
                        state    = state,
                        onDone   = onNavigateBack
                    )
                }

                // ── ERROR: show error and allow retry ─────────────────────────
                is ImportBackupUiState.Error -> {
                    ErrorContent(
                        message      = state.message,
                        onTryAgain   = viewModel::resetToIdle,
                        onNavigateBack = onNavigateBack
                    )
                }

                // ── IMPORTING: non-dismissible progress dialog ────────────────
                // Note: this is rendered as a Dialog overlay BELOW in the same Box,
                // not as a separate composable path, so the underlying FileReady
                // content stays visible behind it.
                else -> Unit
            }

            // ── Importing progress dialog — shown on top of any underlying state ──
            if (uiState is ImportBackupUiState.Importing) {
                ImportProgressDialog()
            }
        }
    }
}

// ─── Idle content ──────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(onChooseFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.large)
    ) {
        Spacer(modifier = Modifier.height(Spacing.extraLarge))

        // Icon
        Icon(
            imageVector        = Icons.Default.CloudDownload,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        // Title + description
        Text(
            text      = "Import Backup",
            style     = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text      = "Merge notes from a .vnbackup file into your current vault.\n\n" +
                    "Your existing notes will not be deleted. " +
                    "New notes will be added and duplicates will be skipped.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Info card — explains what happens with duplicates
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Text(
                    text       = "Merge rules",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                MergeRuleRow(
                    icon  = Icons.Default.Add,
                    label = "New note — added to your vault"
                )
                MergeRuleRow(
                    icon  = Icons.Default.ContentCopy,
                    label = "Same note, edited elsewhere — saved as copy with \"(Restored)\" suffix"
                )
                MergeRuleRow(
                    icon  = Icons.Default.CheckCircle,
                    label = "Identical note already on device — skipped"
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Primary CTA
        Button(
            onClick  = onChooseFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector        = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier           = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.small))
            Text("Choose Backup File")
        }
    }
}

@Composable
private fun MergeRuleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        verticalAlignment   = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(16.dp).padding(top = 2.dp),
            tint               = MaterialTheme.colorScheme.primary
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// ─── File ready content ────────────────────────────────────────────────────────

@Composable
private fun FileReadyContent(
    state: ImportBackupUiState.FileReady,
    password: String,
    showPassword: Boolean,
    onChooseFile: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleShowPassword: () -> Unit,
    onImport: () -> Unit
) {
    val canImport = password.isNotEmpty() && !state.isVerifying

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {

        // ── Backup info card ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(Spacing.large)) {

                // File name row with "Change" button
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment  = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp),
                            tint               = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text     = state.fileName,
                            style    = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onChooseFile) {
                        Text("Change")
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = Spacing.small),
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                // Note and folder count row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BackupStatItem(
                        value = state.noteCount.toString(),
                        label = "Notes"
                    )
                    BackupStatItem(
                        value = state.folderCount.toString(),
                        label = "Folders"
                    )
                    BackupStatItem(
                        value = "v${state.appVersion}",
                        label = "Version"
                    )
                }
            }
        }

        // ── Password field ────────────────────────────────────────────────────
        Text(
            text  = "Enter this backup's vault password to import its notes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        OutlinedTextField(
            value         = password,
            onValueChange = onPasswordChange,
            label         = { Text("Backup vault password") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            isError       = state.errorMessage != null,
            supportingText = state.errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleShowPassword) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        // ── Import button ─────────────────────────────────────────────────────
        Button(
            onClick  = onImport,
            enabled  = canImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isVerifying) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(18.dp),
                    color     = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("Verifying…")
            } else {
                Icon(
                    imageVector        = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("Import Backup")
            }
        }
    }
}

@Composable
private fun BackupStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// ─── Non-dismissible importing progress dialog ─────────────────────────────────

@Composable
private fun ImportProgressDialog() {
    Dialog(
        onDismissRequest = {},  // intentionally empty — cannot dismiss during import
        properties = DialogProperties(
            dismissOnBackPress    = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.large)
            ) {
                CircularProgressIndicator()
                Text(
                    text       = "Importing notes…",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text      = "Re-encrypting notes for your vault.\nDo not close the app.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Success content ───────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(
    state: ImportBackupUiState.Success,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Icon(
            imageVector        = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        Text(
            text       = "Import Complete",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                SummaryRow(
                    icon  = Icons.Default.Add,
                    label = "Notes imported",
                    value = state.notesImported.toString()
                )
                if (state.foldersImported > 0) {
                    SummaryRow(
                        icon  = Icons.Default.Folder,
                        label = "Folders imported",
                        value = state.foldersImported.toString()
                    )
                }
                if (state.skippedDuplicates > 0) {
                    SummaryRow(
                        icon  = Icons.Default.CheckCircle,
                        label = "Duplicates skipped",
                        value = state.skippedDuplicates.toString()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.extraLarge))

        Button(
            onClick  = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun SummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
                tint               = MaterialTheme.colorScheme.primary
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Error content ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    message: String,
    onTryAgain: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        Text(
            text       = "Import Failed",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text      = message,
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onErrorContainer,
                modifier  = Modifier.padding(Spacing.large),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(Spacing.extraLarge))

        Button(
            onClick  = onTryAgain,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Another File")
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        OutlinedButton(
            onClick  = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go Back")
        }
    }
}