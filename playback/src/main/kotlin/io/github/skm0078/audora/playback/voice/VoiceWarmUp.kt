// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/VoiceWarmUp.kt
// Purpose: Loads the neural voice a book will be read with while the reader is open, so the first Play starts
//          without waiting for the model (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError

/**
 * Picks the voice the way reading will (the book's own voice while it is installed, else the language's voice), and
 * loads it when it is a sherpa-onnx model. Only those are worth it: loading one takes about a second and happens on
 * the first sentence otherwise. The phone's own voices bind while [voiceFor] finds them, which already warms them,
 * and the acted Hindi voice is a large opt-in model a listener may never press Play for, so it is left alone.
 *
 * Loading costs the model's memory (60 to 150 MB) a little earlier than Play would; the engine keeps at most
 * `SpeechConfig.maxLoadedVoices` and gives them back when Android asks, as for any voice that has read.
 *
 * @param bookVoice the book's own voice for a language, null when it has none (or it was deleted).
 * @param voiceFor the voice that reads a language in books without their own.
 * @param load makes the engine load [VoiceId]'s model without speaking.
 */
class VoiceWarmUp(
    private val bookVoice: suspend (BookId, Lang) -> VoiceId?,
    private val voiceFor: suspend (Lang) -> Outcome<VoiceId, SpeechError>,
    private val load: suspend (VoiceId) -> Outcome<Unit, SpeechError>,
) {
    /** The voice loaded for [book]'s [lang]; null when it needs no loading. A failure is logged, never thrown. */
    suspend fun warm(book: BookId, lang: Lang): VoiceId? {
        val voice = bookVoice(book, lang) ?: when (val found = voiceFor(lang)) {
            is Outcome.Success -> found.value

            is Outcome.Failure -> {
                logFailure(book, lang, found.error)
                return null
            }
        }
        if (voice.engine != EngineId.SHERPA) return null
        return when (val loaded = load(voice)) {
            is Outcome.Success -> {
                AudoraLog.i(TAG, "Warmed voice=$voice for book=${book.logLabel}")
                voice
            }

            is Outcome.Failure -> {
                logFailure(book, lang, loaded.error)
                null
            }
        }
    }

    private fun logFailure(book: BookId, lang: Lang, error: SpeechError) =
        AudoraLog.w(TAG, "Voice not warmed for book=${book.logLabel} lang=${lang.name}: ${error.message}")

    private companion object {
        const val TAG = "Playback.VoiceWarmUp"
    }
}
