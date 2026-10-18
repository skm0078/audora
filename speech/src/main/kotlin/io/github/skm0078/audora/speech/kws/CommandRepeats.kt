// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/CommandRepeats.kt
// Purpose: Drops a voice command heard again within the repeat gap, so one utterance acts once (plan item 38).

package io.github.skm0078.audora.speech.kws

/**
 * The spotter can report one phrase twice when its tail is decoded again after a reset, and an echo of the user's
 * voice off a wall can be heard a moment later. The same command inside [gapMs] of the last one acted on is that,
 * not a second ask; a different command always acts. Not thread-safe: the listener's one thread owns it.
 */
class CommandRepeats(private val gapMs: Long) {
    private var last: VoiceCommand? = null
    private var lastAtMs = 0L

    init {
        require(gapMs >= 0) { "gapMs must be >= 0, was $gapMs" }
    }

    /** True when [command], heard at [nowMs] (a monotonic clock), should act. */
    fun accept(command: VoiceCommand, nowMs: Long): Boolean {
        val repeat = command == last && nowMs - lastAtMs < gapMs
        if (!repeat) {
            last = command
            lastAtMs = nowMs
        }
        return !repeat
    }
}
