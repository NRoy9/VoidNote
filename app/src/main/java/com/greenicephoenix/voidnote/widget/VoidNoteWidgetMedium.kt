package com.greenicephoenix.voidnote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.greenicephoenix.voidnote.MainActivity
import com.greenicephoenix.voidnote.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * VoidNoteWidgetMedium — dedicated provider for the 4×2 medium widget.
 *
 * Handles the recent notes list via RemoteViewsService.
 * Completely separate from VoidNoteWidgetSmall — no shared layout logic.
 */
class VoidNoteWidgetMedium : AppWidgetProvider() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            scope.launch {
                val views = buildMediumWidget(context, widgetId)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope.cancel()
    }

    private fun buildMediumWidget(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_medium)

        views.setOnClickPendingIntent(
            R.id.btn_widget_new_note_medium,
            makePendingIntent(context, QuickCaptureActivity::class.java)
        )
        views.setOnClickPendingIntent(
            R.id.btn_widget_record_medium,
            makePendingIntent(context, VoiceCaptureActivity::class.java)
        )
        views.setOnClickPendingIntent(
            R.id.widget_label_medium,
            makeMainIntent(context)
        )

        // Recent notes list via RemoteViewsService
        val serviceIntent = Intent(context, WidgetNotesRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = toUri(0).toUri()
        }
        views.setRemoteAdapter(R.id.widget_notes_list, serviceIntent)
        views.setEmptyView(R.id.widget_notes_list, R.id.widget_empty_text)

        val openNoteTemplate = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = WidgetConstants.ACTION_OPEN_NOTE
                flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_notes_list, openNoteTemplate)

        return views
    }

    private fun <T> makePendingIntent(context: Context, activityClass: Class<T>): PendingIntent {
        val intent = Intent(context, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            activityClass.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun makeMainIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}