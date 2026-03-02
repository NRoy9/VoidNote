package com.greenicephoenix.voidnote.presentation.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import androidx.compose.material.icons.filled.ChevronRight
import androidx.core.net.toUri
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Settings Screen
 *
 * Sections:
 * - APPEARANCE (theme)
 * - PERMISSIONS (camera — user can grant/manage from here)
 * - SECURITY (biometric)
 * - STORAGE
 * - DATA MANAGEMENT
 * - ABOUT
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToArchive: () -> Unit = {},
    onNavigateToChangelog: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showExportSuccess by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    val isBiometricEnabled by viewModel.biometricLockEnabled.collectAsState()

    // ── Camera permission state for the Settings permissions row ────────────
    // This mirrors the same logic used in NoteEditorScreen, but here it's used
    // purely for display and one-tap grant from Settings.
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var hasRequestedFromSettings by remember { mutableStateOf(false) }
    var showSettingsCameraRationale by remember { mutableStateOf(false) }

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var hasRequestedMicFromSettings by remember { mutableStateOf(false) }
    var showSettingsMicRationale    by remember { mutableStateOf(false) }

    LaunchedEffect(showExportSuccess) {
        if (showExportSuccess) {
            snackbarHostState.showSnackbar(
                message = "Notes exported successfully",
                duration = SnackbarDuration.Short
            )
            showExportSuccess = false
        }
    }

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
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = currentTheme.displayName,
                    onClick = { showThemeDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── PERMISSIONS ──────────────────────────────────────────────────
            // Shows the status of each runtime permission.
            // Tapping a row requests the permission (or opens App Settings if
            // permanently denied). Users can manage permissions without leaving the app.
            item { SectionHeader(text = "PERMISSIONS") }

            item {
                // Determine the subtitle and action based on current permission state
                val permissionStatus = cameraPermissionState.status
                val (subtitle, actionLabel) = when {
                    permissionStatus.isGranted -> Pair(
                        "Granted — camera captures stay private, never in gallery",
                        null  // no action needed when granted
                    )
                    permissionStatus.shouldShowRationale -> Pair(
                        "Denied — tap to allow (photos are encrypted, never in gallery)",
                        "Allow"
                    )
                    hasRequestedFromSettings -> Pair(
                        "Permanently denied — tap to open App Settings",
                        "Open Settings"
                    )
                    else -> Pair(
                        "Not yet granted — tap to allow camera for note photos",
                        "Allow"
                    )
                }

                PermissionSettingsItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Camera",
                    subtitle = subtitle,
                    isGranted = permissionStatus.isGranted,
                    actionLabel = actionLabel,
                    onAction = {
                        when {
                            permissionStatus.isGranted -> { /* nothing to do */ }
                            permissionStatus.shouldShowRationale -> {
                                showSettingsCameraRationale = true
                            }
                            hasRequestedFromSettings -> {
                                // Permanently denied — open App Settings
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
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
                    micStatus.isGranted -> Pair(
                        "Granted — voice notes are encrypted immediately after recording",
                        null
                    )
                    micStatus.shouldShowRationale -> Pair(
                        "Denied — tap to allow (audio encrypted before saving)",
                        "Allow"
                    )
                    hasRequestedMicFromSettings -> Pair(
                        "Permanently denied — tap to open App Settings",
                        "Open Settings"
                    )
                    else -> Pair(
                        "Not yet granted — tap to allow microphone for voice notes",
                        "Allow"
                    )
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
                            hasRequestedMicFromSettings -> {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            }
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
                    noteCount = uiState.noteCount,
                    folderCount = uiState.folderCount
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── SECURITY ─────────────────────────────────────────────────────
            item { SectionHeader(text = "SECURITY") }

            item {
                val biometricAvailable = viewModel.isBiometricAvailable
                SettingsToggleItem(
                    icon = Icons.Default.Lock,
                    title = "Biometric Lock",
                    subtitle = if (biometricAvailable)
                        "Require fingerprint or PIN to open app"
                    else
                        "Set up a screen lock in Android Settings first",
                    checked = isBiometricEnabled && biometricAvailable,
                    enabled = biometricAvailable,
                    onCheckedChange = { if (biometricAvailable) viewModel.setBiometricLock(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── DATA MANAGEMENT ──────────────────────────────────────────────
            item { SectionHeader(text = "DATA MANAGEMENT") }

            item {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Trash",
                    subtitle = "View and manage deleted notes",
                    onClick = onNavigateToTrash
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Archive,
                    title = "Archive",
                    subtitle = "Notes kept out of the main list",
                    onClick = onNavigateToArchive
                )
            }

            item {
                var showFormatDialog by remember { mutableStateOf(false) }

                val jsonExportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        viewModel.exportNotesToUri(context.contentResolver, it, ExportFormat.JSON)
                        showExportSuccess = true
                    }
                }

                val txtExportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    uri?.let {
                        viewModel.exportNotesToUri(context.contentResolver, it, ExportFormat.TXT)
                        showExportSuccess = true
                    }
                }

                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = "Export Notes",
                    subtitle = "Save all notes to file",
                    onClick = { showFormatDialog = true }
                )

                if (showFormatDialog) {
                    AlertDialog(
                        onDismissRequest = { showFormatDialog = false },
                        icon = { Icon(imageVector = Icons.Default.Upload, contentDescription = null) },
                        title = { Text("Choose Export Format") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                Text("Select format:")
                                Spacer(modifier = Modifier.height(Spacing.small))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        showFormatDialog = false
                                        val timestamp = java.text.SimpleDateFormat(
                                            "yyyyMMdd_HHmmss", java.util.Locale.getDefault()
                                        ).format(java.util.Date())
                                        jsonExportLauncher.launch("voidnote_backup_$timestamp.json")
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.medium)) {
                                        Text("JSON (Recommended)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Preserves formatting, can be imported back",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        showFormatDialog = false
                                        val timestamp = java.text.SimpleDateFormat(
                                            "yyyyMMdd_HHmmss", java.util.Locale.getDefault()
                                        ).format(java.util.Date())
                                        txtExportLauncher.launch("voidnote_backup_$timestamp.txt")
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.medium)) {
                                        Text("Plain Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Human-readable, easy to view anywhere",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { showFormatDialog = false }) { Text("Cancel") } }
                    )
                }
            }

            item {
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "Delete all notes and folders",
                    onClick = { showClearDataDialog = true },
                    isDestructive = true
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // ── ABOUT ────────────────────────────────────────────────────────
            item { SectionHeader(text = "ABOUT") }

            item {
                SettingsItem(
                    icon = Icons.Default.StarOutline,
                    title = "What's New",
                    subtitle = "v${uiState.appVersion} release notes",
                    onClick = onNavigateToChangelog
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = uiState.appVersion,
                    onClick = null
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "GitHub",
                    subtitle = "View source code",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            "https://github.com/NRoy9/VoidNote".toUri())
                        context.startActivity(intent)
                    }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Developer",
                    subtitle = "GreenIcePhoenix",
                    onClick = null
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.extraLarge)) }

            item {
                Text(
                    text = "Void Note • Notes that disappear into the void",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.large)
                )
            }
        }
    }

    // ── Theme dialog ─────────────────────────────────────────────────────────
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { theme ->
                viewModel.setTheme(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // ── Clear data dialog ────────────────────────────────────────────────────
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will permanently delete:", fontWeight = FontWeight.Bold)
                    Text("• All notes (including trash)")
                    Text("• All folders")
                    Text("• All tags")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("This action cannot be undone!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllNotes(); showClearDataDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") } }
        )
    }

    // ── Camera rationale dialog (from Settings) ──────────────────────────────
    if (showSettingsCameraRationale) {
        AlertDialog(
            onDismissRequest = { showSettingsCameraRationale = false },
            icon = { Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Camera Access") },
            text = {
                Text(
                    "Void Note uses the camera to capture photos directly into your notes.\n\n" +
                            "Photos are encrypted immediately and never saved to your gallery."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSettingsCameraRationale = false
                        hasRequestedFromSettings = true
                        cameraPermissionState.launchPermissionRequest()
                    }
                ) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsCameraRationale = false }) { Text("Not now") }
            }
        )
    }

    if (showSettingsMicRationale) {
        AlertDialog(
            onDismissRequest = { showSettingsMicRationale = false },
            icon    = { Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary) },
            title   = { Text("Microphone Access") },
            text    = {
                Text(
                    "Void Note uses the microphone to record voice notes.\n\n" +
                            "Recordings are encrypted immediately and never stored as plain audio."
                )
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
// COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small)
    )
}

/**
 * Permission row — shows permission status with a tappable action.
 *
 * GRANTED:    Green check icon, full opacity, no action label
 * NOT GRANTED: Muted icon at 60% opacity, action label ("Allow" or "Open Settings")
 *
 * The two-tone design (green for granted, muted for denied) gives users an
 * at-a-glance status without needing to read every subtitle.
 */
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Icon — green when granted, muted when denied
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Status badge on the right
            if (isGranted) {
                // Granted: filled green check
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else if (actionLabel != null) {
                // Not granted: small action label button
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium
                    )
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
                tint = if (isDestructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
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
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun StorageInfoCard(noteCount: Int, folderCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.large),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = noteCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = folderCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
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
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Surface(
                        onClick = { onThemeSelected(theme) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = if (theme == currentTheme) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = theme.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (theme == currentTheme) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (theme == currentTheme) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}