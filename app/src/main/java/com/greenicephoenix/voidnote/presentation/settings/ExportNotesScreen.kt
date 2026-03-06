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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * ExportNotesScreen — Settings → Data Management → Export Notes
 *
 * Mirrors the structure of ImportBackupScreen for consistency.
 *
 * ─── SCREEN STATES ─────────────────────────────────────────────────────────
 *
 *   Idle              → format selection (Secure Backup vs Plain Text)
 *   ConfirmingPassword
 *   PasswordVerifying → password entry for secure backup
 *   PasswordError     ↗
 *   ReadyToExport     → LaunchedEffect triggers the file picker
 *   Exporting         → non-dismissible progress dialog
 *   ExportSuccess     → success card with note count + Done button
 *   ExportError       → error card + Try Again button
 *
 * ─── WHY ActivityResultLauncher IS HERE ────────────────────────────────────
 *
 * CreateDocument (the system save dialog) must be launched from a Composable.
 * The ViewModel emits ReadyToExport and a LaunchedEffect here observes it
 * and calls the appropriate launcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportNotesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportNotesViewModel = hiltViewModel()
) {
    val exportState by viewModel.exportState.collectAsState()
    val context = LocalContext.current

    // ── File save launchers ───────────────────────────────────────────────────
    val secureBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.startExport(context.contentResolver, uri)
        else viewModel.reset()
    }

    val plainTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.startExport(context.contentResolver, uri)
        else viewModel.reset()
    }

    // ── Trigger file picker when ViewModel is ready ───────────────────────────
    LaunchedEffect(exportState) {
        if (exportState is ExportState.ReadyToExport) {
            val state = exportState as ExportState.ReadyToExport
            when (state.format) {
                ExportFormat.SECURE_BACKUP  -> secureBackupLauncher.launch(viewModel.secureBackupFilename())
                ExportFormat.PLAIN_TEXT_ZIP -> plainTextLauncher.launch(viewModel.plainTextFilename())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Notes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            when (val state = exportState) {

                // ── IDLE: format selection ────────────────────────────────────
                is ExportState.Idle -> {
                    ExportFormatContent(
                        onSecureBackupClick = {
                            viewModel.onFormatSelected(ExportFormat.SECURE_BACKUP)
                        },
                        onPlainTextClick = {
                            viewModel.onFormatSelected(ExportFormat.PLAIN_TEXT_ZIP)
                        }
                    )
                }

                // ── PASSWORD STATES: entry, verifying, error ──────────────────
                is ExportState.ConfirmingPassword,
                is ExportState.PasswordVerifying,
                is ExportState.PasswordError -> {
                    val isVerifying  = state is ExportState.PasswordVerifying
                    val errorMessage = (state as? ExportState.PasswordError)?.message
                    ExportPasswordContent(
                        isVerifying  = isVerifying,
                        errorMessage = errorMessage,
                        onConfirm    = { password -> viewModel.onPasswordConfirmed(password) },
                        onBack       = { viewModel.onDismissPassword() }
                    )
                }

                // ── SUCCESS ───────────────────────────────────────────────────
                is ExportState.ExportSuccess -> {
                    ExportSuccessContent(
                        noteCount = state.noteCount,
                        format    = state.format,
                        onDone    = onNavigateBack
                    )
                }

                // ── ERROR ─────────────────────────────────────────────────────
                is ExportState.ExportError -> {
                    ExportErrorContent(
                        message    = state.message,
                        onTryAgain = { viewModel.reset() },
                        onBack     = onNavigateBack
                    )
                }

                // ReadyToExport triggers LaunchedEffect above — no content here
                else -> Unit
            }

            // ── Non-dismissible progress dialog ───────────────────────────────
            if (exportState is ExportState.Exporting) {
                ExportProgressOverlay()
            }
        }
    }
}

// ─── Format selection content ──────────────────────────────────────────────────

@Composable
private fun ExportFormatContent(
    onSecureBackupClick: () -> Unit,
    onPlainTextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large)
    ) {
        Spacer(modifier = Modifier.height(Spacing.medium))

        Icon(
            imageVector        = Icons.Default.Upload,
            contentDescription = null,
            modifier           = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        Text(
            text      = "Choose Export Format",
            style     = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )

        Text(
            text      = "Choose how you'd like to export your notes.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        // ── Secure Backup card ────────────────────────────────────────────────
        Card(
            onClick  = onSecureBackupClick,
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.large),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment     = Alignment.Top
            ) {
                Icon(
                    imageVector        = Icons.Default.Lock,
                    contentDescription = null,
                    modifier           = Modifier.size(24.dp).padding(top = 2.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text       = "Secure Backup (.vnbackup)",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text  = "Encrypted backup. All notes stay encrypted.\nCan be imported back into Void Note on any device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text  = "Requires vault password to export",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Plain Text ZIP card ───────────────────────────────────────────────
        Card(
            onClick  = onPlainTextClick,
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.large),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment     = Alignment.Top
            ) {
                Icon(
                    imageVector        = Icons.Default.FolderZip,
                    contentDescription = null,
                    modifier           = Modifier.size(24.dp).padding(top = 2.dp),
                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text       = "Plain Text ZIP (.zip)",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text  = "Human-readable Markdown files organised by folder.\nFor archiving or reading notes outside Void Note.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text  = "Export only — cannot be imported back",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ─── Password entry content ────────────────────────────────────────────────────

@Composable
private fun ExportPasswordContent(
    isVerifying: Boolean,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit
) {
    var password    by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val canConfirm  = password.isNotEmpty() && !isVerifying

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Spacer(modifier = Modifier.height(Spacing.medium))

        Icon(
            imageVector        = Icons.Default.Lock,
            contentDescription = null,
            modifier           = Modifier
                .size(52.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Text(
            text      = "Confirm Vault Password",
            style     = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )

        Text(
            text      = "Enter your vault password to encrypt the backup.\nThis confirms you're authorised to export your notes.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        OutlinedTextField(
            value         = password,
            onValueChange = { password = it },
            label         = { Text("Vault password") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            enabled       = !isVerifying,
            isError       = errorMessage != null,
            supportingText = errorMessage?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }, enabled = !isVerifying) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide" else "Show"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Button(
            onClick  = { onConfirm(password) },
            enabled  = canConfirm,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isVerifying) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("Verifying…")
            } else {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text  = "Export Secure Backup",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        OutlinedButton(
            onClick  = onBack,
            enabled  = !isVerifying,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose Different Format")
        }
    }
}

// ─── Success content ───────────────────────────────────────────────────────────

@Composable
private fun ExportSuccessContent(
    noteCount: Int,
    format: ExportFormat,
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
            text       = "Export Complete",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Notes exported", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text       = noteCount.toString(),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Format", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text  = when (format) {
                            ExportFormat.SECURE_BACKUP  -> ".vnbackup"
                            ExportFormat.PLAIN_TEXT_ZIP -> ".zip"
                        },
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
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

// ─── Error content ─────────────────────────────────────────────────────────────

@Composable
private fun ExportErrorContent(
    message: String,
    onTryAgain: () -> Unit,
    onBack: () -> Unit
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
            text       = "Export Failed",
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

        Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Try Again")
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Go Back")
        }
    }
}

// ─── Progress overlay ──────────────────────────────────────────────────────────

@Composable
private fun ExportProgressOverlay() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress    = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.large)
            ) {
                CircularProgressIndicator()
                Text(
                    text       = "Exporting notes…",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text      = "Writing encrypted backup.\nDo not close the app.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}