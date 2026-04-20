package com.greenicephoenix.voidnote.data.manager

import javax.inject.Inject
import javax.inject.Singleton

/**
 * FolderLockManager — tracks which password-protected folders are unlocked
 * during the current app session.
 *
 * WHY IN-MEMORY ONLY?
 * The requirement is: folders re-lock when the app is backgrounded or the
 * vault locks. In-memory state satisfies this automatically — there is nothing
 * to persist or clean up. When Android kills the process (background), this
 * object is gone. When the vault re-locks and MainActivity recreates, this
 * state is gone. No extra lifecycle management needed.
 *
 * USAGE:
 * - Call unlock(folderId) after the user enters the correct password.
 * - Call isUnlocked(folderId) before navigating into a folder.
 * - Call lock(folderId) when the user taps "Lock Folder" from the menu.
 * - Call lockAll() when the vault locks (call from MainActivity/BiometricLock).
 */
@Singleton
class FolderLockManager @Inject constructor() {

    // Set of folder IDs that are currently unlocked this session
    private val unlockedFolderIds = mutableSetOf<String>()

    /** Returns true if this folder's password has been entered this session. */
    fun isUnlocked(folderId: String): Boolean =
        unlockedFolderIds.contains(folderId)

    /** Mark a folder as unlocked after successful password entry. */
    fun unlock(folderId: String) {
        unlockedFolderIds.add(folderId)
    }

    /** Manually re-lock a single folder (user taps "Lock Folder"). */
    fun lock(folderId: String) {
        unlockedFolderIds.remove(folderId)
    }

    /** Lock ALL folders — call this when the vault locks. */
    fun lockAll() {
        unlockedFolderIds.clear()
    }
}