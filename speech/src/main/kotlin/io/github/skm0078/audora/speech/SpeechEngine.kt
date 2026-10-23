// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/SpeechEngine.kt
// Purpose: Single contract every TTS backend implements; playback never knows which engine it uses (Build Spec §4).

package io.github.skm0078.audora.speech

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.audio.PcmAudio

/** One rendered sentence, or why it could not be rendered. */
typealias RenderResult = Outcome<PcmAudio, SpeechError>

/**
 * A text-to-speech backend.
 *
 * Contract for every implementation:
 * - [render] is main-safe: the engine moves the work to its own thread.
 * - Renders are serialized per engine; a second caller waits for the first.
 * - Cancelling the calling coroutine cancels the render: [render] throws `CancellationException` instead of returning,
 *   and the engine stays usable. How quickly the work stops depends on the engine (see each implementation).
 * - Expected failures (missing voice, engine not installed, synthesis error) come back as [Outcome.Failure];
 *   exceptions other than cancellation never escape.
 * - [render]'s rate is applied by the voice itself (the model speaks faster or slower), never by stretching the
 *   audio afterwards, which smears words (ADR-033, replacing Build Spec C6's player-side speed).
 */
interface SpeechEngine {
    val id: EngineId

    /** Whether this engine can speak [lang] at all. A `true` does not promise an installed voice; see [voices]. */
    fun supports(lang: Lang): Boolean

    /** Voices that can render right now, offline. */
    suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError>

    /**
     * Renders [text] (one sentence-sized reading unit) with [voice] to 16-bit mono PCM, spoken at [rate] (1.0 = the
     * voice's own speed). Blank text renders silence.
     */
    suspend fun render(text: String, voice: VoiceId, rate: Float = SpeechStyle.NORMAL_RATE): RenderResult

    /**
     * [render] for a sentence that asks for a [feeling] (ADR-056). A voice with speaking styles reads it in the style
     * that fits (ADR-057); every other voice ignores it, which is this default.
     */
    suspend fun render(text: String, voice: VoiceId, rate: Float, feeling: Feeling): RenderResult =
        render(text, voice, rate)

    /** Whether [voice] reads a feeling in a style of its own (ADR-057), so the caller can choose which words get it. */
    fun actsFeelings(voice: VoiceId): Boolean = false

    /**
     * Whether [voice] performs a sentence as a whole (ADR-059): its language then gets no dialogue, heading or
     * character voices, and its sentences are never split at quotes.
     */
    fun keepsSentencesWhole(voice: VoiceId): Boolean = false

    /** Frees native models and engine connections. Later renders fail with [SpeechError.EngineUnavailable]. */
    fun release()
}

/**
 * A voice the user can pick.
 *
 * @property licence short licence note for the "Voices & licences" screen (P3-06); null when the phone's engine
 *   owns it.
 */
data class VoiceInfo(val id: VoiceId, val lang: Lang, val label: String, val licence: String?)
