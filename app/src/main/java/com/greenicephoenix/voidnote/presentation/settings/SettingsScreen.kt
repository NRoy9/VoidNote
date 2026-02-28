package com.greenicephoenix.voidnote.presentation.settings

import android.content.Intent
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

/**
 * Settings Screen - App configuration and preferences
 *
 * Sections:
 * - Appearance (theme)
 * - Storage info
 * - Data management
 * - About
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    //ADD: Snackbar for export feedback
    val snackbarHostState = remember { SnackbarHostState() }
    var showExportSuccess by remember { mutableStateOf(false) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    val isBiometricEnabled by viewModel.biometricLockEnabled.collectAsState()

    // ✅ ADD: Show snackbar when export succeeds
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
            // APPEARANCE SECTION
            item {
                SectionHeader(text = "APPEARANCE")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = currentTheme.displayName,
                    onClick = { showThemeDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // STORAGE SECTION
            item {
                SectionHeader(text = "STORAGE")
            }

            item {
                StorageInfoCard(
                    noteCount = uiState.noteCount,
                    folderCount = uiState.folderCount
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // SECURITY SECTION
            item {
                SectionHeader(text = "SECURITY")
            }

            item {
                // Biometric lock — always visible so users know the feature exists.
                // When device has no screen lock set up, the toggle is shown but
                // disabled at 50% alpha with an explanatory subtitle.
                // isBiometricAvailable is read directly here as a plain Boolean —
                // hardware availability never changes at runtime.
                val biometricAvailable = viewModel.isBiometricAvailable
                SettingsToggleItem(
                    icon = Icons.Default.Lock,           // Lock icon — in default set, always available
                    title = "Biometric Lock",
                    subtitle = if (biometricAvailable)
                        "Require fingerprint or PIN to open app"
                    else
                        "Set up a screen lock in Android Settings first",
                    checked = isBiometricEnabled && biometricAvailable,
                    enabled = biometricAvailable,        // greys out switch when unavailable
                    onCheckedChange = { if (biometricAvailable) viewModel.setBiometricLock(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(Spacing.large)) }

            // DATA MANAGEMENT SECTION
            item {
                SectionHeader(text = "DATA MANAGEMENT")
            }

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

                // File pickers for different formats
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

                // Format selection dialog
                if (showFormatDialog) {
                    AlertDialog(
                        onDismissRequest = { showFormatDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null
                            )
                        },
                        title = { Text("Choose Export Format") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                Text("Select format:")
                                Spacer(modifier = Modifier.height(Spacing.small))

                                // JSON option
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        showFormatDialog = false
                                        val timestamp = java.text.SimpleDateFormat(
                                            "yyyyMMdd_HHmmss",
                                            java.util.Locale.getDefault()
                                        ).format(java.util.Date())
                                        jsonExportLauncher.launch("voidnote_backup_$timestamp.json")
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.medium)) {
                                        Text(
                                            text = "JSON (Recommended)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Preserves formatting, can be imported back",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                // TXT option
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        showFormatDialog = false
                                        val timestamp = java.text.SimpleDateFormat(
                                            "yyyyMMdd_HHmmss",
                                            java.util.Locale.getDefault()
                                        ).format(java.util.Date())
                                        txtExportLauncher.launch("voidnote_backup_$timestamp.txt")
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.medium)) {
                                        Text(
                                            text = "Plain Text",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Human-readable, easy to view anywhere",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showFormatDialog = false }) {
                                Text("Cancel")
                            }
                        }
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

            // ABOUT SECTION
            item {
                SectionHeader(text = "ABOUT")
            }

            item {
                // "What's New" — taps through to full changelog screen
                // Shows latest version as subtitle so users know what to expect
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

            // FOOTER
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

    // Theme Selection Dialog
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

    // Clear Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear All Data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will permanently delete:",
                        fontWeight = FontWeight.Bold
                    )
                    Text("• All notes (including trash)")
                    Text("• All folders")
                    Text("• All tags")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "This action cannot be undone!",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllNotes()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Section Header
 */
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
 * Settings Item - Individual setting row
 */
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
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
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
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
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

/**
 * Settings item with a toggle switch instead of a chevron.
 * Used for binary on/off preferences like Biometric Lock.
 */
@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,        // when false: 50% alpha, switch disabled
    onCheckedChange: (Boolean) -> Unit
) {
    // Alpha communicates "this exists but can't be used yet"
    // better than hiding the item completely
    val contentAlpha = if (enabled) 1f else 0.45f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(contentAlpha)
            .then(
                if (enabled) Modifier.clickable { onCheckedChange(!checked) }
                else Modifier          // no click when disabled
            ),
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
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * Storage Info Card
 */
@Composable
private fun StorageInfoCard(
    noteCount: Int,
    folderCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.large),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StorageInfoItem(
                icon = Icons.Default.Description,
                count = noteCount,
                label = "Notes"
            )

            VerticalDivider(
                modifier = Modifier.height(48.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            StorageInfoItem(
                icon = Icons.Default.Folder,
                count = folderCount,
                label = "Folders"
            )
        }
    }
}

/**
 * Storage Info Item
 */
@Composable
private fun StorageInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * Theme Selection Dialog
 */
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = Spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(Spacing.medium))
                        Text(
                            text = theme.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}