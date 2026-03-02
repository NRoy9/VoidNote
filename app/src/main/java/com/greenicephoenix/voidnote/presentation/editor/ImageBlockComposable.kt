package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.greenicephoenix.voidnote.data.storage.EncryptedFile
import com.greenicephoenix.voidnote.data.storage.VoidNoteImageLoader
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * ImageBlockComposable — renders a single encrypted IMAGE inline block.
 *
 * ─── CURSOR FIX ───────────────────────────────────────────────────────────────
 *
 * PROBLEM (same root cause as TodoItem cursor bug):
 * Using BasicTextField(value = payload.caption, ...) drives the text field with
 * a plain String from the ViewModel. On each keystroke:
 *   1. User types → onValueChange fires → ViewModel.updateImageCaption() called
 *   2. DB write → Room Flow emits → ViewModel state updates
 *   3. Recomposition: new payload.caption String passed to BasicTextField
 *   4. BasicTextField resets its internal cursor to the END of the string
 *      (Compose doesn't know where the cursor was — it only got a String)
 *
 * Result: typing "laptop" quickly scrambles to "laopt" as characters land
 * at the end rather than at the cursor position.
 *
 * FIX:
 * Use local TextFieldValue state which carries explicit cursor position:
 *   var captionValue by remember(block.id) { mutableStateOf(TextFieldValue(payload.caption)) }
 *
 * The local state OWNS the cursor position. We only sync the text from the
 * ViewModel payload (external source of truth), preserving cursor on each sync:
 *   if (payload.caption != captionValue.text) {
 *       captionValue = captionValue.copy(text = payload.caption)
 *   }
 *
 * captionValue.copy(text = ...) keeps the existing selection/cursor intact
 * while updating the text content — so the cursor never jumps.
 *
 * WHY remember(block.id)?
 * If the block is replaced entirely (different blockId), we want a fresh
 * TextFieldValue starting at position 0. Using block.id as the key ensures
 * the state resets when the block changes but persists through recompositions.
 *
 * ─── DELETE CONFIRMATION ──────────────────────────────────────────────────────
 *
 * Images are encrypted and stored only in app-private filesDir — they cannot
 * be recovered once deleted. A simple accidental tap on the ✕ button would
 * permanently destroy the file. We show a confirmation dialog before deleting.
 */
@Composable
fun ImageBlockComposable(
    block: InlineBlock,
    voidNoteImageLoader: VoidNoteImageLoader,
    onCaptionChange: (String) -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val payload = block.payload as? InlineBlockPayload.Image ?: return

    // ── Cursor fix: local TextFieldValue tracks cursor position ───────────────
    var captionValue by remember(block.id) {
        mutableStateOf(TextFieldValue(payload.caption))
    }

    // Sync text from ViewModel without disturbing cursor.
    // LaunchedEffect fires when payload.caption changes externally (e.g. after
    // a save round-trip). We only update the text part, not the selection.
    LaunchedEffect(payload.caption) {
        if (payload.caption != captionValue.text) {
            captionValue = captionValue.copy(text = payload.caption)
        }
    }

    // ── Delete confirmation state ─────────────────────────────────────────────
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(8.dp),
        border    = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Image ──────────────────────────────────────────────────────
                SubcomposeAsyncImage(
                    model              = EncryptedFile(payload.filePath),
                    imageLoader        = voidNoteImageLoader.loader,
                    contentDescription = payload.caption.ifBlank { "Embedded image" },
                    contentScale       = ContentScale.FillWidth,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier           = Modifier.size(32.dp)
                                )
                                Text(
                                    text  = "Image not found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                )

                // ── Caption (cursor-fixed BasicTextField) ──────────────────────
                BasicTextField(
                    value         = captionValue,
                    onValueChange = { newValue ->
                        captionValue = newValue
                        // Only propagate text changes to ViewModel — selection/cursor
                        // is owned locally and must NOT be sent to the DB.
                        if (newValue.text != payload.caption) {
                            onCaptionChange(newValue.text)
                        }
                    },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                    textStyle     = TextStyle(
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush   = SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (captionValue.text.isEmpty()) {
                                Text(
                                    text     = "Add a caption…",
                                    style    = TextStyle(
                                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        fontSize  = 13.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // ── Delete button — opens confirmation dialog ──────────────────────
            IconButton(
                onClick  = { showDeleteConfirm = true },  // ← dialog, not direct delete
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.extraSmall)
                    .size(28.dp)
                    .background(
                        color  = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        shape  = CircleShape
                    )
            ) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = "Remove image",
                    tint               = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier           = Modifier.size(14.dp)
                )
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    // Shown before permanently deleting an encrypted image.
    // Images live only in app-private filesDir and cannot be recovered once deleted.
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Image?") },
            text  = {
                Text(
                    "This image is stored only inside Void Note.\n\n" +
                            "Once deleted it cannot be recovered."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDeleteBlock() },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") }
            }
        )
    }
}