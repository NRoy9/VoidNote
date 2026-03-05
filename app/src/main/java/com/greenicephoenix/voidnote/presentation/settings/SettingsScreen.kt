package com.greenicephoenix.voidnote.presentation.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * SettingsScreen
 *
 * ─── EXPORT FLOW ──────────────────────────────────────────────────────────────
 *
 * The export UI is driven by ExportState from SettingsViewModel.
 * There are NO separate Boolean flags for the export dialogs — the sealed class
 * handles all transitions and impossible states cannot be represented.
 *
 * Launching the file picker (CreateDocument) MUST happen from a Composable —
 * it cannot be called from a ViewModel. So the ViewModel emits ReadyToExport
 * and a LaunchedEffect here observes it and calls the appropriate launcher.
 *
 * Dialogs rendered based on ExportState:
 *   ChoosingFormat      → ExportFormatDialog
 *   ConfirmingPassword  → ExportPasswordDialog (with plain confirm button)
 *   PasswordVerifying   → ExportPasswordDialog (with spinner, fields disabled)
 *   PasswordError       → ExportPasswordDialog (with error message shown)
 *   Exporting           → ExportProgressDialog (non-dismissible)
 *   ExportSuccess       → success snackbar, state reset to Idle
 *   ExportError         → error snackbar, state reset to Idle
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToArchive: () -> Unit = {},
    onNavigateToChangelog: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},            // Flow B: import backup from Settings
    onNavigateToChangePassword: () -> Unit = {},    // Change vault password
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState        by viewModel.uiState.collectAsState()
    val currentTheme   by viewModel.currentTheme.collectAsState()
    val isBiometricEnabled by viewModel.biometricLockEnabled.collectAsState()
    val exportState    by viewModel.exportState.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showThemeDialog    by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    // ── Permission states ─────────────────────────────────────────────────────
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var hasRequestedFromSettings    by remember { mutableStateOf(false) }
    var showSettingsCameraRationale by remember { mutableStateOf(false) }

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var hasRequestedMicFromSettings by remember { mutableStateOf(false) }
    var showSettingsMicRationale    by remember { mutableStateOf(false) }

    // ── File picker launchers ─────────────────────────────────────────────────
    // These CANNOT be called from the ViewModel — ActivityResultLauncher requires
    // a Composable context. The ViewModel emits ReadyToExport and the
    // LaunchedEffect below calls the correct launcher.

    val secureBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        // uri is null if user cancelled the picker — reset state cleanly
        if (uri != null) {
            viewModel.startExport(context.contentResolver, uri)
        } else {
            viewModel.onExportDismissed()
        }
    }

    val plainTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.startExport(context.contentResolver, uri)
        } else {
            viewModel.onExportDismissed()
        }
    }

    // ── Observe ReadyToExport → trigger the correct file picker ───────────────
    // LaunchedEffect re-runs whenever exportState changes.
    // We only act on ReadyToExport — all other states are handled by dialogs below.
    LaunchedEffect(exportState) {
        if (exportState is ExportState.ReadyToExport) {
            val state = exportState as ExportState.ReadyToExport
            when (state.format) {
                ExportFormat.SECURE_BACKUP  ->
                    secureBackupLauncher.launch(viewModel.secureBackupFilename())
                ExportFormat.PLAIN_TEXT_ZIP ->
                    plainTextLauncher.launch(viewModel.plainTextFilename())
            }
        }
    }

    // ── Show snackbar for success and error outcomes ───────────────────────────
    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.ExportSuccess -> {
                val formatLabel = when (state.format) {
                    ExportFormat.SECURE_BACKUP  -> "Secure backup"
                    ExportFormat.PLAIN_TEXT_ZIP -> "Plain text export"
                }
                snackbarHostState.showSnackbar(
                    message  = "$formatLabel saved — ${state.noteCount} notes",
                    duration = SnackbarDuration.Short
                )
                viewModel.onExportResultAcknowledged()
            }
            is ExportState.ExportError -> {
                snackbarHostState.showSnackbar(
                    message  = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.onExportResultAcknowledged()
            }
            else -> Unit
        }
    }

    // ── Main scaffold ─────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = Spacing.medium)
        ) {

            // ── APPEARANCE ───────────────────────────────────────────────────
            item { SectionHeader(text = "APPEARANCE") }
            item {
                SettingsItem(
                    icon     = Icons.Default.Palette,
                    title    = "Theme",
                    subtitle = currentTheme.displayName,
                    onClick  = { showThemeDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── PERMISSIONS ──────────────────────────────────────────────────
            item { SectionHeader(text = "PERMISSIONS") }

            item {
                val permissionStatus = cameraPermissionState.status
                val (subtitle, actionLabel) = when {
                    permissionStatus.isGranted ->
                        "Granted — camera captures stay private, never in gallery" to null
                    permissionStatus.shouldShowRationale ->
                        "Denied — tap to allow (photos are encrypted, never in gallery)" to "Allow"
                    hasRequestedFromSettings ->
                        "Permanently denied — tap to open App Settings" to "Open Settings"
                    else ->
                        "Not yet granted — tap to allow camera for note photos" to "Allow"
                }
                PermissionSettingsItem(
                    icon        = Icons.Default.CameraAlt,
                    title       = "Camera",
                    subtitle    = subtitle,
                    isGranted   = permissionStatus.isGranted,
                    actionLabel = actionLabel,
                    onAction    = {
                        when {
                            permissionStatus.isGranted -> { }
                            permissionStatus.shouldShowRationale ->
                                showSettingsCameraRationale = true
                            hasRequestedFromSettings ->
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            else -> {
                                hasRequestedFromSettings = true
                                cameraPermissionState.launchPermissionRequest()
                            }
                        }
                    }
                )
            }

            item {
                val micStatus = micPermissionState.status
                val (micSubtitle, micActionLabel) = when {
                    micStatus.isGranted ->
                        "Granted — voice notes are encrypted immediately after recording" to null
                    micStatus.shouldShowRationale ->
                        "Denied — tap to allow (audio encrypted before saving)" to "Allow"
                    hasRequestedMicFromSettings ->
                        "Permanently denied — tap to open App Settings" to "Open Settings"
                    else ->
                        "Not yet granted — tap to allow microphone for voice notes" to "Allow"
                }
                PermissionSettingsItem(
                    icon        = Icons.Default.Mic,
                    title       = "Microphone",
                    subtitle    = micSubtitle,
                    isGranted   = micStatus.isGranted,
                    actionLabel = micActionLabel,
                    onAction    = {
                        when {
                            micStatus.isGranted -> { }
                            micStatus.shouldShowRationale -> showSettingsMicRationale = true
                            hasRequestedMicFromSettings ->
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            else -> {
                                hasRequestedMicFromSettings = true
                                micPermissionState.launchPermissionRequest()
                            }
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── STORAGE ──────────────────────────────────────────────────────
            item { SectionHeader(text = "STORAGE") }
            item {
                StorageInfoCard(
                    noteCount   = uiState.noteCount,
                    folderCount = uiState.folderCount
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── SECURITY ─────────────────────────────────────────────────────
            item { SectionHeader(text = "SECURITY") }
            item {
                val biometricAvailable = viewModel.isBiometricAvailable
                SettingsToggleItem(
                    icon     = Icons.Default.Lock,
                    title    = "Biometric Lock",
                    subtitle = if (biometricAvailable)
                        "Require fingerprint or PIN to open app"
                    else
                        "Set up a screen lock in Android Settings first",
                    checked  = isBiometricEnabled && biometricAvailable,
                    enabled  = biometricAvailable,
                    onCheckedChange = { if (biometricAvailable) viewModel.setBiometricLock(it) }
                )
            }

            item {
                SettingsItem(
                    icon     = Icons.Default.LockReset,
                    title    = "Change Vault Password",
                    subtitle = "Re-encrypts all notes with a new password",
                    onClick  = onNavigateToChangePassword
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── DATA MANAGEMENT ──────────────────────────────────────────────
            item { SectionHeader(text = "DATA MANAGEMENT") }

            item {
                SettingsItem(
                    icon     = Icons.Default.Delete,
                    title    = "Trash",
                    subtitle = "View and manage deleted notes",
                    onClick  = onNavigateToTrash
                )
            }

            item {
                SettingsItem(
                    icon     = Icons.Default.Archive,
                    title    = "Archive",
                    subtitle = "Notes kept out of the main list",
                    onClick  = onNavigateToArchive
                )
            }

            // ── Export ────────────────────────────────────────────────────────
            item {
                SettingsItem(
                    icon     = Icons.Default.Upload,
                    title    = "Export Notes",
                    subtitle = "Secure backup or plain text",
                    onClick  = { viewModel.onExportTapped() }
                )
            }

            // ── Import Backup ─────────────────────────────────────────────────
            item {
                SettingsItem(
                    icon     = Icons.Default.Download,
                    title    = "Import Backup",
                    subtitle = "Merge notes from a .vnbackup file",
                    onClick  = onNavigateToImport
                )
            }

            item {
                SettingsItem(
                    icon         = Icons.Default.DeleteForever,
                    title        = "Clear All Data",
                    subtitle     = "Delete all notes and folders",
                    onClick      = { showClearDataDialog = true },
                    isDestructive = true
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── ABOUT ────────────────────────────────────────────────────────
            item { SectionHeader(text = "ABOUT") }

            item {
                SettingsItem(
                    icon     = Icons.Default.StarOutline,
                    title    = "What's New",
                    subtitle = "v${uiState.appVersion} release notes",
                    onClick  = onNavigateToChangelog
                )
            }

            item {
                SettingsItem(
                    icon     = Icons.Default.Info,
                    title    = "Version",
                    subtitle = uiState.appVersion,
                    onClick  = null
                )
            }

            item {
                SettingsItem(
                    icon     = Icons.Default.Code,
                    title    = "GitHub",
                    subtitle = "View source code",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW,
                                "https://github.com/NRoy9/VoidNote".toUri())
                        )
                    }
                )
            }

            item {
                SettingsItem(
                    icon     = Icons.Default.Person,
                    title    = "Developer",
                    subtitle = "GreenIcePhoenix",
                    onClick  = null
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.extraLarge)) }

            item {
                Text(
                    text  = "Void Note • Notes that disappear into the void",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.large)
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    // Each dialog is rendered based on the current ExportState. Only one is
    // ever visible at a time because only one ExportState is active at a time.

    when (val state = exportState) {
        is ExportState.ChoosingFormat -> {
            ExportFormatDialog(
                onSecureBackupSelected = { viewModel.onFormatSelected(ExportFormat.SECURE_BACKUP) },
                onPlainTextSelected    = { viewModel.onFormatSelected(ExportFormat.PLAIN_TEXT_ZIP) },
                onDismiss              = { viewModel.onExportDismissed() }
            )
        }

        is ExportState.ConfirmingPassword,
        is ExportState.PasswordVerifying,
        is ExportState.PasswordError -> {
            val isVerifying = state is ExportState.PasswordVerifying
            val errorMsg    = (state as? ExportState.PasswordError)?.message
            ExportPasswordDialog(
                isVerifying  = isVerifying,
                errorMessage = errorMsg,
                onConfirm    = { password -> viewModel.onExportPasswordConfirmed(password) },
                onDismiss    = { viewModel.onExportDismissed() }
            )
        }

        is ExportState.Exporting -> {
            // Non-dismissible progress dialog while ZIP is being written
            ExportProgressDialog()
        }

        // ReadyToExport, Idle, ExportSuccess, ExportError — no dialog
        // (ReadyToExport triggers the file picker via LaunchedEffect above)
        else -> Unit
    }

    // ── Theme dialog ──────────────────────────────────────────────────────────
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme    = currentTheme,
            onThemeSelected = { theme -> viewModel.setTheme(theme); showThemeDialog = false },
            onDismiss       = { showThemeDialog = false }
        )
    }

    // ── Clear data dialog ─────────────────────────────────────────────────────
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon  = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear All Data?") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will permanently delete:", fontWeight = FontWeight.Bold)
                    Text("• All notes (including trash)")
                    Text("• All folders")
                    Text("• All tags")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("This action cannot be undone!",
                        color        = MaterialTheme.colorScheme.error,
                        fontWeight   = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllNotes(); showClearDataDialog = false },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Camera rationale dialog ───────────────────────────────────────────────
    if (showSettingsCameraRationale) {
        AlertDialog(
            onDismissRequest = { showSettingsCameraRationale = false },
            icon  = { Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Camera Access") },
            text  = {
                Text("Void Note uses the camera to capture photos directly into your notes.\n\n" +
                        "Photos are encrypted immediately and never saved to your gallery.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsCameraRationale = false
                    hasRequestedFromSettings = true
                    cameraPermissionState.launchPermissionRequest()
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsCameraRationale = false }) { Text("Not now") }
            }
        )
    }

    if (showSettingsMicRationale) {
        AlertDialog(
            onDismissRequest = { showSettingsMicRationale = false },
            icon  = { Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Microphone Access") },
            text  = {
                Text("Void Note uses the microphone to record voice notes.\n\n" +
                        "Recordings are encrypted immediately and never stored as plain audio.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsMicRationale = false
                    hasRequestedMicFromSettings = true
                    micPermissionState.launchPermissionRequest()
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsMicRationale = false }) { Text("Not now") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPORT DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Step 1: User picks between Secure Backup and Plain Text ZIP.
 *
 * Secure Backup — next step is password confirmation.
 * Plain Text    — next step is the file picker directly.
 */
@Composable
private fun ExportFormatDialog(
    onSecureBackupSelected: () -> Unit,
    onPlainTextSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Upload, null) },
        title = { Text("Export Notes") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {

                // ── Option 1: Secure Backup ───────────────────────────────────
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    onClick   = onSecureBackupSelected,
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.medium)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text       = "Secure Backup  (.vnbackup)",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text  = "Encrypted. Can be restored on any device with your vault password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text  = "Requires vault password to export.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ── Option 2: Plain Text ZIP ──────────────────────────────────
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    onClick   = onPlainTextSelected,
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.medium)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text       = "Plain Text  (.zip)",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text  = "Readable Markdown files, organized in folders. For archiving — cannot be imported back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text  = "No password required. Notes are unencrypted.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Step 2 (Secure Backup only): User re-types their vault password.
 *
 * This dialog is reused for three states:
 *   ConfirmingPassword — waiting for input, confirm button enabled
 *   PasswordVerifying  — PBKDF2 running, spinner shown, fields disabled
 *   PasswordError      — error message shown, fields re-enabled for retry
 *
 * The caller (SettingsScreen) passes [isVerifying] and [errorMessage].
 *
 * WHY ASK FOR THE PASSWORD BEFORE THE FILE PICKER?
 * If we opened the file picker first and the user picked a location, then
 * found out the password was wrong, they'd need to go through the picker again.
 * Verifying password first also means we never open an output stream we won't
 * be able to fill correctly.
 */
@Composable
private fun ExportPasswordDialog(
    isVerifying: Boolean,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        icon  = {
            if (isVerifying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        title = { Text("Confirm Your Vault Password") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(
                    text  = "Enter your vault password to create the backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value         = password,
                    onValueChange = { password = it },
                    label         = { Text("Vault Password") },
                    singleLine    = true,
                    enabled       = !isVerifying,
                    isError       = errorMessage != null,
                    supportingText = errorMessage?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon   = {
                        IconButton(
                            onClick  = { showPassword = !showPassword },
                            enabled  = !isVerifying
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Reminder to keep the password safe
                AnimatedVisibility(visible = !isVerifying) {
                    Text(
                        text  = "⚠ This password is needed to restore the backup on any device. " +
                                "There is no recovery option.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(password) },
                enabled  = !isVerifying && password.isNotEmpty()
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Export")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isVerifying
            ) { Text("Cancel") }
        }
    )
}

/**
 * Non-dismissible progress dialog shown while the ZIP is being written.
 *
 * Writing a large backup with media files can take several seconds.
 * The dialog prevents navigation away and communicates that work is in progress.
 */
@Composable
private fun ExportProgressDialog() {
    Dialog(
        onDismissRequest = { /* non-dismissible */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(Spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                CircularProgressIndicator()
                Text(
                    text  = "Creating backup…",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text  = "Please keep the app open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE COMPOSABLES (unchanged from original)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text      = text,
        style     = MaterialTheme.typography.labelMedium,
        color     = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier  = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small)
    )
}

@Composable
private fun PermissionSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAction),
        color    = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint     = if (isGranted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (isGranted) {
                Icon(
                    imageVector        = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(20.dp)
                )
            } else if (actionLabel != null) {
                TextButton(
                    onClick        = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    isDestructive: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint     = if (isDestructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector        = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
        color    = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Icon(icon, null,
                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun StorageInfoCard(noteCount: Int, folderCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.large),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.large),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = noteCount.toString(),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Notes", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = folderCount.toString(),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Folders", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text  = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Surface(
                        onClick   = { onThemeSelected(theme) },
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = MaterialTheme.shapes.small,
                        color     = if (theme == currentTheme)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text  = theme.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (theme == currentTheme)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (theme == currentTheme) {
                                Icon(
                                    imageVector        = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint               = MaterialTheme.colorScheme.primary,
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}