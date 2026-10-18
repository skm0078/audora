// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/tutor/MicrophoneGate.kt
// Purpose: The reading tutor and voice notes pause app basics' voice commands while they record.

package io.github.skm0078.audora.playback.tutor

/**
 * Takes the microphone from voice commands until the returned handle is closed: app basics' VoiceCommandPause pauses
 * them meanwhile (wired at landing). The tutor and voice notes are kept apart by their modal dialogs, not by this
 * gate. The reason names a feature, never content.
 * Callers take the hold inside `withContext(NonCancellable)` and close it in `use {}` or a `finally`.
 */
fun interface MicrophoneGate {
    suspend fun hold(reason: String): AutoCloseable

    companion object {
        /** Nothing to pause: no other feature records on this branch. */
        val NONE = MicrophoneGate { AutoCloseable {} }

        /**
         * The app-wide gate. At landing this becomes `MicrophoneGate { VoiceCommandPause.shared.hold(it) }` (one
         * import; callers already take it under NonCancellable; landing-notes.md).
         */
        val shared: MicrophoneGate = NONE
    }
}
