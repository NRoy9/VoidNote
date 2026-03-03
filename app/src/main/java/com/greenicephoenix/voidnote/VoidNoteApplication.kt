package com.greenicephoenix.voidnote

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.greenicephoenix.voidnote.data.worker.TrashCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * VoidNoteApplication — the app's entry point, created once when the process starts.
 *
 * @HiltAndroidApp triggers Hilt's code generation for the whole app.
 * This class MUST be declared in AndroidManifest.xml:
 *   android:name=".VoidNoteApplication"
 * (Already done from Sprint 1 — no change needed in the manifest.)
 *
 * ─── WORKMANAGER + HILT INTEGRATION ──────────────────────────────────────────
 *
 * To inject dependencies into a Worker via @HiltWorker, we must:
 *
 * 1. Implement Configuration.Provider — this interface has one property,
 *    workManagerConfiguration, that WorkManager calls to get its setup.
 *    We return a Configuration that uses Hilt's factory instead of the
 *    default factory (which can't inject dependencies).
 *
 * 2. Inject HiltWorkerFactory — Hilt generates this class automatically.
 *    It knows how to create every @HiltWorker class with all their
 *    @AssistedInject dependencies wired in.
 *
 * WITHOUT this setup, WorkManager would try to call TrashCleanupWorker()
 * with no arguments (its default factory), fail because no no-arg constructor
 * exists, and crash with an "Could not instantiate Worker" exception at runtime.
 *
 * ─── FIX: KEEP not KEEP_EXISTING ─────────────────────────────────────────────
 *
 * ExistingPeriodicWorkPolicy.KEEP_EXISTING does not exist in WorkManager.
 * The correct value is ExistingPeriodicWorkPolicy.KEEP.
 *
 * KEEP means: if a job with this name is already scheduled, do nothing —
 * don't replace it, don't reset its timer. This is exactly what we want
 * so the daily cleanup isn't reset to 0 every time the app starts.
 */
@HiltAndroidApp
class VoidNoteApplication : Application(), Configuration.Provider {

    /**
     * Hilt injects this after the Application is created but before
     * getWorkManagerConfiguration() is called by WorkManager.
     * lateinit is safe here — the injection lifecycle guarantees this.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        scheduleTrashCleanup()
    }

    /**
     * WorkManager reads this property to configure itself.
     * We swap in Hilt's factory so @HiltWorker classes can receive
     * their injected dependencies (NoteDao, InlineBlockDao) at runtime.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Schedule the daily trash auto-delete job.
     *
     * PeriodicWorkRequest with a 1-day interval means WorkManager will run
     * TrashCleanupWorker approximately once every 24 hours. The exact time
     * is chosen by Android based on battery and Doze mode — a few hours
     * of variance is normal and acceptable for a cleanup job.
     *
     * ExistingPeriodicWorkPolicy.KEEP: if a job named WORK_NAME already
     * exists (e.g. the app restarted), leave it alone. We don't want to
     * reset the 24h timer on every app launch.
     */
    private fun scheduleTrashCleanup() {
        val cleanupRequest = PeriodicWorkRequestBuilder<TrashCleanupWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TrashCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,   // ← KEEP, not KEEP_EXISTING
            cleanupRequest
        )
    }
}