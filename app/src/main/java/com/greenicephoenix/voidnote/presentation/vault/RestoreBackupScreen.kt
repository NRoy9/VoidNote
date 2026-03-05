package com.greenicephoenix.voidnote.presentation.vault

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * RestoreBackupScreen — Flow A: restore from .vnbackup on a fresh install.
 *
 * Reached from VaultSetupScreen → "Restore from existing backup".
 * After success, NavGraph navigates to NotesList with popUpTo(0).
 *
 * ─── UI STATES ────────────────────────────────────────────────────────────
 *
 * 1. No file selected     → File picker button only
 * 2. File selected        → Show backup info card + password field + Restore button
 * 3. isLoading (reading)  → Spinner during readBackupHeader()
 * 4. isLoading (restoring)→ Spinner during full import (~1–5s depending on size)
 * 5. Error                → Error text, user can retry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupScreen(
    onNavigateBack: () -> Unit,
    onRestoreComplete: () -> Unit,
    viewModel: RestoreBackupViewModel = hiltViewModel()
) {
    val context         = LocalContext.current
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val fileReady        by viewModel.fileReady.collectAsState()
    val backupHeader     by viewModel.backupHeader.collectAsState()
    val password         by viewModel.password.collectAsState()
    val showPassword     by viewModel.showPassword.collectAsState()
    val isLoading        by viewModel.isLoading.collectAsState()
    val errorMessage     by viewModel.errorMessage.collectAsState()

    // Derived here — Compose recomputes this whenever any of the three flows above change.
    // Simpler and more reliable than a combine+stateIn in the ViewModel.
    val canRestore = fileReady && password.isNotEmpty() && !isLoading

    // File picker — opens the system file manager filtered to .vnbackup
    // "application/octet-stream" is used because .vnbackup is a custom extension
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Read the display name from the content URI for the UI
            val displayName = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }
            viewModel.onFileSelected(uri, context.contentResolver, displayName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Restore Backup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isLoading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.extraLarge, vertical = Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {

            Spacer(modifier = Modifier.height(Spacing.medium))

            // ── Icon ──────────────────────────────────────────────────────────
            Icon(
                imageVector = Icons.Default.Restore,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Title + subtitle ──────────────────────────────────────────────
            Text(
                text = "RESTORE FROM BACKUP",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color     = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Select your .vnbackup file and enter the vault password\nyou used when you created it.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Step 1: File picker ───────────────────────────────────────────
            OutlinedButton(
                onClick  = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(if (selectedFileName != null) "Change file" else "Select .vnbackup file")
            }

            // ── Backup info card (shown after file is selected) ───────────────
            AnimatedVisibility(visible = fileReady && backupHeader != null) {
                val header = backupHeader
                if (header != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier  = Modifier.padding(Spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text       = selectedFileName ?: "backup.vnbackup",
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text  = "${header.noteCount} notes · ${header.folderCount} folders · v${header.appVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // ── Step 2: Password field (shown after file is selected) ─────────
            AnimatedVisibility(visible = fileReady) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label         = { Text("Vault Password") },
                        placeholder   = { Text("Password used on the original device") },
                        singleLine    = true,
                        enabled       = !isLoading,
                        isError       = errorMessage != null,
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon  = {
                            IconButton(
                                onClick = { viewModel.toggleShowPassword() },
                                enabled = !isLoading
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

                    // Warning: password becomes vault password going forward
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Text("ℹ", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "After restore, this backup password becomes your vault password on this device. You can change it later in Settings → Security.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // ── Error message ─────────────────────────────────────────────────
            errorMessage?.let { err ->
                Text(
                    text      = err,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            // ── Loading indicator (file reading) ──────────────────────────────
            AnimatedVisibility(visible = isLoading && !fileReady) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text("Reading backup file…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Restore button ────────────────────────────────────────────────
            Button(
                onClick  = { viewModel.confirmRestore(context.contentResolver, onRestoreComplete) },
                enabled  = canRestore,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isLoading && fileReady) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Restoring…")
                } else {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(
                        text  = "Restore Notes",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.large))
        }
    }
}