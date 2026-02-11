package com.greenicephoenix.voidnote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Void Note
 *
 * @HiltAndroidApp triggers Hilt's code generation including a base class
 * for your application that serves as the application-level dependency container.
 *
 * This is the entry point for dependency injection throughout the app.
 */
@HiltAndroidApp
class VoidNoteApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Application initialization
        // We'll add more setup here later (e.g., WorkManager, Firebase, etc.)
    }
}