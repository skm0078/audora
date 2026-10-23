// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/BookRendering.kt
// Purpose: Builds, outside the playback service, the renderer playback would use for one book (ADR-039).

package io.github.skm0078.audora.playback.studio

import android.content.Context
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.reading.BookIntent
import io.github.skm0078.audora.playback.reading.QuoteCarry
import io.github.skm0078.audora.playback.reading.UnitReading
import io.github.skm0078.audora.playback.session.SentenceTimings
import io.github.skm0078.audora.playback.voice.BookRenderers
import io.github.skm0078.audora.playback.voice.EngineRenderer
import io.github.skm0078.audora.playback.voice.RenderBook
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import io.github.skm0078.audora.speech.route.CharacterVoices
import kotlinx.coroutines.flow.first

/**
 * What playback applies when it opens a book, read once: the listener's style with the book's own speed, pace and
 * voices on top, its pronunciation fixes, the reading modes and the chapter cues. Studio mode renders with it so
 * playback finds the audio under the same [key]; clips render with it so they sound like listening (and use studio
 * audio when it is ready).
 *
 * @property live the voice itself, exactly as playback's `EngineRenderer` is built (without the speed tracker).
 * @property reading which units playback plays.
 * @property quotes which sentences start inside a quote and who speaks them; feed it each page before rendering it
 *   (as AudiobookExporter's onPage does), or quotes keep the narrator's voice.
 * @property intent what the book is (ADR-056), told from the pages fed the same way; [page] feeds both.
 */
@Suppress("LongParameterList") // What a run renders with: key, style, voice, modes, the two page trackers, timings.
class BookRendering private constructor(
    val key: StudioKey,
    val style: SpeechStyle,
    val live: EngineRenderer,
    val reading: UnitReading,
    val quotes: QuoteCarry,
    val intent: BookIntent,
    private val timings: SentenceTimings,
) {
    /** One page, before any of its units renders: to the quote tracking and the book's intent, as in playback. */
    fun page(units: List<ReadingUnit>, continuesPreviousPage: Boolean) {
        quotes.page(units, continuesPreviousPage)
        intent.page(units)
    }

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
        /** Sets [voices]' book voice. Reads the book's settings and fixes and the outline: call off the main thread. */
        suspend fun prepare(
            id: BookId,
            text: BookText,
            voices: SpeechVoices,
            run: RunSettings = RunSettings(),
        ): BookRendering {
            val (normalRate, global, characters) = run
            val own = settings.get(id)
            val fixer = PronunciationFixer(pronunciations.observe(id).first())
            voices.bookVoice = BookSound.voices(own)
            val styleStore = SpeechStyleStore.forContext(context)
            val style = BookSound.style(own, global ?: styleStore.current, normalRate)
            val chapterPages = text.chapterStarts().toSet()
            val timings = SentenceTimings()
            val quotes = QuoteCarry()
            val intent = BookIntent()
            // BookRenderers builds playback's renderer too, so studio audio is the audio playback would render.
            val live = BookRenderers.create(
                context,
                voices,
                style = { style },
                styleConfig = styleStore.config,
                book = RenderBook(
                    chapterPages = { chapterPages },
                    scanned = text::isScanned,
                    fixer = { fixer },
                    startsInQuote = quotes::startsInQuote,
                    speakerOf = quotes::speakerOf,
                    characters = characters,
                    intent = { intent.current },
                ),
                timings = timings,
            )
            // One read of the explicit choices serves the key and the run: these voices answer with it from now on, so
            // a voice picked in Settings meanwhile never changes the audio under the key. A language on its default
            // binds it on its first sentence, so an English-only book never binds the phone's TTS.
            val chosen = voices.fixChoices()
            val key = StudioKey(id, StudioMode.signature(context, style, chosen::get, fixer.signature))
            // English's default (the bundled voice) binds no phone TTS: know it now, as the pin did, so the run's
            // first sentence already has its character voices.
            if (chosen[Lang.EN] == null) voices.voiceFor(Lang.EN)
            val reading = UnitReading(style.modes, chapterPages = chapterPages)
            return BookRendering(key, style, live, reading, quotes, intent, timings)
        }
    }
}

/**
 * How a whole-book run reads, beyond the book's own settings.
 * @property normalRate read at the voice's own rate instead of the listener's speed, for files that go to an app with
 *   its own speed control (the talking book), as the audiobook export does.
 * @property global the listener's style as the caller read it, so a run whose voices were built from one read of the
 *   style file renders and keys with that same read; null reads it now.
 * @property characters the book's character voices (plan item 63), as playback casts them; null reads every character
 *   in the dialogue voice.
 */
data class RunSettings(
    val normalRate: Boolean = false,
    val global: SpeechStyle? = null,
    val characters: CharacterVoices? = null,
)
