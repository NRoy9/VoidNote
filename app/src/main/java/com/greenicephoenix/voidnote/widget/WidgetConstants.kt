package com.greenicephoenix.voidnote.widget

/**
 * WidgetConstants — shared constants used across all widget classes.
 *
 * Previously these lived in VoidNoteWidgetSmall. Now that the small widget
 * is split into VoidNoteWidgetNewNote and VoidNoteWidgetRecord, constants
 * that are shared across widget classes live here instead.
 */
object WidgetConstants {
    const val ACTION_OPEN_NOTE = "com.greenicephoenix.voidnote.widget.ACTION_OPEN_NOTE"
    const val EXTRA_NOTE_ID    = "widget_note_id"
}