// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/tutor/MicrophoneGate.kt
// Purpose: One feature holds the microphone at a time: the reading tutor and voice notes pause voice commands.

package io.github.skm0078.audora.playback.tutor

/**
 * Takes the microphone from other features of the app until the returned handle is closed: app basics' voice
 * commands pause meanwhile (VoiceCommandPause, wired at landing). The reason names a feature, never content.
 * Callers take the hold inside `withContext(NonCancellable)` and close it in `use {}` or a `finally`.
 */
fun interface MicrophoneGate {
    suspend fun hold(reason: String): AutoCloseable

    companion object {
        /** Nothing to pause: no other feature records on this branch. */
        val NONE = MicrophoneGate { AutoCloseable {} }

        /**
         * The app-wide gate. At landing this becomes
         * `MicrophoneGate { withContext(NonCancellable) { VoiceCommandPause.shared.hold(it) } }` (landing-notes.md).
         */
        val shared: MicrophoneGate = NONE
    }
}
