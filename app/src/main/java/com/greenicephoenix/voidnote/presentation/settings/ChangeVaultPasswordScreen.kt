package com.greenicephoenix.voidnote.presentation.settings

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
 * ChangeVaultPasswordScreen — Settings → Security → Change Vault Password
 *
 * ─── SCREEN STATES (driven by ChangePasswordState) ───────────────────────────
 *
 *   Idle         → form with current + new + confirm password fields
 *   Verifying    → form disabled, spinner in the button (~300ms PBKDF2 check)
 *   Reencrypting → non-dismissible progress dialog with note count + progress bar
 *   Success      → success card, "Done" button → popBackStack
 *   Error        → error card, "Try Again" resets to Idle
 *
 * The non-dismissible dialog during Reencrypting is intentional and essential.
 * Killing the app mid-transaction would leave the DB in an inconsistent state
 * (half old-key, half new-key ciphertext) because the @Transaction only guarantees
 * atomicity if it completes — a mid-process kill bypasses Room's rollback.
 * The dialog + "Do not close the app" message is the user-facing mitigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeVaultPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChangeVaultPasswordViewModel = hiltViewModel()
) {
    val state               by viewModel.state.collectAsState()
    val currentPassword     by viewModel.currentPassword.collectAsState()
    val newPassword         by viewModel.newPassword.collectAsState()
    val confirmNewPassword  by viewModel.confirmNewPassword.collectAsState()
    val showCurrentPassword by viewModel.showCurrentPassword.collectAsState()
    val showNewPassword     by viewModel.showNewPassword.collectAsState()
    val currentPasswordError by viewModel.currentPasswordError.collectAsState()

    // Derived validation
    val newPasswordTooShort  = newPassword.isNotEmpty() && newPassword.length < 8
    val passwordsMismatch    = confirmNewPassword.isNotEmpty() && newPassword != confirmNewPassword
    val isBusy               = state is ChangePasswordState.Verifying
    val canSubmit            = currentPassword.isNotEmpty()
            && newPassword.length >= 8
            && newPassword == confirmNewPassword
            && !isBusy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change Vault Password") },
                navigationIcon = {
                    IconButton(
                        onClick  = onNavigateBack,
                        enabled  = state !is ChangePasswordState.Reencrypting
                    ) {
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
            when (state) {

                // ── IDLE / VERIFYING: show the form ───────────────────────────
                is ChangePasswordState.Idle,
                is ChangePasswordState.Verifying -> {
                    PasswordFormContent(
                        currentPassword      = currentPassword,
                        newPassword          = newPassword,
                        confirmNewPassword   = confirmNewPassword,
                        showCurrentPassword  = showCurrentPassword,
                        showNewPassword      = showNewPassword,
                        currentPasswordError = currentPasswordError,
                        newPasswordTooShort  = newPasswordTooShort,
                        passwordsMismatch    = passwordsMismatch,
                        isBusy               = isBusy,
                        canSubmit            = canSubmit,
                        onCurrentPasswordChange    = viewModel::onCurrentPasswordChange,
                        onNewPasswordChange        = viewModel::onNewPasswordChange,
                        onConfirmNewPasswordChange = viewModel::onConfirmNewPasswordChange,
                        onToggleCurrent            = viewModel::toggleShowCurrentPassword,
                        onToggleNew                = viewModel::toggleShowNewPassword,
                        onSubmit                   = viewModel::confirmChange
                    )
                }

                // ── SUCCESS ───────────────────────────────────────────────────
                is ChangePasswordState.Success -> {
                    SuccessContent(onDone = onNavigateBack)
                }

                // ── ERROR ─────────────────────────────────────────────────────
                is ChangePasswordState.Error -> {
                    ErrorContent(
                        message    = (state as ChangePasswordState.Error).message,
                        onTryAgain = viewModel::resetToIdle
                    )
                }

                // Reencrypting handled by dialog overlay below
                else -> Unit
            }

            // ── Non-dismissible re-encryption progress dialog ─────────────────
            if (state is ChangePasswordState.Reencrypting) {
                val reencState = state as ChangePasswordState.Reencrypting
                ReencryptingDialog(
                    progress = reencState.progress,
                    total    = reencState.total
                )
            }
        }
    }
}

// ─── Password form ─────────────────────────────────────────────────────────────

@Composable
private fun PasswordFormContent(
    currentPassword: String,
    newPassword: String,
    confirmNewPassword: String,
    showCurrentPassword: Boolean,
    showNewPassword: Boolean,
    currentPasswordError: String?,
    newPasswordTooShort: Boolean,
    passwordsMismatch: Boolean,
    isBusy: Boolean,
    canSubmit: Boolean,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmNewPasswordChange: (String) -> Unit,
    onToggleCurrent: () -> Unit,
    onToggleNew: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {

        // ── Info card ─────────────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Icon(
                    imageVector        = Icons.Default.Info,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp).padding(top = 2.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Text(
                    text  = "Changing your password re-encrypts all your notes. " +
                            "This may take a few seconds. Do not close the app while it runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // ── Current password ──────────────────────────────────────────────────
        OutlinedTextField(
            value         = currentPassword,
            onValueChange = onCurrentPasswordChange,
            label         = { Text("Current vault password") },
            singleLine    = true,
            enabled       = !isBusy,
            isError       = currentPasswordError != null,
            supportingText = currentPasswordError?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showCurrentPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleCurrent, enabled = !isBusy) {
                    Icon(
                        if (showCurrentPassword) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (showCurrentPassword) "Hide" else "Show"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )

        // ── New password ──────────────────────────────────────────────────────
        OutlinedTextField(
            value         = newPassword,
            onValueChange = onNewPasswordChange,
            label         = { Text("New password") },
            placeholder   = { Text("Min. 8 characters") },
            singleLine    = true,
            enabled       = !isBusy,
            isError       = newPasswordTooShort,
            supportingText = when {
                newPasswordTooShort -> ({ Text("At least 8 characters required",
                    color = MaterialTheme.colorScheme.error) })
                newPassword.length >= 8 -> ({ Text("✓ Good",
                    color = MaterialTheme.colorScheme.primary) })
                else -> null
            },
            keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showNewPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleNew, enabled = !isBusy) {
                    Icon(
                        if (showNewPassword) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (showNewPassword) "Hide" else "Show"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // ── Confirm new password ──────────────────────────────────────────────
        OutlinedTextField(
            value         = confirmNewPassword,
            onValueChange = onConfirmNewPasswordChange,
            label         = { Text("Confirm new password") },
            singleLine    = true,
            enabled       = !isBusy,
            isError       = passwordsMismatch,
            supportingText = when {
                passwordsMismatch -> ({ Text("Passwords do not match",
                    color = MaterialTheme.colorScheme.error) })
                confirmNewPassword.isNotEmpty() && !passwordsMismatch -> ({ Text("✓ Passwords match",
                    color = MaterialTheme.colorScheme.primary) })
                else -> null
            },
            keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
            // Confirm field always hidden — no toggle, intentional UX
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        // ── Submit button ─────────────────────────────────────────────────────
        Button(
            onClick  = onSubmit,
            enabled  = canSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isBusy) {
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
                    text  = "Change Password",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

// ─── Re-encrypting progress dialog ────────────────────────────────────────────

@Composable
private fun ReencryptingDialog(progress: Float, total: Int) {
    Dialog(
        onDismissRequest = {},  // intentionally empty — cannot dismiss during re-encryption
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
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                Icon(
                    imageVector        = Icons.Default.Lock,
                    contentDescription = null,
                    modifier           = Modifier.size(32.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Text(
                    text       = "Changing vault password…",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                // Progress bar — gives user feedback for large vaults
                if (total > 0) {
                    LinearProgressIndicator(
                        progress  = { progress },
                        modifier  = Modifier.fillMaxWidth()
                    )
                    Text(
                        text  = "Re-encrypting note ${(progress * total).toInt()} of $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Warning — essential to prevent data loss
                Text(
                    text      = "Do not close the app.",
                    style     = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color     = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Success ───────────────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(onDone: () -> Unit) {
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
            text       = "Password Changed",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Spacing.small))
        Text(
            text      = "All your notes have been re-encrypted with your new password. " +
                    "Use the new password for future exports and backups.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.extraLarge))
        Button(
            onClick  = onDone,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Done") }
    }
}

// ─── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String, onTryAgain: () -> Unit) {
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
            text       = "Password Change Failed",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Spacing.medium))
        Card(
            colors = CardDefaults.cardColors(
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
        Spacer(modifier = Modifier.height(Spacing.medium))
        Text(
            text      = "Your password has NOT been changed. Your notes are intact.",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.extraLarge))
        Button(
            onClick  = onTryAgain,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Try Again") }
    }
}