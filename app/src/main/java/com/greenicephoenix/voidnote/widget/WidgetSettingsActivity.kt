package com.greenicephoenix.voidnote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * WidgetSettingsActivity — the widget configuration screen.
 *
 * This Activity is shown when the user long-presses a widget and taps
 * "Widget settings" (or automatically when the widget is first placed on the
 * launcher, if declared as the `configure` activity in widget_info XML).
 *
 * CURRENT SETTINGS:
 * - Open in editor after save: if ON, saving a quick note opens the full editor.
 *   If OFF, the note is saved silently and the widget refreshes.
 *
 * HOW CONFIGURE ACTIVITIES WORK:
 * Android delivers the widgetId via Intent.getIntExtra(EXTRA_APPWIDGET_ID).
 * We MUST call setResult(RESULT_OK, intent-with-widgetId) when the user confirms.
 * If we call setResult(RESULT_CANCELED) or finish() without setting a result,
 * Android will NOT place the widget on the launcher.
 */
class WidgetSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result = CANCELED in case user presses back
        setResult(RESULT_CANCELED)

        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java
        )
        val preferencesManager = entryPoint.preferencesManager()

        setContent {
            var openInEditor by remember { mutableStateOf(false) }

            // Load current preference
            LaunchedEffect(Unit) {
                openInEditor = preferencesManager.widgetOpenInEditorFlow.first()
            }

            WidgetSettingsScreen(
                openInEditor  = openInEditor,
                onToggle      = { openInEditor = it },
                onConfirm = {
                    lifecycleScope.launch {
                        preferencesManager.setWidgetOpenInEditor(openInEditor)

                        // Tell Android the widget was configured successfully
                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        }
                        setResult(RESULT_OK, resultValue)
                        finish()
                    }
                }
            )
        }
    }
}

@Composable
private fun WidgetSettingsScreen(
    openInEditor: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                Text(
                    text  = "WIDGET SETTINGS",
                    style = TextStyle(
                        color         = Color.White,
                        fontSize      = 14.sp,
                        fontFamily    = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Toggle: Open in editor after save ─────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text  = "Open in editor after save",
                            style = TextStyle(color = Color.White, fontSize = 15.sp)
                        )
                        Text(
                            text  = "Opens the full editor after saving a quick note",
                            style = TextStyle(color = Color.Gray, fontSize = 12.sp),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Switch(
                        checked         = openInEditor,
                        onCheckedChange = onToggle,
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = Color.Black,
                            checkedTrackColor   = Color.White,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF3A3A3A)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text  = "DONE",
                        color = Color.Black,
                        style = TextStyle(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    }
}