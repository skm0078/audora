// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/Voice.kt
// Purpose: Names a voice independently of the engine that renders it, so resume anchors and settings can store it.

package io.github.skm0078.audora.core.model

/** The speech backend a voice belongs to (Build Spec §3). */
enum class EngineId {
    /** Neural voices run on the phone by sherpa-onnx: the bundled English voice and, later, downloaded ones. */
    SHERPA,

    /** The phone's own text-to-speech engine (usually Google), through Android's `TextToSpeech`. */
    SYSTEM,

    /** The acted Hindi voice (Chatterbox, ADR-059): too slow to read live, so only studio mode renders with it. */
    ACTED,
}

/**
 * One voice of one engine.
 *
 * @property name the engine's own stable name: the catalog id for sherpa voices (`piper-en-ljspeech`), the
 *   platform voice name for system voices (`hi-in-x-hia-local`).
 */
data class VoiceId(val engine: EngineId, val name: String) {
    init {
        require(name.isNotBlank()) { "Voice name must not be blank" }
    }

    override fun toString(): String = "${engine.name.lowercase()}:$name"
}
