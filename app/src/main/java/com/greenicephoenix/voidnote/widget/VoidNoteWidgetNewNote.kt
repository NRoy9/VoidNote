package com.greenicephoenix.voidnote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.greenicephoenix.voidnote.R

/**
 * VoidNoteWidgetNewNote — 1×1 Quick Note widget.
 * Tapping opens QuickCaptureActivity (text input dialog).
 */
class VoidNoteWidgetNewNote : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_new_note)
            views.setOnClickPendingIntent(
                R.id.btn_widget_new_note,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, QuickCaptureActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}