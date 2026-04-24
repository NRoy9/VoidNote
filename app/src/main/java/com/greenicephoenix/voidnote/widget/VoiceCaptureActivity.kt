package com.greenicephoenix.voidnote.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.greenicephoenix.voidnote.data.storage.AudioStorageManager
import com.greenicephoenix.voidnote.data.storage.VoiceRecorderManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * VoiceCaptureActivity — a transparent overlay for recording a voice note from the widget.
 *
 * FLOW:
 * 1. Activity opens → checks RECORD_AUDIO permission
 * 2. If permission granted → show recording UI and start recording immediately
 * 3. User taps Stop → audio saved as encrypted .enc file → InlineBlock inserted → finish()
 * 4. Widget list refreshes to show the new note
 *
 * The recording is saved as a note with a voice audio block — identical to the
 * flow used inside the editor. We reuse VoiceRecorderManager and AudioStorageManager
 * directly (accessed via EntryPointAccessors).
 *
 * WHY NOT @AndroidEntryPoint?
 * Same reason as QuickCaptureActivity — transparent Activity pattern.
 * We use EntryPointAccessors to retrieve singletons.
 *
 * NOTE: The audio note is saved as a standalone note (no folder, no title).
 * The title defaults to "Voice Note" and the timestamp is the creation time.
 */
class VoiceCaptureActivity : ComponentActivity() {

    // Mutable state so that granting permission triggers recomposition and
    // the LaunchedEffect(hasPermission) inside VoiceCaptureDialog fires with true.
    private val hasPermissionState = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            // Update state → triggers recomposition → LaunchedEffect starts recording
            hasPermissionState.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve singletons
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java
        )
        val db                  = entryPoint.database()
        val encryption          = entryPoint.encryptionManager()
        val repo                = WidgetNoteRepository(db = db, encryption = encryption)
        val audioStorageManager = entryPoint.audioStorageManager()
        val voiceRecorderManager = entryPoint.voiceRecorderManager()

        // Check permission — if already granted, set state to true immediately.
        // If not, launch the system dialog; the result callback above updates the state.
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            hasPermissionState.value = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            // Read the state here so Compose subscribes to it and recomposes when it changes
            val hasPermission by hasPermissionState
            VoiceCaptureDialog(
                hasPermission        = hasPermission,
                audioStorageManager  = audioStorageManager,
                voiceRecorderManager = voiceRecorderManager,
                onStop = { durationMs, tempFilePath ->
                    lifecycleScope.launch {
                        try {
                            val blockId = java.util.UUID.randomUUID().toString()

                            val encPath = audioStorageManager.encryptRecordingTempFile(tempFilePath, blockId)
                            if (encPath == null) {
                                Toast.makeText(applicationContext, "Failed to encrypt recording", Toast.LENGTH_SHORT).show()
                                finish()
                                return@launch
                            }

                            // Create the note first (FK constraint: note must exist before block)
                            val noteId = repo.insertQuickNote(
                                title   = "Voice Note",
                                content = ""
                            )

                            // Insert the audio inline block
                            val payload = """{"filePath":"$encPath","durationMs":$durationMs}"""
                            val blockEntity = com.greenicephoenix.voidnote.data.local.entity.InlineBlockEntity(
                                id        = blockId,
                                noteId    = noteId,
                                type      = "AUDIO",
                                payload   = payload,
                                createdAt = System.currentTimeMillis()
                            )
                            db.inlineBlockDao().insertBlock(blockEntity)

                            // Refresh widget instances
                            refreshWidgets()

                            Toast.makeText(applicationContext, "Voice note saved", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(applicationContext, "Failed to save recording", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                },
                onDismiss = { finish() }
            )
        }
    }

    private fun refreshWidgets() {
        val manager   = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, VoidNoteWidgetMedium::class.java)
        val ids       = manager.getAppWidgetIds(component)
        val intent    = Intent(applicationContext, VoidNoteWidgetMedium::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }
}

@Composable
private fun VoiceCaptureDialog(
    hasPermission: Boolean,
    audioStorageManager: AudioStorageManager,
    voiceRecorderManager: VoiceRecorderManager,
    onStop: (durationMs: Long, tempFilePath: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isRecording    by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var tempFilePath   by remember { mutableStateOf("") }

    // Pulsing animation for the recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = 1.2f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Start recording as soon as permission is confirmed
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            // Use AudioStorageManager to create the temp file in app-private storage
            val path = audioStorageManager.createRecordingTempFile()
            tempFilePath = path
            voiceRecorderManager.startRecording(path)
            isRecording = true
        }
    }

    // Timer — increments every second while recording
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            elapsedSeconds++
        }
    }

    // Format elapsed time as MM:SS
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeLabel = "%02d:%02d".format(minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier              = Modifier.padding(32.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text  = "RECORDING",
                    style = TextStyle(
                        color         = Color.White,
                        fontSize      = 12.sp,
                        fontFamily    = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    )
                )

                // Pulsing mic icon
                Box(
                    modifier        = Modifier
                        .size(80.dp)
                        .scale(if (isRecording) pulseScale else 1f)
                        .background(Color(0xFF2A2A2A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Recording",
                        tint   = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Elapsed time
                Text(
                    text  = timeLabel,
                    style = TextStyle(
                        color      = Color.White,
                        fontSize   = 32.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )

                // Stop button
                Button(
                    onClick = {
                        isRecording = false
                        voiceRecorderManager.stopRecording()
                        voiceRecorderManager.releaseRecorder()
                        onStop(elapsedSeconds * 1000L, tempFilePath)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text  = "STOP",
                        color = Color.Black,
                        style = TextStyle(fontFamily = FontFamily.Monospace)
                    )
                }

                TextButton(onClick = {
                    voiceRecorderManager.stopRecording()
                    voiceRecorderManager.releaseRecorder()
                    onDismiss()
                }) {
                    Text("CANCEL", color = Color.Gray, style = TextStyle(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}