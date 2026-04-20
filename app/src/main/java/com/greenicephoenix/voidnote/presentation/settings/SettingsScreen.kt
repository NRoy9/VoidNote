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
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.animation.AnimatedVisibility

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
    onNavigateToSupport: () -> Unit = {},           // → SupportScreen
    onNavigateToMigrator: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState            by viewModel.uiState.collectAsState()
    val currentTheme       by viewModel.currentTheme.collectAsState()
    val isBiometricEnabled by viewModel.biometricLockEnabled.collectAsState()
    val context = LocalContext.current

    var showThemeDialog          by remember { mutableStateOf(false) }
    var showClearDataDialog      by remember { mutableStateOf(false) }
    var showUpdateAvailableDialog by remember { mutableStateOf(false) }

    // Show the update-available dialog whenever state transitions to Available
    val updateState = uiState.updateCheckState
    LaunchedEffect(updateState) {
        if (updateState is UpdateCheckState.Available || updateState == UpdateCheckState.UpToDate || updateState == UpdateCheckState.Error) {
            showUpdateAvailableDialog = true
        }
    }

    // ── Permission states ─────────────────────────────────────────────────────
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var hasRequestedFromSettings    by remember { mutableStateOf(false) }
    var showSettingsCameraRationale by remember { mutableStateOf(false) }

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var hasRequestedMicFromSettings by remember { mutableStateOf(false) }
    var showSettingsMicRationale    by remember { mutableStateOf(false) }

    // ── Main scaffold ─────────────────────────────────────────────────────────
    Scaffold(
        contentWindowInsets = WindowInsets(0),
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
        val appVersion = uiState.appVersion

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = Spacing.medium)
        ) {

            // ─────────────────────────────────────────────────────────────────
            // GROUP 01 · DISPLAY
            // Single item. Keeping it alone makes the section feel intentional
            // rather than buried inside a bigger group.
            // ─────────────────────────────────────────────────────────────────
            item { SectionHeader(text = "DISPLAY") }
            item {
                SettingsItem(
                    icon     = Icons.Default.Palette,
                    title    = "Theme",
                    // Uppercase value is the 20% Command Palette influence —
                    // reads like key → VALUE, subtle but distinctive.
                    subtitle = currentTheme.displayName.uppercase(),
                    onClick  = { showThemeDialog = true }
                )
            }

            // ─────────────────────────────────────────────────────────────────
            // GROUP 02 · ACCESS
            // Everything about getting into the app unified here.
            // Previously: Biometric/Password in SECURITY, Camera/Mic in PERMISSIONS.
            // Unified rationale: they all answer "who can open my notes".
            // ─────────────────────────────────────────────────────────────────
            item { SectionHeader(text = "ACCESS") }

            item {
                val biometricAvailable = viewModel.isBiometricAvailable
                SettingsToggleItem(
                    icon     = Icons.Default.Lock,
                    title    = "Biometric Lock",
                    subtitle = if (biometricAvailable)
                        "Fingerprint or PIN to open app"
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

            // Camera permission
            item {
                val permissionStatus = cameraPermissionState.status
                val (subtitle, actionLabel) = when {
                    permissionStatus.isGranted ->
                        "Granted — captures stay private, never in gallery" to null
                    permissionStatus.shouldShowRationale ->
                        "Denied — tap to allow (photos encrypted, never in gallery)" to "Allow"
                    hasRequestedFromSettings ->
                        "Permanently denied — tap to open App Settings" to "Open Settings"
                    else ->
                        "Not yet granted — needed for note photos" to "Allow"
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

            // Microphone permission
            item {
                val micStatus = micPermissionState.status
                val (micSubtitle, micActionLabel) = when {
                    micStatus.isGranted ->
                        "Granted — recordings encrypted immediately after capture" to null
                    micStatus.shouldShowRationale ->
                        "Denied — tap to allow (audio encrypted before saving)" to "Allow"
                    hasRequestedMicFromSettings ->
                        "Permanently denied — tap to open App Settings" to "Open Settings"
                    else ->
                        "Not yet granted — needed for voice notes" to "Allow"
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

            // ─────────────────────────────────────────────────────────────────
            // GROUP 03 · VAULT
            // Storage stats sit at the top to give context before the user
            // reaches Export/Import/Clear. All 4 stats shown so the card
            // reflects real vault state.
            // ─────────────────────────────────────────────────────────────────
            item { SectionHeader(text = "VAULT") }

            item {
                StorageInfoCard(
                    noteCount    = uiState.noteCount,
                    folderCount  = uiState.folderCount,
                    archiveCount = uiState.archiveCount,
                    trashCount   = uiState.trashCount,
                    diaryCount   = uiState.diaryCount
                )
            }

            // Export — navigates to ExportNotesScreen, no dialogs here
            item {
                SettingsItem(
                    icon     = Icons.Default.Upload,
                    title    = "Export Notes",
                    subtitle = "Secure backup or plain text",
                    onClick  = onNavigateToExport
                )
            }

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
                    icon     = Icons.Default.MoveToInbox,
                    title    = "Import from Another App",
                    subtitle = "Evernote · Google Keep · Notion",
                    onClick  = onNavigateToMigrator
                )
            }

            item {
                SettingsItem(
                    icon          = Icons.Default.DeleteForever,
                    title         = "Clear All Data",
                    subtitle      = "Permanently deletes all notes, folders and tags",
                    onClick       = { showClearDataDialog = true },
                    isDestructive = true
                )
            }

            // ─────────────────────────────────────────────────────────────────
            // GROUP 04 · NOTES
            // Archive and Trash are note states, not data operations.
            // Separate from VAULT so users know these navigate to lists.
            // Live counts surface here before they tap in.
            // ─────────────────────────────────────────────────────────────────
            item { SectionHeader(text = "NOTES") }

            item {
                val archiveSubtitle = when (uiState.archiveCount) {
                    0    -> "No archived notes"
                    1    -> "1 archived note"
                    else -> "${uiState.archiveCount} archived notes"
                }
                SettingsItem(
                    icon     = Icons.Default.Archive,
                    title    = "Archive",
                    subtitle = archiveSubtitle,
                    onClick  = onNavigateToArchive
                )
            }

            item {
                val trashSubtitle = when (uiState.trashCount) {
                    0    -> "No deleted notes"
                    1    -> "1 deleted note"
                    else -> "${uiState.trashCount} deleted notes"
                }
                SettingsItem(
                    icon     = Icons.Default.Delete,
                    title    = "Trash",
                    subtitle = trashSubtitle,
                    onClick  = onNavigateToTrash
                )
            }

            // ─────────────────────────────────────────────────────────────────
            // GROUP 05 · SYSTEM
            // App-internal meta: changelog, updates, website, privacy policy.
            // ─────────────────────────────────────────────────────────────────
            item { SectionHeader(text = "SYSTEM") }

            item {
                SettingsItem(
                    icon     = Icons.Default.StarOutline,
                    title    = "What's New",
                    subtitle = "v$appVersion release notes",
                    onClick  = onNavigateToChangelog
                )
            }

            // Subtitle reflects live update check state.
            // LinearProgressIndicator slides in while the request is in-flight.
            item {
                val updateState    = uiState.updateCheckState
                val isChecking     = updateState == UpdateCheckState.Checking
                val updateSubtitle = when (updateState) {
                    is UpdateCheckState.Idle      -> "Installed: v$appVersion"
                    is UpdateCheckState.Checking  -> "Checking for updates..."
                    is UpdateCheckState.UpToDate  -> "Up to date — v$appVersion"
                    is UpdateCheckState.Available -> "v${updateState.version} available — tap to download"
                    is UpdateCheckState.Error     -> "Check failed — tap to retry"
                }
                Column {
                    SettingsItem(
                        icon     = Icons.Default.SystemUpdate,
                        title    = "Check for Updates",
                        subtitle = updateSubtitle,
                        onClick  = { viewModel.checkForUpdates() }
                    )
                    AnimatedVisibility(visible = isChecking) {
                        LinearProgressIndicator(
                            modifier   = Modifier.fillMaxWidth().padding(horizontal = Spacing.large).height(2.dp),
                            color      = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    }
                }
            }

            item {
                SettingsItem(
                    icon     = Icons.Default.Language,
                    title    = "Website",
                    subtitle = "voidnote.pages.dev",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://voidnote.pages.dev".toUri())
                        )
                    }
                )
            }

            // Privacy Policy — required by Play Store data safety section
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

            // ─────────────────────────────────────────────────────────────────
            // GROUP 06 · COMMUNITY
            // Outward-facing links. Separate from SYSTEM (app-internal) and
            // VAULT (data operations).
            // ─────────────────────────────────────────────────────────────────
            item { SectionHeader(text = "COMMUNITY") }

            item {
                SettingsItem(
                    icon     = Icons.Default.Forum,
                    title    = "Discord",
                    subtitle = "Bug reports, feedback & chat",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://discord.gg/WguVY8wmzT".toUri())
                        )
                    }
                )
            }

            item {
                SettingsItem(
                    icon     = Icons.Default.Favorite,
                    title    = "Support the Developer",
                    subtitle = "PayPal · UPI",
                    onClick  = onNavigateToSupport
                )
            }

            // ── Footer ───────────────────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(Spacing.extraLarge)) }

            item {
                Text(
                    text  = "Void Note • Notes that disappear into the void",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
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

    // ── Update result dialog ─────────────────────────────────────────────────
    // Shown when updateState transitions to UpToDate, Available, or Error.
    // Uses showUpdateAvailableDialog flag so we can dismiss without resetting state.
    if (showUpdateAvailableDialog) {
        when (val state = uiState.updateCheckState) {
            is UpdateCheckState.Available -> AlertDialog(
                onDismissRequest = { showUpdateAvailableDialog = false; viewModel.dismissUpdateResult() },
                icon  = { Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Update Available") },
                text  = {
                    Text("Version ${state.version} is available on GitHub.\nTap Download to open the release page.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.openDownloadUrl(state.downloadUrl)
                        showUpdateAvailableDialog = false
                    }) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateAvailableDialog = false; viewModel.dismissUpdateResult() }) { Text("Later") }
                }
            )
            is UpdateCheckState.UpToDate -> AlertDialog(
                onDismissRequest = { showUpdateAvailableDialog = false; viewModel.dismissUpdateResult() },
                icon  = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("You're Up to Date") },
                text  = { Text("v${uiState.appVersion} is the latest version.") },
                confirmButton = {
                    TextButton(onClick = { showUpdateAvailableDialog = false; viewModel.dismissUpdateResult() }) { Text("OK") }
                }
            )
            is UpdateCheckState.Error -> AlertDialog(
                onDismissRequest = { showUpdateAvailableDialog = false; viewModel.dismissUpdateResult() },
                icon  = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Check Failed") },
                text  = { Text("Couldn't reach GitHub. Check your internet connection and try again.") },
                confirmButton = {
                    TextButton(onClick = { showUpdateAvailableDialog = false; viewModel.dismissUpdateResult() }) { Text("OK") }
                }
            )
            else -> { showUpdateAvailableDialog = false }
        }
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
    // 20% Command Palette influence: label on the left, thin hairline stretches to the right.
    // Gives each section a separator feel without being heavy.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.large, end = Spacing.large, top = Spacing.large, bottom = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Text(
            text  = text,
            // labelSmall + wider tracking mimics the key names in a config file
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
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
private fun StorageInfoCard(
    noteCount: Int,
    folderCount: Int,
    archiveCount: Int,
    trashCount: Int,
    diaryCount: Int     // NEW
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            @Composable
            fun StatColumn(value: Int, label: String) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = value.toString(),
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text  = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            @Composable
            fun StatDivider() {
                VerticalDivider(
                    modifier  = Modifier.height(32.dp),
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }

            StatColumn(noteCount,    "Notes")
            StatDivider()
            StatColumn(diaryCount,   "Journal")   // NEW column
            StatDivider()
            StatColumn(folderCount,  "Folders")
            StatDivider()
            StatColumn(archiveCount, "Archived")
            StatDivider()
            StatColumn(trashCount,   "Trashed")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle       = {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
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
                .padding(horizontal = Spacing.large)
                .padding(bottom = Spacing.extraLarge)
        ) {
            Text(
                text     = "CHOOSE THEME",
                style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = Spacing.medium)
            )
            AppTheme.entries.forEach { theme ->
                Surface(
                    onClick  = { onThemeSelected(theme) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.medium,
                    color    = if (theme == currentTheme)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium),
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
                Spacer(modifier = Modifier.height(Spacing.small))
            }
        }
    }
}