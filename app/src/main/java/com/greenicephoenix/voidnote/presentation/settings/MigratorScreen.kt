package com.greenicephoenix.voidnote.presentation.settings

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.AppActivityState
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * MigratorScreen — Settings → Data Management → Import from Another App
 *
 * UI flow:
 *   Idle      → Three format cards (Evernote / Keep / Notion)
 *               User taps a card → file picker launches for that format
 *   ReadingFile → full-screen spinner
 *   Preview   → file info card + note count + Import button
 *   Importing → non-dismissible progress dialog ("Do not close the app")
 *   Success   → summary card + Done
 *   Error     → error card + Try Again
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigratorScreen(
    onNavigateBack: () -> Unit,
    viewModel: MigratorViewModel = hiltViewModel()
) {
    val state   = viewModel.state.collectAsState().value
    val context = LocalContext.current

    // ── One launcher per format — each passes different MIME types ────────────
    val evernoteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        viewModel.onFileSelected(MigratorFormat.EVERNOTE, uri, context.contentResolver, name)
    }

    val keepLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        viewModel.onFileSelected(MigratorFormat.KEEP, uri, context.contentResolver, name)
    }

    val notionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        viewModel.onFileSelected(MigratorFormat.NOTION, uri, context.contentResolver, name)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from Another App") },
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {

                // ── Format picker ─────────────────────────────────────────────
                is MigratorState.Idle -> {
                    IdleContent(
                        onPickEvernote = {
                            AppActivityState.suppressLock = true
                            evernoteLauncher.launch(MigratorFormat.EVERNOTE.mimeTypes)
                        },
                        onPickKeep = {
                            AppActivityState.suppressLock = true
                            keepLauncher.launch(MigratorFormat.KEEP.mimeTypes)
                        },
                        onPickNotion = {
                            AppActivityState.suppressLock = true
                            notionLauncher.launch(MigratorFormat.NOTION.mimeTypes)
                        }
                    )
                }

                // ── Reading spinner ───────────────────────────────────────────
                is MigratorState.ReadingFile -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                            CircularProgressIndicator()
                            Text("Reading ${s.format.label} file…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }

                // ── Preview ───────────────────────────────────────────────────
                is MigratorState.Preview -> {
                    PreviewContent(
                        state    = s,
                        onImport = { viewModel.confirmImport(context.contentResolver) },
                        onBack   = viewModel::reset
                    )
                }

                // ── Success ───────────────────────────────────────────────────
                is MigratorState.Success -> {
                    SuccessContent(state = s, onDone = onNavigateBack)
                }

                // ── Error ─────────────────────────────────────────────────────
                is MigratorState.Error -> {
                    ErrorContent(message = s.message, onTryAgain = viewModel::reset)
                }

                // Importing → handled by overlay below
                else -> Unit
            }

            // Non-dismissible dialog while import is running
            if (state is MigratorState.Importing) {
                ImportingDialog()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IDLE — format picker
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    onPickEvernote : () -> Unit,
    onPickKeep     : () -> Unit,
    onPickNotion   : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Spacer(Modifier.height(Spacing.small))

        Icon(
            imageVector        = Icons.Default.MoveToInbox,
            contentDescription = null,
            modifier           = Modifier.size(52.dp).align(Alignment.CenterHorizontally),
            tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        Spacer(Modifier.height(Spacing.small))

        Text(
            text      = "Move your notes to Void Note",
            style     = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
        Text(
            text      = "Your imported notes will be encrypted and added to your vault. " +
                    "Existing notes will not be deleted.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.small))

        // One card per format
        listOf(
            Triple(MigratorFormat.EVERNOTE, "Export from Evernote → File → Export Notes → ENEX format", onPickEvernote),
            Triple(MigratorFormat.KEEP,     "Google Takeout (takeout.google.com) → select Keep → download ZIP", onPickKeep),
            Triple(MigratorFormat.NOTION,   "Notion Settings → Export workspace → Markdown & CSV → download ZIP", onPickNotion),
        ).forEach { (fmt, hint, onPick) ->
            FormatCard(format = fmt, hint = hint, onClick = onPick)
        }
    }
}

@Composable
private fun FormatCard(
    format  : MigratorFormat,
    hint    : String,
    onClick : () -> Unit
) {
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment     = Alignment.Top
        ) {
            Text(format.emoji, fontSize = 28.sp, modifier = Modifier.padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text       = format.label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = format.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text  = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreviewContent(
    state    : MigratorState.Preview,
    onImport : () -> Unit,
    onBack   : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Spacer(Modifier.height(Spacing.large))

        Text(
            "${state.format.emoji}  ${state.format.label}",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.fillMaxWidth(),
            textAlign  = TextAlign.Center
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("File",  style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(state.fileName, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier.weight(1f, fill = false),
                        maxLines   = 1)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Notes found", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(state.noteCount.toString(),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Info card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(Modifier.padding(Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Icon(Icons.Default.Info, null, Modifier.size(16.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Notes will be encrypted and added to your vault. " +
                            "Importing the same file twice will create duplicates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onImport,
            enabled  = state.noteCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.small))
            Text("Import ${state.noteCount} Note${if (state.noteCount != 1) "s" else ""}")
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Choose Different File")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IMPORTING DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ImportingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.padding(Spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.large)
            ) {
                CircularProgressIndicator()
                Text("Importing notes…", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                Text("Encrypting and saving your notes.\nDo not close the app.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUCCESS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(state: MigratorState.Success, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Spacing.large))
        Text("Import Complete", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.medium))
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(Spacing.large), Arrangement.spacedBy(Spacing.small)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Notes imported"); Text(state.notesImported.toString(), fontWeight = FontWeight.Bold)
                }
                if (state.foldersCreated > 0) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Folders created"); Text(state.foldersCreated.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.extraLarge))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String, onTryAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(Spacing.large))
        Text("Import Failed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.medium))
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(message, Modifier.padding(Spacing.large),
                color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(Spacing.extraLarge))
        Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
    }
}