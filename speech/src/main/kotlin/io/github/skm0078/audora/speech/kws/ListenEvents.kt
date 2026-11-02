// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/ListenEvents.kt
// Purpose: What a voice-command listener reports while it listens, and why it stops (plan item 38).

package io.github.skm0078.audora.speech.kws

/** Why listening could not start or had to stop; each is a state the reader's line or Settings can explain. */
enum class ListenFailure {
    /** The microphone permission is missing or was taken back. */
    NO_PERMISSION,

    /** This phone has no on-device speech recogniser (no Google speech services, for one). */
    NOT_AVAILABLE,

    /** The recogniser's English speech pack is not on the phone yet; Android has been asked to fetch it. */
    LANGUAGE_MISSING,

    /** Another app holds the microphone. */
    MICROPHONE_BUSY,

    /** The recogniser kept failing, so listening stopped instead of retrying for ever. */
    MICROPHONE_LOST,
}

/**
 * Where a listener reports: each command acted on, Android silencing or un-silencing the capture (when the listener
 * can tell; the system recogniser cannot), and listening really starting and stopping ([onMicrophone]), so the reader
 * can say "Listening" only while it truly listens. Called on the main thread.
 */
class ListenEvents(
    val onSilenced: (Boolean) -> Unit = {},
    val onMicrophone: (Boolean) -> Unit = {},
    val onCommand: (VoiceCommand) -> Unit,
)
