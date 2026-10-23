// File: app/src/main/kotlin/io/github/skm0078/audora/privacy/DeviceUnlock.kt
// Purpose: Asks for the phone's own fingerprint, face, PIN, pattern or password (ADR-061), through Android's prompt.

package io.github.skm0078.audora.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog

/**
 * The framework's BiometricPrompt (API 30+, so no androidx.biometric dependency). ListenLock keeps no PIN of its own:
 * proving it is you is Android's job, and nothing about the proof reaches the app beyond "it worked".
 * Weak biometrics are allowed because nothing is decrypted with the result; it only opens the screen.
 */
class DeviceUnlock(private val activity: Activity) {
    /** False when the phone has no screen lock at all, so there is nothing to ask for. */
    fun hasScreenLock(): Boolean = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** [onResult] runs on the main thread: true once the person proved it is them, false on cancel or error. */
    fun ask(onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.app_lock_prompt_title))
            .setSubtitle(activity.getString(R.string.app_lock_prompt_subtitle))
            .setAllowedAuthenticators(ALLOWED)
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // A cancel is the person's choice; anything else (lockout, no hardware) is worth a line in the log.
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                ) {
                    AudoraLog.w(TAG, "Unlock prompt ended with error $errorCode")
                }
                onResult(false)
            }
        }
        try {
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, callback)
        } catch (e: IllegalArgumentException) {
            AudoraLog.e(TAG, "Unlock prompt refused its settings", e)
            onResult(false)
        }
    }

    private companion object {
        const val TAG = "Audora.AppLock"
        const val ALLOWED = Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
    }
}
