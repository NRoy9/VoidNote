package com.greenicephoenix.voidnote.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.greenicephoenix.voidnote.MainActivity
import androidx.compose.foundation.layout.imePadding
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * QuickCaptureActivity — a transparent, dialog-style Activity for quick note input.
 *
 * WHY A SEPARATE ACTIVITY (not inline widget input)?
 * Android's RemoteViews system does NOT support EditText on modern Android (API 31+
 * removed it). The correct and widely-used pattern (Google Keep, Google Tasks, etc.)
 * is to tap the widget → a lightweight Activity slides up with an input field.
 *
 * WHY TRANSPARENT THEME?
 * We declare this Activity with a transparent theme in AndroidManifest.xml
 * (Theme.VoidNote.Transparent). The user sees the launcher "behind" this Activity,
 * giving the illusion of typing directly on the home screen.
 *
 * WIDGET SETTING — OPEN IN EDITOR:
 * If the user has enabled "Open in editor after save" in widget settings, we start
 * MainActivity with the new note's ID after saving. otherwise we save silently
 * and close, then trigger a widget refresh so the new note appears in the list.
 *
 * HILT:
 * This Activity is NOT annotated @AndroidEntryPoint — that would require it to be
 * a Hilt activity, which conflicts with the transparent-dialog approach. Instead we
 * use EntryPointAccessors directly (same pattern as the widget provider).
 */
class QuickCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve Hilt singletons via EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java
        )
        val repo = WidgetNoteRepository(
            db         = entryPoint.database(),
            encryption = entryPoint.encryptionManager()
        )
        val preferencesManager = entryPoint.preferencesManager()

        setContent {
            QuickCaptureDialog(
                onSave = { noteText ->
                    lifecycleScope.launch {
                        // 1. Save the note to the encrypted DB
                        val noteId = repo.insertQuickNote(
                            title   = "",        // Auto-titled as "Quick Note" in repo
                            content = noteText
                        )

                        // 2. Check widget preference: open in editor or save silently
                        val openInEditor = preferencesManager.widgetOpenInEditorFlow.first()

                        // 3. Refresh all widget instances so the new note appears in the list
                        val manager   = AppWidgetManager.getInstance(applicationContext)
                        val component = ComponentName(applicationContext, VoidNoteWidgetMedium::class.java)
                        val ids       = manager.getAppWidgetIds(component)
                        val updateIntent = Intent(applicationContext, VoidNoteWidgetMedium::class.java).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        sendBroadcast(updateIntent)

                        // 4. Navigate or close
                        if (openInEditor) {
                            val editorIntent = Intent(applicationContext, MainActivity::class.java).apply {
                                action = WidgetConstants.ACTION_OPEN_NOTE
                                putExtra(WidgetConstants.EXTRA_NOTE_ID, noteId)
                                flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(editorIntent)
                        } else {
                            Toast.makeText(applicationContext, "Note saved", Toast.LENGTH_SHORT).show()
                        }

                        finish()
                    }
                },
                onDismiss = { finish() }
            )
        }
    }
}

/**
 * The Compose UI for the quick capture dialog.
 * Displayed as a floating card over the transparent Activity window.
 */
@Composable
private fun QuickCaptureDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Background scrim — tapping outside dismisses the dialog
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A)  // Near-black, matching Void Note theme
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────
                Text(
                    text  = "Quick Note",
                    style = TextStyle(
                        color      = Color.White,
                        fontSize   = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Text input ────────────────────────────────────────────────
                // BasicTextField gives us full control over styling.
                // The keyboard appears automatically via LaunchedEffect below.
                BasicTextField(
                    value         = text,
                    onValueChange = { text = it },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 200.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color    = Color.White,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text  = "Start typing...",
                                    style = TextStyle(
                                        color    = Color.Gray,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Buttons ───────────────────────────────────────────────────
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text  = "CANCEL",
                            color = Color.Gray,
                            style = TextStyle(fontFamily = FontFamily.Monospace)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = { if (text.isNotBlank()) onSave(text) else onDismiss() }
                    ) {
                        Text(
                            text  = "SAVE",
                            color = Color.White,
                            style = TextStyle(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }
    }

    // Auto-focus the text field so the keyboard appears immediately
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}