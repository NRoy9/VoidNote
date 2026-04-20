package com.greenicephoenix.voidnote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.greenicephoenix.voidnote.R

/**
 * VoidNoteWidgetRecord — 1×1 Voice Record widget.
 * Tapping opens VoiceCaptureActivity (recording overlay).
 */
class VoidNoteWidgetRecord : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_record)
            views.setOnClickPendingIntent(
                R.id.btn_widget_record,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, VoiceCaptureActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}