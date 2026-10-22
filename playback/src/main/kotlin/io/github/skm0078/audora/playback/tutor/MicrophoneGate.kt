// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/tutor/MicrophoneGate.kt
// Purpose: The reading tutor and voice notes pause app basics' voice commands while they record.

package io.github.skm0078.audora.playback.tutor

import io.github.skm0078.audora.speech.kws.VoiceCommandPause

/**
 * Takes the microphone from voice commands until the returned handle is closed: [VoiceCommandPause] pauses them
 * meanwhile. The tutor and voice notes are kept apart by their modal dialogs, not by this
 * gate. The reason names a feature, never content.
 * Callers take the hold inside `withContext(NonCancellable)` and close it in `use {}` or a `finally`.
 */
fun interface MicrophoneGate {
    suspend fun hold(reason: String): AutoCloseable

    companion object {
        /** Nothing to pause, for tests and previews. */
        val NONE = MicrophoneGate { AutoCloseable {} }

        /** The app-wide gate: voice commands stop listening while the tutor or a voice note records. */
        val shared: MicrophoneGate = MicrophoneGate { VoiceCommandPause.shared.hold(it) }
    }
}
