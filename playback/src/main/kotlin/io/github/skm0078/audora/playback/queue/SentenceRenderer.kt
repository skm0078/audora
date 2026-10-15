// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/queue/SentenceRenderer.kt
// Purpose: What the render queue needs from speech: one sentence in, PCM or a typed failure out, blocking.

package io.github.skm0078.audora.playback.queue

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio

/** A sentence's place in the book. Stable across voice changes and playlist rebuilds. */
data class UnitKey(val page: Int, val index: Int) {
    override fun toString(): String = "p${page}u$index"

    companion object {
        fun of(unit: ReadingUnit): UnitKey = UnitKey(unit.page, unit.index)
    }
}

/**
 * Blocking: called on the render queue's own worker thread. Throws [InterruptedException] when the thread is
 * interrupted (the sentence left the render window, or the queue closed); every other failure is a
 * [Outcome.Failure]. The adapter over `SpeechEngine` is [io.github.skm0078.audora.playback.voice.EngineRenderer].
 */
fun interface SentenceRenderer {
    fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError>
}
