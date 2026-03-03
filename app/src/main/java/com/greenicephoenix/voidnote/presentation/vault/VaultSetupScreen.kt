package com.greenicephoenix.voidnote.presentation.vault

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * VaultSetupScreen — shown once, after onboarding, before the notes list.
 *
 * CANNOT BE SKIPPED. The user must create a vault password to proceed.
 * This screen is the gate between onboarding and the actual app.
 *
 * DESIGN:
 * - Full black screen, Nothing aesthetic
 * - Lock icon at top (animated in)
 * - Clear warning about password loss
 * - Password + Confirm fields with show/hide toggles
 * - "Generate for me" button for users who want a random password
 * - Generated password displayed clearly so user can save it
 * - "Create Vault" button (disabled until both fields valid + match)
 * - Loading spinner during PBKDF2 derivation (~300ms)
 */
@Composable
fun VaultSetupScreen(
    onVaultCreated: () -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: VaultSetupViewModel = hiltViewModel()
) {
    val password        by viewModel.password.collectAsState()
    val confirmPassword by viewModel.confirmPassword.collectAsState()
    val showPassword    by viewModel.showPassword.collectAsState()
    val showConfirm     by viewModel.showConfirmPassword.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val errorMessage    by viewModel.errorMessage.collectAsState()

    // Derived validation states — drive UI feedback
    val passwordTooShort  = password.isNotEmpty() && password.length < 8
    val passwordsMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    val canConfirm        = password.length >= 8 && password == confirmPassword && !isLoading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.extraLarge, vertical = Spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {

            Spacer(modifier = Modifier.height(Spacing.extraLarge))

            // ── Lock icon ──────────────────────────────────────────────────────
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Title ──────────────────────────────────────────────────────────
            Text(
                text = "CREATE YOUR VAULT",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Your vault password encrypts all your notes.\nIt never leaves your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Warning card ───────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Text(
                        text = "⚠",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "If you forget this password, your notes cannot be recovered. There is no reset option. Write it down and keep it safe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Password field ─────────────────────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Vault Password") },
                placeholder = { Text("Min. 8 characters") },
                singleLine = true,
                isError = passwordTooShort,
                supportingText = {
                    when {
                        passwordTooShort -> Text(
                            "Password must be at least 8 characters",
                            color = MaterialTheme.colorScheme.error
                        )
                        password.isNotEmpty() && password.length >= 8 -> Text(
                            "✓ Good",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleShowPassword() }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Confirm password field ─────────────────────────────────────────
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { viewModel.onConfirmPasswordChange(it) },
                label = { Text("Confirm Password") },
                singleLine = true,
                isError = passwordsMismatch,
                supportingText = {
                    when {
                        passwordsMismatch -> Text(
                            "Passwords do not match",
                            color = MaterialTheme.colorScheme.error
                        )
                        confirmPassword.isNotEmpty() && !passwordsMismatch -> Text(
                            "✓ Passwords match",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                visualTransformation = if (showConfirm) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleShowConfirmPassword() }) {
                        Icon(
                            imageVector = if (showConfirm) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showConfirm) "Hide" else "Show"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Generate password button ───────────────────────────────────────
            // Tonal (secondary) style — present but not the primary CTA
            OutlinedButton(
                onClick = { viewModel.generatePassword() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("Generate a password for me")
            }

            // ── Generated password hint ────────────────────────────────────────
            // Only shown after generate is tapped and password is visible
            AnimatedVisibility(visible = showPassword && password.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Save this password somewhere safe before continuing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        // Show the actual password in monospace so it's easy to read
                        Text(
                            text = password,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // ── API error message ──────────────────────────────────────────────
            errorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // ── Create Vault button ────────────────────────────────────────────
            Button(
                onClick = { viewModel.confirmSetup(onVaultCreated) },
                enabled = canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Create Vault",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.medium),
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
            )

            TextButton(
                onClick  = onNavigateToImport,
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector        = Icons.Default.Restore,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("Restore from existing backup")
            }

            Text(
                text      = "Have a .vnbackup file from another device?",
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.large))
        }
    }
}