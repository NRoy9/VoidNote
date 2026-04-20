package com.greenicephoenix.voidnote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.greenicephoenix.voidnote.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

/**
 * WidgetNotesRemoteViewsService — serves the recent notes list to the medium widget.
 *
 * WHY A SERVICE?
 * ListView/GridView inside a widget cannot use a standard Android Adapter.
 * Instead, Android requires a RemoteViewsService + RemoteViewsFactory pair:
 *   - RemoteViewsService: a bound Service that Android connects to when the widget
 *     needs to populate its list. Android manages the lifecycle.
 *   - RemoteViewsFactory: the actual "adapter" — getCount(), getViewAt(), etc.
 *
 * The Service must be declared in AndroidManifest.xml with the permission:
 *   android:permission="android.permission.BIND_REMOTEVIEWS"
 *
 * This prevents other apps from binding to our service.
 */
class WidgetNotesRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return WidgetNotesRemoteViewsFactory(applicationContext, widgetId)
    }
}

/**
 * WidgetNotesRemoteViewsFactory — the "adapter" for the widget's recent notes list.
 *
 * LIFECYCLE:
 * onCreate()     → called once when the factory is first created
 * onDataSetChanged() → called when we notify the list to refresh (e.g. after saving a note)
 * getViewAt(pos) → called for each visible row — return a RemoteViews for that row
 * onDestroy()    → called when the factory is no longer needed
 *
 * THREADING:
 * onDataSetChanged() and getViewAt() are called on a background thread by Android.
 * We use runBlocking here (not a coroutine) because the RemoteViews factory interface
 * is synchronous — Android expects these calls to block and return a result.
 * This is the standard pattern for widget list factories.
 */
class WidgetNotesRemoteViewsFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    // Cached list of notes. Populated in onDataSetChanged().
    private var notes: List<WidgetNote> = emptyList()

    // Lazy-initialized repository — retrieved via EntryPoint when first needed
    private val repo: WidgetNoteRepository by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        WidgetNoteRepository(
            db         = entryPoint.database(),
            encryption = entryPoint.encryptionManager()
        )
    }

    override fun onCreate() {
        // Initial data load happens in onDataSetChanged()
    }

    override fun onDataSetChanged() {
        // Fetch the 3 most recent notes. runBlocking is correct here —
        // Android calls this on a background thread and expects it to block.
        notes = runBlocking { repo.getRecentNotes(limit = 3) }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    /**
     * Build the RemoteViews for a single row in the recent notes list.
     *
     * IMPORTANT: We use setOnClickFillInIntent() NOT setOnClickPendingIntent().
     * The list has a PendingIntent TEMPLATE set on it (in VoidNoteWidget).
     * setOnClickFillInIntent() provides the per-row data that fills in that template.
     * Android merges them to produce the final Intent when the user taps the row.
     *
     * The fill-in intent carries the noteId so MainActivity knows which note to open.
     */
    override fun getViewAt(position: Int): RemoteViews {
        val note  = notes.getOrNull(position) ?: return getLoadingView()
        val views = RemoteViews(context.packageName, R.layout.widget_item_note)

        // Set the note title — truncate if too long for the widget row
        val displayTitle = note.title.ifBlank { "Untitled" }.take(40)
        views.setTextViewText(R.id.widget_note_title, displayTitle)

        // Set the preview text — first ~80 chars of content
        val displayPreview = note.preview.ifBlank { "No content" }
        views.setTextViewText(R.id.widget_note_preview, displayPreview)

        // Fill-in intent: carries the noteId to MainActivity when tapped
        val fillIntent = Intent().apply {
            putExtra(WidgetConstants.EXTRA_NOTE_ID, note.id)
        }
        views.setOnClickFillInIntent(R.id.widget_note_row, fillIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews {
        // Shown briefly while getViewAt() is loading. A simple empty layout.
        return RemoteViews(context.packageName, R.layout.widget_item_note)
    }

    override fun getViewTypeCount(): Int = 1  // All rows use the same layout

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}