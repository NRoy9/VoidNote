package com.greenicephoenix.voidnote.security

import android.content.Context
// Import alias resolves the name collision between:
//   androidx.biometric.BiometricManager  (our library)
//   android.hardware.biometrics.BiometricManager  (built into Android OS)
// Without the alias Kotlin sees both and throws "Conflicting import: ambiguous".
// The alias tells Kotlin: "whenever I write BiometricManager, I mean the AndroidX one."
import androidx.biometric.BiometricManager as BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BiometricLockManager — Single source of truth for biometric auth.
 *
 * WHAT IS BIOMETRIC AUTH?
 * Android's BiometricPrompt API shows the native system dialog for fingerprint,
 * face recognition, or PIN/pattern fallback. We never touch the biometric data
 * directly — Android handles it securely in the TEE (Trusted Execution Environment).
 *
 * WHY A SEPARATE MANAGER CLASS?
 * - Keeps biometric logic out of ViewModels and Activities
 * - Single place to check availability, show prompt, and handle result
 * - Easily testable via constructor injection
 *
 * AUTHENTICATOR TYPES:
 * BIOMETRIC_STRONG = fingerprint, 3D face (highest security class)
 * DEVICE_CREDENTIAL = PIN, pattern, password (fallback for devices without biometrics)
 * We allow both via BIOMETRIC_STRONG or DEVICE_CREDENTIAL — the user gets the most
 * secure option their device supports, with PIN/pattern as a guaranteed fallback.
 *
 * HILT:
 * @Singleton ensures one instance is shared across the app.
 * @ApplicationContext gives us a context that lives as long as the app — safe for
 * BiometricManager checks (which don't need an Activity).
 */
@Singleton
class BiometricLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val biometricManager = BiometricManager.from(context)

    /**
     * Represents the current availability of biometric authentication.
     *
     * WHY A SEALED CLASS?
     * A Boolean "isAvailable" loses information — we'd need another field to
     * explain WHY it's unavailable. A sealed class carries the reason.
     */
    sealed class Availability {
        object Ready : Availability()           // Biometrics enrolled and ready
        object NoneEnrolled : Availability()    // Hardware exists but nothing enrolled
        object NoHardware : Availability()      // Device has no biometric sensor
        object Unavailable : Availability()     // Temporarily unavailable (lock-out, etc.)
    }

    /**
     * Check if the device can perform biometric authentication.
     *
     * Call this before showing the biometric toggle in Settings to avoid offering
     * a feature that won't work. Also call before showing the lock screen.
     *
     * AUTHENTICATORS:
     * We use BIOMETRIC_STRONG or DEVICE_CREDENTIAL so:
     * - Phones WITH fingerprint → show fingerprint prompt
     * - Phones WITHOUT fingerprint → fall back to PIN/pattern
     * This means biometric lock works on ALL Android devices that have a screen lock.
     */
    fun checkAvailability(): Availability {
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS          -> Availability.Ready
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE   -> Availability.NoHardware
            else                                           -> Availability.Unavailable
        }
    }

    /**
     * Returns true if biometric/device-credential auth is available and usable.
     * Convenience function for simple boolean checks.
     */
    fun isAvailable(): Boolean = checkAvailability() == Availability.Ready

    /**
     * Show the biometric authentication prompt.
     *
     * WHY DOES THIS NEED A FRAGMENTACTIVITY?
     * BiometricPrompt attaches itself to a FragmentActivity's lifecycle so it can:
     * - Show system UI fragments (the bottom sheet dialog)
     * - Survive configuration changes (screen rotation)
     * - Be automatically dismissed when the activity is destroyed
     *
     * We pass the activity from the call site (MainActivity) rather than storing it,
     * which would create a memory leak.
     *
     * @param activity    The host Activity (must be a FragmentActivity — AppCompatActivity works)
     * @param onSuccess   Called when authentication succeeds — unlock the app
     * @param onFailed    Called when auth fails (wrong finger, face not recognised)
     * @param onError     Called for system errors (lock-out, cancellation, hardware error)
     */
    fun showPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Called when biometric doesn't match — NOT a terminal error.
                // The system prompt stays open and the user can try again.
                // We call onFailed() for the ViewModel to react (e.g. show a message).
                onFailed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Terminal errors: too many attempts, cancelled, hardware failure.
                // The prompt is dismissed. We pass the error message for the ViewModel.
                onError(errString.toString())
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Void Note")
            .setSubtitle("Authenticate to access your notes")
            .setDescription("Your notes are private and secured.")
            // Allow BOTH biometrics AND device credential (PIN/pattern/password).
            // setAllowedAuthenticators with DEVICE_CREDENTIAL removes the need for
            // setNegativeButtonText() — the system provides "Use PIN" automatically.
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(promptInfo)
    }
}