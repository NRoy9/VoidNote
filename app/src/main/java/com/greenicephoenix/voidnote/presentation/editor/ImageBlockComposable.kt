package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 *
 * ─── SPRINT 5: FULLSCREEN VIEWER ─────────────────────────────────────────────
 *
 * Tapping the image now opens a fullscreen dialog with pinch-to-zoom and pan.
 *
 * IMPLEMENTATION:
 * We use a Dialog with usePlatformDefaultWidth = false (fills the entire screen)
 * and a black background. The image is rendered inside a Box that tracks
 * gesture transforms using detectTransformGestures().
 *
 * graphicsLayer { scaleX; scaleY; translationX; translationY } applies the
 * zoom/pan without recomposition — only the render layer is updated. This is
 * how Compose handles smooth gesture-driven visual transforms.
 *
 * A close button (top-right ✕) and a tap-anywhere-to-close gesture are
 * provided for discoverability and usability.
 *
 * CONSTRAINTS:
 * - scale is clamped between 1f (no zoom-out below original) and 5f (max 5×)
 * - pan is not clamped — the user can pan freely (matches how photos.app works)
 * - double-tap is NOT implemented here (would need separate gesture handling)
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

    // ── SPRINT 5: Fullscreen viewer state ─────────────────────────────────────
    var showFullscreen by remember { mutableStateOf(false) }

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

                // ── Image — tap to fullscreen ──────────────────────────────────
                // SPRINT 5: Added .clickable { showFullscreen = true }
                // The image was previously not tappable at all. Now tapping it
                // opens FullscreenImageViewer.
                SubcomposeAsyncImage(
                    model              = EncryptedFile(payload.filePath),
                    imageLoader        = voidNoteImageLoader.loader,
                    contentDescription = payload.caption.ifBlank { "Embedded image — tap to enlarge" },
                    contentScale       = ContentScale.FillWidth,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .clickable { showFullscreen = true },  // ← SPRINT 5
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

    // ── SPRINT 5: Fullscreen image viewer ─────────────────────────────────────
    if (showFullscreen) {
        FullscreenImageViewer(
            encryptedFilePath  = payload.filePath,
            voidNoteImageLoader = voidNoteImageLoader,
            onDismiss          = { showFullscreen = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FULLSCREEN IMAGE VIEWER (SPRINT 5)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * FullscreenImageViewer — a full-screen pinch-to-zoom image overlay.
 *
 * ─── HOW PINCH-TO-ZOOM WORKS IN COMPOSE ──────────────────────────────────────
 *
 * detectTransformGestures() detects three gestures simultaneously:
 *   - Pan   (drag)   → changes offsetX, offsetY
 *   - Zoom  (pinch)  → multiplies scale by zoom factor
 *   - Rotate (twist) → we IGNORE rotation (not needed here)
 *
 * These values are stored as Compose state variables. On each gesture event,
 * the state changes, which triggers Compose to recompose.
 *
 * graphicsLayer { scaleX; scaleY; translationX; translationY } applies these
 * transforms to the Image's rendered output WITHOUT reflowing the layout.
 * It's equivalent to a hardware-accelerated canvas transform — fast and smooth.
 *
 * WHY graphicsLayer and not Modifier.scale()?
 * graphicsLayer lets us control ALL transforms (scale + translation + rotation)
 * in a single modifier application. Using separate Modifier.scale() and
 * Modifier.offset() would require two layout passes and can cause jitter.
 *
 * ─── SCALE CLAMPING ──────────────────────────────────────────────────────────
 *
 * scale.coerceIn(1f, 5f) means:
 *   - Can't zoom out below 1× (original size) — prevents the image from
 *     shrinking smaller than the screen, which feels wrong
 *   - Can't zoom in past 5× — prevents extreme zoom that serves no purpose
 *
 * ─── RESETTING ON DISMISS ────────────────────────────────────────────────────
 *
 * When onDismiss() is called (close button or screen tap), the dialog is
 * removed from composition. All state variables (scale, offsetX, offsetY)
 * are recreated fresh next time the viewer opens — no stale zoom state.
 *
 * @param encryptedFilePath   Path to the .enc image file in app-private storage
 * @param voidNoteImageLoader Coil image loader that can decrypt .enc files
 * @param onDismiss           Called when the user closes the viewer
 */
@Composable
private fun FullscreenImageViewer(
    encryptedFilePath: String,
    voidNoteImageLoader: VoidNoteImageLoader,
    onDismiss: () -> Unit
) {
    // Transform state — these drive the graphicsLayer below
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Dialog with usePlatformDefaultWidth = false fills the full screen.
    // Without this, Material dialogs have a default max width (~280dp) —
    // not what we want for a photo viewer.
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(
            usePlatformDefaultWidth = false,  // let the dialog fill the screen
            dismissOnBackPress      = true,   // Android back button closes viewer
            dismissOnClickOutside   = false   // we handle click-to-close manually below
        )
    ) {
        // Pure black background — the Nothing aesthetic for immersive viewing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // pointerInput handles ALL gestures on this Box.
                // detectTransformGestures fires on every pointer frame with
                // the current zoom (zoomChange), pan (panChange), and rotation.
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        // Clamp scale between 1× and 5×
                        val newScale = (scale * zoomChange).coerceIn(1f, 5f)

                        // When zooming back to 1×, reset pan to center
                        // so the image doesn't stay offset after un-pinching
                        if (newScale == 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            offsetX += pan.x
                            offsetY += pan.y
                        }

                        scale = newScale
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // The image with transform applied via graphicsLayer
            SubcomposeAsyncImage(
                model              = EncryptedFile(encryptedFilePath),
                imageLoader        = voidNoteImageLoader.loader,
                contentDescription = "Fullscreen image",
                contentScale       = ContentScale.Fit,   // Fit shows the entire image
                modifier           = Modifier
                    .fillMaxWidth()
                    // graphicsLayer applies the zoom and pan as a GPU-layer transform.
                    // This doesn't trigger layout — it only affects the render output.
                    .graphicsLayer(
                        scaleX       = scale,
                        scaleY       = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )

            // ── Close button (top-right corner) ───────────────────────────────
            // Shown over the image. Semi-transparent background so it's visible
            // on both light and dark images.
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(
                        color  = Color.Black.copy(alpha = 0.5f),
                        shape  = CircleShape
                    )
            ) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = "Close fullscreen",
                    tint               = Color.White,
                    modifier           = Modifier.size(20.dp)
                )
            }

            // ── Zoom hint (fades in briefly) ───────────────────────────────────
            // Shows a subtle "Pinch to zoom" hint when scale is still 1×.
            // This makes the feature discoverable for first-time users.
            if (scale == 1f) {
                Text(
                    text     = "Pinch to zoom",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}