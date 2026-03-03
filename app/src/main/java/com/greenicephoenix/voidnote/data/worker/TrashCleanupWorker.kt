package com.greenicephoenix.voidnote.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greenicephoenix.voidnote.data.local.dao.InlineBlockDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * TrashCleanupWorker — background job that permanently deletes notes
 * that have been in the trash for more than 30 days.
 *
 * ─── WHAT IS WORKMANAGER? ────────────────────────────────────────────────────
 *
 * WorkManager is Android's system for running background tasks that MUST
 * eventually complete, even if the user closes the app or the phone restarts.
 * It is the right tool for periodic maintenance tasks like this one.
 *
 * We schedule this worker to run once per day. Android decides exactly when
 * based on battery and Doze mode, but guarantees it runs within ~24 hours.
 *
 * ─── HOW HILT WORKS WITH WORKMANAGER ─────────────────────────────────────────
 *
 * Normally WorkManager creates Worker instances internally using a no-arg
 * constructor — which means @Inject wouldn't work. The @HiltWorker annotation
 * combined with @AssistedInject tells Hilt to generate a special factory that
 * WorkManager can call to create this worker WITH injected dependencies.
 *
 * Requirements for this to work:
 *   1. @HiltWorker on the class
 *   2. @AssistedInject on the constructor
 *   3. @Assisted on the two required WorkManager params
 *   4. HiltWorkerFactory registered in VoidNoteApplication (done there)
 *
 * ─── THE CRITICAL KOTLIN BUG THIS FILE FIXES ──────────────────────────────────
 *
 * WRONG (compile error):
 *   expiredNoteIds.forEach { noteId ->
 *       inlineBlockDao.deleteBlocksForNote(noteId)  // ERROR: suspend in non-suspend lambda
 *   }
 *
 * WHY IT FAILS: .forEach {} takes a regular lambda `(T) -> Unit`, not a
 * suspend lambda. Kotlin does not allow calling suspend functions from a
 * regular lambda, even if the outer function is itself a suspend function.
 *
 * RIGHT (compiles and works):
 *   for (noteId in expiredNoteIds) {
 *       inlineBlockDao.deleteBlocksForNote(noteId)  // OK — for loop inherits suspend context
 *   }
 *
 * A `for` loop runs directly in the surrounding coroutine body, so suspend
 * calls work fine. This is the idiomatic Kotlin pattern for suspend loops.
 */
@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val noteDao: NoteDao,
    private val inlineBlockDao: InlineBlockDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        /**
         * Unique name for this periodic work request.
         * Passing this name to enqueueUniquePeriodicWork() ensures only one
         * instance of this job is ever scheduled — no duplicates on app restart.
         */
        const val WORK_NAME = "void_note_trash_cleanup"

        /** 30 days expressed in milliseconds. */
        private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
    }

    /**
     * This is the entry point called by WorkManager when it's time to run.
     * It runs inside a coroutine, so all suspend function calls here are safe.
     *
     * Steps:
     * 1. Calculate the cutoff timestamp (now minus 30 days)
     * 2. Find all note IDs that are trashed and older than the cutoff
     * 3. Delete inline blocks for those notes first (so no orphan rows remain)
     * 4. Delete the expired notes themselves
     */
    override suspend fun doWork(): Result {
        return try {
            val cutoffTime = System.currentTimeMillis() - THIRTY_DAYS_MS

            // Step 1: Get the IDs of expired trashed notes.
            // We must fetch IDs BEFORE deleting the notes, because once the
            // notes are gone we can't find their blocks anymore.
            val expiredNoteIds: List<String> = noteDao.getExpiredTrashedNoteIds(cutoffTime)

            if (expiredNoteIds.isEmpty()) {
                android.util.Log.d("TrashCleanup", "No expired notes to delete")
                return Result.success()
            }

            // Step 2: Delete inline blocks for each expired note.
            // IMPORTANT: Use a `for` loop, not .forEach {}, because
            // deleteBlocksForNote() is a suspend function. Regular lambdas
            // like those passed to forEach {} cannot call suspend functions.
            for (noteId in expiredNoteIds) {
                inlineBlockDao.deleteBlocksForNote(noteId)
            }

            // Step 3: Delete the expired notes themselves.
            noteDao.deleteExpiredTrashedNotes(cutoffTime)

            android.util.Log.d(
                "TrashCleanup",
                "Auto-deleted ${expiredNoteIds.size} note(s) from trash (>30 days old)"
            )

            Result.success()

        } catch (e: Exception) {
            android.util.Log.e("TrashCleanup", "Cleanup failed — will retry", e)
            // Result.retry() causes WorkManager to try again with exponential backoff.
            // The next attempt won't run until the backoff period elapses (min 10s, max 6h).
            Result.retry()
        }
    }
}