// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/BookRendering.kt
// Purpose: Builds, outside the playback service, the renderer playback would use for one book (ADR-039).

package io.github.skm0078.audora.playback.studio

import android.content.Context
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.reading.UnitReading
import io.github.skm0078.audora.playback.session.SentenceTimings
import io.github.skm0078.audora.playback.voice.EngineRenderer
import io.github.skm0078.audora.playback.voice.ReadingSetup
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import io.github.skm0078.audora.speech.punct.PunctuationModels
import io.github.skm0078.audora.speech.punct.RunOnPunctuation
import kotlinx.coroutines.flow.first

/**
 * What playback applies when it opens a book, read once: the listener's style with the book's own speed, pace and
 * voices on top, its pronunciation fixes, the reading modes and the chapter cues. Studio mode renders with it so
 * playback finds the audio under the same [key]; clips render with it so they sound like listening (and use studio
 * audio when it is ready).
 *
 * @property live the voice itself, exactly as playback's `EngineRenderer` is built (without the speed tracker).
 * @property reading which units playback plays.
 */
class BookRendering private constructor(
    val key: StudioKey,
    val style: SpeechStyle,
    val live: EngineRenderer,
    val reading: UnitReading,
    private val timings: SentenceTimings,
) {
    /** [live] with each sentence's voice placement, which studio mode stores for the word highlight. */
    fun studioVoice(): StudioVoice = StudioVoice { unit ->
        when (val rendered = live.render(unit)) {
            is Outcome.Success -> {
                val audio = rendered.value
                val timing = timings.of(UnitKey.of(unit))
                Outcome.Success(
                    StudioVoiced(audio, timing?.leadMs ?: 0L, timing?.speechMs ?: audio.durationMs),
                )
            }

            is Outcome.Failure -> Outcome.Failure(rendered.error.message)
        }
    }

    /** Builds [BookRendering]s from the stores playback reads a book's own settings and fixes from. */
    class Factory(
        private val context: Context,
        private val settings: BookSettingsStore,
        private val pronunciations: PronunciationStore,
    ) {
        /**
         * Sets [voices]' book voice. Reads the book's settings and fixes and the outline: call off the main thread.
         * @param normalRate read at the voice's own rate instead of the listener's speed, for files that go to an app
         *   with its own speed control (the talking book), as the audiobook export does.
         */
        suspend fun prepare(
            id: BookId,
            text: BookText,
            voices: SpeechVoices,
            normalRate: Boolean = false,
        ): BookRendering {
            val own = settings.get(id)
            val fixer = PronunciationFixer(pronunciations.observe(id).first())
            voices.bookVoice = { lang -> own?.voices?.get(lang) }
            val styleStore = SpeechStyleStore.forContext(context)
            val global = styleStore.current
            val heard = own?.over(global) ?: global
            val style = if (normalRate) heard.copy(rate = SpeechStyle.NORMAL_RATE) else heard
            val chapterPages = text.chapterStarts().toSet()
            val timings = SentenceTimings()
            // As AudoraPlaybackService builds it, so studio audio is the audio playback would render.
            val live = EngineRenderer(
                voices,
                style = { style },
                styleConfig = styleStore.config,
                reading = ReadingSetup(
                    chapterPages = { chapterPages },
                    fixer = { fixer },
                    scanned = text::isScanned,
                    runOn = RunOnPunctuation(punctuator = PunctuationModels.get(context)::punctuator),
                ),
                timings = timings,
            )
            val key = StudioKey(id, StudioMode.signature(context, style, voices::choiceFor, fixer.signature))
            return BookRendering(key, style, live, UnitReading(style.modes, chapterPages = chapterPages), timings)
        }
    }
}
