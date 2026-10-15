// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaSynthesizer.kt
// Purpose: The seam between SherpaSpeechEngine's logic (JVM-tested) and sherpa-onnx's native OfflineTts.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError

/** One loaded voice model. Not thread-safe: the engine calls it from one thread at a time. */
interface SherpaSynthesizer {
    val sampleRate: Int

    /**
     * Renders [text] and returns float samples in [-1, 1]. Blocking.
     *
     * [keepGoing] is polled each time sherpa-onnx finishes one of its internal sentences; returning false stops the
     * render early and the partial result is discarded by the caller.
     */
    fun generate(text: String, speakerId: Int, keepGoing: () -> Boolean): FloatArray

    fun release()
}

/** Loads a voice's model. Blocking (a Piper model takes ~1.5 s); the engine calls it on its own thread. */
fun interface SherpaSynthesizerFactory {
    fun load(spec: SherpaVoiceSpec): Outcome<SherpaSynthesizer, SpeechError>
}
