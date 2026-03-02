package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
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
 * KEY CHANGE FROM SPRINT 5 INITIAL VERSION:
 * Previously used `model = File(path)` — Coil's default file fetcher which
 * reads raw bytes and tries to decode them as JPEG. This broke with encryption
 * because the file contains AES-256-GCM ciphertext, not a JPEG.
 *
 * Now uses `model = EncryptedFile(path)` with `imageLoader = voidNoteImageLoader.loader`.
 * EncryptedFileFetcher intercepts the load, decrypts the bytes in memory, and
 * returns plain image bytes to Coil's BitmapDecoder for rendering.
 *
 * The decrypted bytes exist only in RAM during rendering — never written to disk.
 *
 * VISUAL STRUCTURE:
 * ┌────────────────────────────────┐
 * │                          [✕]  │  ← delete button
 * │   ┌──────────────────────┐    │
 * │   │    image renders     │    │  ← decrypted on-the-fly by Coil + EncryptedFileFetcher
 * │   └──────────────────────┘    │
 * │   Add a caption...            │  ← editable, auto-saves
 * └────────────────────────────────┘
 *
 * @param block                 InlineBlock with InlineBlockPayload.Image payload
 * @param voidNoteImageLoader   Injected singleton with EncryptedFileFetcher registered
 * @param onCaptionChange       Called on each caption keystroke
 * @param onDeleteBlock         Called when ✕ tapped — ViewModel deletes file + DB row
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

    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(8.dp),
        border    = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Image ──────────────────────────────────────────────────────
                // model = EncryptedFile(path) — triggers EncryptedFileFetcher.
                // imageLoader = voidNoteImageLoader.loader — the custom loader
                //   that has EncryptedFileFetcher registered. Without this,
                //   Coil uses the global default loader which doesn't know about
                //   our encryption and would fail to decode the .enc file.
                SubcomposeAsyncImage(
                    model             = EncryptedFile(payload.filePath),
                    imageLoader       = voidNoteImageLoader.loader,
                    contentDescription = payload.caption.ifBlank { "Embedded image" },
                    contentScale      = ContentScale.FillWidth,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    loading = {
                        // Shimmer-style placeholder while decryption + decode runs
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    },
                    error = {
                        // File missing, decryption failed, or data tampered
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(32.dp)
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

                // ── Caption ────────────────────────────────────────────────────
                BasicTextField(
                    value       = payload.caption,
                    onValueChange = onCaptionChange,
                    modifier    = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                    textStyle   = TextStyle(
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (payload.caption.isEmpty()) {
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

            // ── Delete button ──────────────────────────────────────────────────
            IconButton(
                onClick  = onDeleteBlock,
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
}