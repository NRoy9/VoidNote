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
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import java.io.File

/**
 * ImageBlockComposable — renders a single IMAGE inline block in the note editor.
 *
 * WHAT IT SHOWS:
 * ┌────────────────────────────────┐  ← rounded card
 * │                          [✕]  │  ← delete button top-right
 * │   ┌──────────────────────┐    │
 * │   │                      │    │  ← full-width image, aspect-ratio preserved
 * │   │      image here      │    │
 * │   └──────────────────────┘    │
 * │   Add a caption...            │  ← editable caption (optional)
 * └────────────────────────────────┘
 *
 * COIL LOADING STATES:
 * - Loading  → shimmer-style grey placeholder
 * - Success  → image rendered via AsyncImage
 * - Error    → broken image icon with "Image not found" message
 *   (happens if the file was manually deleted from app storage)
 *
 * CAPTION:
 * Editable inline. No "done" button — caption auto-saves via onCaptionChange
 * which feeds into the ViewModel's debounced save. Same pattern as todo item text.
 *
 * DELETE:
 * The ✕ button in the top-right corner calls onDeleteBlock.
 * The ViewModel deletes the physical file AND the DB row.
 *
 * NOTHING AESTHETIC:
 * - Card with very slight corner rounding (8dp)
 * - Subtle border (1dp, outline.copy(alpha = 0.15f))
 * - No heavy shadows
 * - Caption in small, low-contrast text
 *
 * @param block          The InlineBlock with InlineBlockPayload.Image payload
 * @param onCaptionChange Called on every caption keystroke — ViewModel debounces the save
 * @param onDeleteBlock  Called when ✕ is tapped — ViewModel deletes file + DB row
 * @param modifier       Standard Compose modifier chain
 */
@Composable
fun ImageBlockComposable(
    block: InlineBlock,
    onCaptionChange: (String) -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val payload = block.payload as? InlineBlockPayload.Image ?: return

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Image ──────────────────────────────────────────────────────
                // SubcomposeAsyncImage lets us render custom loading and error states.
                // We load from the absolute file path stored in the payload.
                // File() model tells Coil it's a local file (not a URL).
                SubcomposeAsyncImage(
                    model = File(payload.filePath),
                    contentDescription = payload.caption.ifBlank { "Embedded image" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    loading = {
                        // Placeholder while Coil reads and decodes the file
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    },
                    error = {
                        // File missing or corrupted — show a graceful error state
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
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Image not found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                )

                // ── Caption ────────────────────────────────────────────────────
                // BasicTextField gives us a minimal, unstyled text field — no
                // Material decoration (no underline, no box, no label).
                // We style it ourselves to match the Nothing aesthetic.
                BasicTextField(
                    value = payload.caption,
                    onValueChange = onCaptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Spacing.medium,
                            vertical = Spacing.small
                        ),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    // Cursor colour matches the muted text colour
                    cursorBrush = SolidColor(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            // Placeholder shown when caption is empty
                            if (payload.caption.isEmpty()) {
                                Text(
                                    text = "Add a caption…",
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        fontSize = 13.sp,
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
            // Floating in the top-right corner over the image.
            // Semi-transparent dark circle ensures visibility on both light and
            // dark images. Size kept small (24dp) to not obscure the image.
            IconButton(
                onClick = onDeleteBlock,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.extraSmall)
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove image",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}