// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/engine/BenchEngine.kt
// Purpose: The one contract the benchmark drives, so sherpa-onnx voices and the phone's system voices are
//          measured by the same code. A cut-down preview of :speech's SpeechEngine (Build Spec §4).

package io.github.skm0078.audora.spike.voices.engine

/** One rendered sentence as 16-bit mono PCM. */
class Rendered(val pcm: ShortArray, val sampleRate: Int) {
    val audioMs: Long get() = if (sampleRate > 0) pcm.size * MS_PER_SECOND / sampleRate else 0L

    private companion object {
        const val MS_PER_SECOND = 1_000L
    }
}

/** Typed engine failure: what broke and where, never a bare exception escaping into the UI. */
class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface BenchEngine {
    val voiceId: String
    val label: String
    val lang: String

    /** Threads the engine uses; null when the engine decides itself (system TTS). */
    val threads: Int?

    /** Extra facts for the report, e.g. the system voice's name and whether it needs the network. */
    val details: List<String>

    /** Loads the model. Called once, off the main thread, before [render]. */
    @Throws(EngineException::class)
    fun load()

    /** Renders [text] completely and returns the audio. Blocking; never on the main thread. */
    @Throws(EngineException::class)
    fun render(text: String): Rendered

    fun release()
}
