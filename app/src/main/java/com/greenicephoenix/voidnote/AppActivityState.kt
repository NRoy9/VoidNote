package com.greenicephoenix.voidnote

/**
 * Global flag to suppress biometric re-lock when the app is temporarily
 * covered by a system activity (file picker, camera, share sheet, etc.).
 *
 * Set suppressLock = true BEFORE launching any ActivityResultLauncher.
 * MainActivity.onResume() clears it automatically.
 *
 * WHY A SINGLETON AND NOT VIEWMODEL STATE?
 * MainActivity owns the lock. It has no reference to individual screen
 * ViewModels. A simple object is the least-coupling solution.
 */
object AppActivityState {
    var suppressLock: Boolean = false
}