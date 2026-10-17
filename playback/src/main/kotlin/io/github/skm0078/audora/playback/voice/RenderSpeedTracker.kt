// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/RenderSpeedTracker.kt
// Purpose: Measures each voice's render speed from the sentences playback renders anyway (P4-02, ADR-027).

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.route.RoutedAudio
import io.github.skm0078.audora.speech.voices.MeasuredSpeeds
import io.github.skm0078.audora.speech.voices.SpeedSample
import java.util.concurrent.TimeUnit

/**
 * No benchmark run: every sentence the render queue finishes is a measurement. A sentence counts only when one voice
 * read all of it and the sentence before it ended with that same voice, so the time never includes loading a model
 * (the first sentence of a session, or a switch between voices: the sherpa engine keeps one model loaded) and a mixed
 * sentence is never charged to one voice. Called on the render worker thread only.
 *
 * @param speeds where measurements go; resolved on the render thread, as the store reads its file on first use.
 */
class RenderSpeedTracker(private val speeds: () -> MeasuredSpeeds) {
    @Volatile
    private var lastVoice: VoiceId? = null

    /**
     * [rate] is the speaking rate the sentence was rendered at: the sample is scaled back to the voice's own speed
     * (rate 1.0), so changing the rate does not skew the voice's measured speed (ADR-033).
     */
    fun onRendered(chars: Int, routed: RoutedAudio, renderNanos: Long, rate: Float = SpeechStyle.NORMAL_RATE) {
        val voice = routed.voices.distinct().singleOrNull()?.takeIf { it == lastVoice }
        lastVoice = routed.voices.lastOrNull()
        val audioMs = routed.audio.durationMs - routed.pausesMs // the voice's audio, not the clause pauses
        val measurable = chars > 0 && audioMs > 0 && renderNanos >= 0 && rate > 0f && rate.isFinite()
        if (voice == null || !measurable) return
        speeds().record(
            voice,
            SpeedSample(
                chars = chars,
                renderSeconds = renderNanos.toDouble() / TimeUnit.SECONDS.toNanos(1),
                audioSeconds = audioMs.toDouble() * rate / TimeUnit.SECONDS.toMillis(1),
            ),
        )
    }
}
