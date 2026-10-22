// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/StudyGapRenderer.kt
// Purpose: "Leave a gap to say it back" (plan item 31): silence after each sentence, about as long as the sentence.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.StudyConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio

/**
 * Wraps live playback's renderer only (an exported audiobook never gets the gaps). The gap is part of the sentence's
 * audio, so it plays with the screen off, needs no timer and survives a repeat; [gaps] is read per sentence, like the
 * rest of the style, and a change restarts the sentence playing.
 */
class StudyGapRenderer(
    private val inner: SentenceRenderer,
    private val gaps: () -> Boolean,
    private val config: StudyConfig = StudyConfig(),
) : SentenceRenderer {
    override fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError> {
        val rendered = inner.render(unit)
        return if (gaps()) rendered.map { it.withSilenceAfter(config.gapMs(it.durationMs)) } else rendered
    }
}
