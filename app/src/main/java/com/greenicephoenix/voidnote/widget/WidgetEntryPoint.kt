package com.greenicephoenix.voidnote.widget

import com.greenicephoenix.voidnote.data.local.VoidNoteDatabase
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import com.greenicephoenix.voidnote.data.storage.AudioStorageManager
import com.greenicephoenix.voidnote.data.storage.VoiceRecorderManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * WidgetEntryPoint — the bridge between Android widgets and Hilt.
 *
 * WHY THIS EXISTS:
 * Android widgets (AppWidgetProvider) are instantiated by the Android OS, not
 * by Hilt. That means @Inject constructor doesn't work in a widget class. The
 * @EntryPoint pattern is the official Hilt solution: declare the dependencies
 * you need here, then retrieve them at runtime using EntryPointAccessors.
 *
 * HOW IT WORKS:
 * Hilt generates an implementation of this interface that returns the actual
 * singleton instances from the SingletonComponent (the same instances your
 * ViewModels and Repositories use). We call it from the widget like this:
 *
 *   val ep = EntryPointAccessors.fromApplication(
 *       context.applicationContext,
 *       WidgetEntryPoint::class.java
 *   )
 *   val db = ep.database()
 *
 * This gives us the SAME database singleton that Room uses everywhere else —
 * no second DB connection, no data inconsistency.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun database(): VoidNoteDatabase
    fun encryptionManager(): NoteEncryptionManager
    fun preferencesManager(): PreferencesManager
    fun audioStorageManager(): AudioStorageManager
    fun voiceRecorderManager(): VoiceRecorderManager
}