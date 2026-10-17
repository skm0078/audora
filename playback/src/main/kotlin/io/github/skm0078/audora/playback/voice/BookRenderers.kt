// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/BookRenderers.kt
// Purpose: The one place a book's sentence renderer is built, so playback, audiobook export and studio sound alike.

package io.github.skm0078.audora.playback.voice

import android.content.Context
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.playback.session.SentenceTimings
import io.github.skm0078.audora.speech.punct.PunctuationModels
import io.github.skm0078.audora.speech.punct.RunOnPunctuation
import io.github.skm0078.audora.speech.route.VoiceSource

/**
 * Every renderer built here gets the same reading setup (reading modes, cues, pronunciation fixes, run-on punctuation,
 * cast voices) and the listener's live [style], which carries the voice shaping; a new renderer feature is wired
 * once, here, rather than in each service. [speed] and [timings] are what only live playback needs.
 */
object BookRenderers {
    @Suppress("LongParameterList") // The voices, the style, the book; the rest optional.
    fun create(
        context: Context,
        voices: VoiceSource,
        style: () -> SpeechStyle,
        styleConfig: SpeechStyleConfig,
        book: RenderBook,
        speakability: SpeakabilityConfig = SpeakabilityConfig(),
        speed: RenderSpeedTracker? = null,
        timings: SentenceTimings? = null,
    ): EngineRenderer = EngineRenderer(
        voices,
        speed = speed,
        style = style,
        styleConfig = styleConfig,
        reading = ReadingSetup(
            speakability,
            chapterPages = book.chapterPages,
            fixer = book.fixer,
            scanned = book.scanned,
            runOn = RunOnPunctuation(punctuator = PunctuationModels.get(context)::punctuator),
            startsInQuote = book.startsInQuote,
            speakerOf = book.speakerOf,
            characters = book.characters,
        ),
        timings = timings,
    )
}
