package com.greenicephoenix.voidnote.presentation.editor

import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.data.storage.AudioStorageManager
import com.greenicephoenix.voidnote.data.storage.VoiceRecorderManager
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AudioBlockComposable — renders a single encrypted AUDIO inline block.
 *
 * ─── DELETE CONFIRMATION ──────────────────────────────────────────────────────
 *
 * Voice notes are encrypted and stored only in app-private filesDir.
 * Once deleted, the .enc file is gone permanently — unlike gallery photos
 * which remain in their original location.
 *
 * Tapping the ✕ button shows a confirmation dialog before calling onDeleteBlock.
 * The dialog uses "Delete" / "Keep" (not "Cancel") to be explicit that keeping
 * is the safe choice.
 *
 * ─── PLAYBACK ARCHITECTURE ────────────────────────────────────────────────────
 *
 * All playback state (isPlaying, elapsedMs, MediaPlayer instance) lives locally
 * in this Composable — not in the ViewModel. The ViewModel only owns whether
 * the block exists and its file path / duration.
 *
 * Flow:
 *   Play tap → decrypt .enc → ByteArray in memory → EncryptedAudioDataSource
 *           → MediaPlayer.setDataSource() → player.prepare() → player.start()
 *   Timer coroutine ticks every 100ms while playing
 *   onCompletion → reset to IDLE
 */
@Composable
fun AudioBlockComposable(
    block: InlineBlock,
    audioStorage: AudioStorageManager,
    voiceRecorder: VoiceRecorderManager,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val payload = block.payload as? InlineBlockPayload.Audio ?: return

    // ── Playback state ────────────────────────────────────────────────────────
    var playbackState by remember { mutableStateOf(PlaybackState.IDLE) }
    var mediaPlayer   by remember { mutableStateOf<MediaPlayer?>(null) }
    var elapsedMs     by remember { mutableLongStateOf(0L) }
    val scope         = rememberCoroutineScope()

    // ── Delete confirmation state ─────────────────────────────────────────────
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ── Waveform — deterministic from block.id ────────────────────────────────
    val waveformHeights = remember(block.id) {
        val rng = java.util.Random(block.id.hashCode().toLong())
        List(36) { 0.15f + rng.nextFloat() * 0.85f }
    }

    // ── Playback elapsed timer ────────────────────────────────────────────────
    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.PLAYING) {
            while (true) {
                delay(100)
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    elapsedMs = mp.currentPosition.toLong()
                } else {
                    break
                }
            }
        }
    }

    DisposableEffect(block.id) {
        onDispose { mediaPlayer?.release(); mediaPlayer = null }
    }

    // ── Playback helpers ──────────────────────────────────────────────────────
    fun startPlayback() {
        scope.launch {
            playbackState = PlaybackState.LOADING
            elapsedMs = 0L

            val bytes = audioStorage.decryptToBytes(payload.filePath)
            if (bytes == null) { playbackState = PlaybackState.ERROR; return@launch }

            val player = voiceRecorder.createPlayer(bytes) {
                mediaPlayer = null; elapsedMs = 0L; playbackState = PlaybackState.IDLE
            }
            if (player == null) { playbackState = PlaybackState.ERROR; return@launch }

            mediaPlayer = player
            player.start()
            playbackState = PlaybackState.PLAYING
        }
    }

    fun pausePlayback()  { mediaPlayer?.pause(); playbackState = PlaybackState.PAUSED }
    fun resumePlayback() { mediaPlayer?.start(); playbackState = PlaybackState.PLAYING }
    fun stopPlayback()   { mediaPlayer?.release(); mediaPlayer = null; elapsedMs = 0L; playbackState = PlaybackState.IDLE }

    // ── UI ────────────────────────────────────────────────────────────────────
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        border    = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                // ── Play/Pause/Loading button ──────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (playbackState) {
                        PlaybackState.LOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        PlaybackState.PLAYING -> {
                            IconButton(onClick = { pausePlayback() }) {
                                Icon(Icons.Default.Pause, "Pause", Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        PlaybackState.PAUSED -> {
                            IconButton(onClick = { resumePlayback() }) {
                                Icon(Icons.Default.PlayArrow, "Resume", Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        PlaybackState.ERROR -> {
                            IconButton(onClick = { startPlayback() }) {
                                Icon(Icons.Default.Refresh, "Retry", Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        PlaybackState.IDLE -> {
                            IconButton(onClick = { startPlayback() }) {
                                Icon(Icons.Default.PlayArrow, "Play", Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // ── Waveform + duration ────────────────────────────────────────
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (playbackState == PlaybackState.ERROR) {
                        Text("Could not play audio", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        WaveformBars(
                            heights          = waveformHeights,
                            isPlaying        = playbackState == PlaybackState.PLAYING,
                            progressFraction = if (payload.durationMs > 0L)
                                (elapsedMs.toFloat() / payload.durationMs).coerceIn(0f, 1f) else 0f,
                            modifier         = Modifier.fillMaxWidth().height(32.dp)
                        )
                    }
                    val displayMs = if (playbackState == PlaybackState.PLAYING ||
                        playbackState == PlaybackState.PAUSED) elapsedMs
                    else payload.durationMs
                    Text(
                        text  = formatAudioDuration(displayMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Delete button — opens confirmation dialog ──────────────────────
            IconButton(
                onClick  = { showDeleteConfirm = true },  // ← confirmation, not direct delete
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove voice note",
                    tint     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    // Shown before permanently deleting an encrypted voice note.
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Delete Voice Note?") },
            text  = {
                Text(
                    "This recording is stored only inside Void Note.\n\n" +
                            "Once deleted it cannot be recovered."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        stopPlayback()                    // stop before deleting
                        showDeleteConfirm = false
                        onDeleteBlock()
                    },
                    colors = ButtonDefaults.textButtonColors(
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

// ─────────────────────────────────────────────────────────────────────────────
// WaveformBars (unchanged from sprint 6)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WaveformBars(
    heights: List<Float>,
    isPlaying: Boolean,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "wavePhase"
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        heights.forEachIndexed { index, baseHeight ->
            val fraction     = index.toFloat() / heights.size
            val isPlayed     = fraction < progressFraction
            val animatedHeight = if (isPlaying) {
                val phase    = (animPhase + index * 0.04f) % 1f
                val sinValue = kotlin.math.sin(phase * 2 * Math.PI.toFloat())
                (baseHeight * 0.6f + (0.4f + sinValue * 0.4f) * 0.4f).coerceIn(0.1f, 1f)
            } else baseHeight

            val barColor = when {
                isPlayed  -> MaterialTheme.colorScheme.primary
                isPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else      -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight(animatedHeight)
                .clip(RoundedCornerShape(2.dp)).background(barColor))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RecordingSheet (unchanged from sprint 6)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecordingSheet(isVisible: Boolean, elapsedMs: Long, onStopClick: () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.expandVertically(tween(200), Alignment.Bottom) +
                androidx.compose.animation.fadeIn(tween(150)),
        exit  = androidx.compose.animation.shrinkVertically(tween(150), Alignment.Bottom) +
                androidx.compose.animation.fadeOut(tween(100))
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsingDot()
                        Text("RECORDING", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontSize = 11.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                        Text(formatAudioDuration(elapsedMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                    }
                    FilledTonalIconButton(onClick = onStopClick, modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Icon(Icons.Default.Stop, "Stop recording", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.error.copy(alpha = alpha), CircleShape))
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

enum class PlaybackState { IDLE, LOADING, PLAYING, PAUSED, ERROR }

fun formatAudioDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}