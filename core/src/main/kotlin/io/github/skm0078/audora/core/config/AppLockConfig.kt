// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/AppLockConfig.kt
// Purpose: Tunables for the optional app lock (ADR-061).

package io.github.skm0078.audora.core.config

/**
 * @property lockAfterAwayMs once the app has been out of sight this long, it asks to be unlocked again. Long enough
 *   that a trip to the file picker, the camera app or a share sheet does not lock it; short enough that a phone left
 *   unlocked on a table does not leave the library open. Settings says "30 seconds" (strings_privacy.xml): change both.
 */
data class AppLockConfig(val lockAfterAwayMs: Long = 30_000L) {
    init {
        require(lockAfterAwayMs >= 0) { "lockAfterAwayMs must be >= 0, was $lockAfterAwayMs" }
    }
}
