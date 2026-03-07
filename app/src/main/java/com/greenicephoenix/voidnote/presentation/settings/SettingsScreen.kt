package com.greenicephoenix.voidnote.presentation.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 * Export and Import both navigate to their own dedicated screens.
 * No export dialogs live here anymore — ExportNotesScreen owns that flow.
 * This keeps Settings as a simple navigation hub for data management.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToArchive: () -> Unit = {},
    onNavigateToChangelog: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},            // → ExportNotesScreen
    onNavigateToImport: () -> Unit = {},            // → ImportBackupScreen
    onNavigateToChangePassword: () -> Unit = {},    // → ChangeVaultPasswordScreen
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState            by viewModel.uiState.collectAsState()
    val currentTheme       by viewModel.currentTheme.collectAsState()
    val isBiometricEnabled by viewModel.biometricLockEnabled.collectAsState()
    val context = LocalContext.current

    var showThemeDialog     by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    // ── Permission states ─────────────────────────────────────────────────────
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var hasRequestedFromSettings    by remember { mutableStateOf(false) }
    var showSettingsCameraRationale by remember { mutableStateOf(false) }

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var hasRequestedMicFromSettings by remember { mutableStateOf(false) }
    var showSettingsMicRationale    by remember { mutableStateOf(false) }

    // ── Main scaffold ─────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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

            // ── Export — navigates to ExportNotesScreen ───────────────────────
            // Previously called viewModel.onExportTapped() which opened dialogs.
            // Now consistently navigates to a full screen like Import does.
            item {
                SettingsItem(
                    icon     = Icons.Default.Upload,
                    title    = "Export Notes",
                    subtitle = "Secure backup or plain text",
                    onClick  = onNavigateToExport
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
                    icon          = Icons.Default.DeleteForever,
                    title         = "Clear All Data",
                    subtitle      = "Delete all notes and folders",
                    onClick       = { showClearDataDialog = true },
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

            // Website — primary destination for APK downloads and release info
            item {
                SettingsItem(
                    icon     = Icons.Default.Language,
                    title    = "Website",
                    subtitle = "voidnote.pages.dev",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW,
                                "https://voidnote.pages.dev".toUri())
                        )
                    }
                )
            }

            // Privacy Policy — required by Play Store; hosted on our website
            item {
                SettingsItem(
                    icon     = Icons.Default.PrivacyTip,
                    title    = "Privacy Policy",
                    subtitle = "How we handle your data",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW,
                                "https://voidnote.pages.dev/privacy-policy.html".toUri())
                        )
                    }
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
                    imageVector        = Icons.Default.Warning,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.error
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
                    Text(
                        text       = "This action cannot be undone!",
                        color      = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllNotes(); showClearDataDialog = false },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
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
                Text(
                    "Void Note uses the camera to capture photos directly into your notes.\n\n" +
                            "Photos are encrypted immediately and never saved to your gallery."
                )
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

    // ── Microphone rationale dialog ───────────────────────────────────────────
    if (showSettingsMicRationale) {
        AlertDialog(
            onDismissRequest = { showSettingsMicRationale = false },
            icon  = { Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Microphone Access") },
            text  = {
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
// REUSABLE COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelMedium,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small)
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
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (isGranted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier           = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
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
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (isDestructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier           = Modifier.size(24.dp)
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
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Icon(
                icon, null,
                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
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
            modifier              = Modifier.fillMaxWidth().padding(Spacing.large),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = noteCount.toString(),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = folderCount.toString(),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Folders",
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
        text  = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Surface(
                        onClick  = { onThemeSelected(theme) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.small,
                        color    = if (theme == currentTheme)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(Spacing.medium),
                            verticalAlignment     = Alignment.CenterVertically,
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
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}