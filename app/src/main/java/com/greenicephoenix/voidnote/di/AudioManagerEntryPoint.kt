package com.greenicephoenix.voidnote.di

import com.greenicephoenix.voidnote.data.storage.AudioStorageManager
import com.greenicephoenix.voidnote.data.storage.VoiceRecorderManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint for accessing audio singletons from Composables.
 *
 * AudioStorageManager and VoiceRecorderManager are @Singleton objects in the
 * Hilt graph. Composables can't use @Inject directly — EntryPointAccessors
 * is the standard Hilt pattern for this case.
 *
 * USAGE in AudioBlockComposable:
 *
 *   val audioEntryPoint = EntryPointAccessors.fromApplication(
 *       LocalContext.current.applicationContext,
 *       AudioManagerEntryPoint::class.java
 *   )
 *   val audioStorage  = audioEntryPoint.audioStorage()
 *   val voiceRecorder = audioEntryPoint.voiceRecorder()
 *
 * PLACE THIS FILE AT:
 *   di/AudioManagerEntryPoint.kt
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioManagerEntryPoint {
    fun audioStorage(): AudioStorageManager
    fun voiceRecorder(): VoiceRecorderManager
}