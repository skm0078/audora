// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/NarratorEcho.kt
// Purpose: Keeps the narrator from giving voice commands: none act while the book itself says "Audora" (plan item 38).

package io.github.skm0078.audora.speech.kws

import io.github.skm0078.audora.core.config.VoiceCommandConfig

/**
 * The spotter hears the speaker too. The echo canceller removes most of the book's voice, and every command starts
 * with "Audora", which books almost never say; this covers the book that does. The playback service reports each
 * sentence as it starts ([sentenceStarted]) and when reading stops ([stopped]); while the sentence playing names the
 * app, and for [holdMs] after it (the spotter reports a phrase a moment after it ends), commands are ignored.
 *
 * Process-wide ([shared]): the service and the listener live in one process. Any thread; only flags and times are
 * kept, never the text.
 */
class NarratorEcho(private val holdMs: Long) {
    @Volatile
    private var mentionsNow = false

    @Volatile
    private var mentionEndedAtMs = Long.MIN_VALUE / 2

    init {
        require(holdMs >= 0) { "holdMs must be >= 0, was $holdMs" }
    }

    fun sentenceStarted(text: String, nowMs: Long) {
        endMention(nowMs)
        mentionsNow = mentionsWakeWord(text)
    }

    /** Paused, stopped or finished: nothing is being said, so "Audora, play" works at once after the hold. */
    fun stopped(nowMs: Long) = endMention(nowMs)

    /** True while a command heard at [nowMs] may be the narrator's own voice. */
    fun suppresses(nowMs: Long): Boolean = mentionsNow || nowMs - mentionEndedAtMs < holdMs

    private fun endMention(nowMs: Long) {
        if (mentionsNow) mentionEndedAtMs = nowMs
        mentionsNow = false
    }

    companion object {
        /** The app's name as a book could print it: English, and as Hindi and Bengali would spell it. */
        private val WAKE_WORDS = listOf("audora", "ऑडोरा", "অডোরা")

        val shared = NarratorEcho(VoiceCommandConfig().narratorHoldMs)

        fun mentionsWakeWord(text: String): Boolean = WAKE_WORDS.any { text.contains(it, ignoreCase = true) }
    }
}
