// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SpeechConfig.kt
// Purpose: Tunables for the speech engines (P1-03): neural threads and loaded models, system TTS timeouts.

package io.github.skm0078.audora.core.config

/**
 * @property sherpaThreads ONNX Runtime threads per sherpa-onnx voice. 2 is what S1 measured and what ADR-012's
 *   Kitten rule is stated in; more threads leave less CPU for the player, UI and PDF pipeline.
 * @property maxLoadedVoices sherpa-onnx models kept loaded at once, least recently used dropped first (P4-02). A
 *   premium model is ~150 MB on disk and more in RAM once ONNX Runtime has its arenas, so each extra slot costs
 *   that much memory; two covers a bilingual book (English + Hindi with both premium voices) without reloading a
 *   model at every language switch. The service drops all but the voice in use when Android asks it to trim.
 * @property systemConnectTimeoutMs how long to wait for the phone's TTS engine to bind and initialise.
 * @property systemChunkTimeoutMs how long one `synthesizeToFile` call may take before it counts as hung. Generous:
 *   a chunk can be up to `getMaxSpeechInputLength()` (4000) characters on a slow engine.
 */
data class SpeechConfig(
    val sherpaThreads: Int = 2,
    val maxLoadedVoices: Int = 2,
    val systemConnectTimeoutMs: Long = 15_000,
    val systemChunkTimeoutMs: Long = 120_000,
) {
    init {
        require(sherpaThreads > 0) { "sherpaThreads must be > 0, was $sherpaThreads" }
        require(maxLoadedVoices >= 1) { "maxLoadedVoices must be >= 1, was $maxLoadedVoices" }
        require(systemConnectTimeoutMs > 0) { "systemConnectTimeoutMs must be > 0, was $systemConnectTimeoutMs" }
        require(systemChunkTimeoutMs > 0) { "systemChunkTimeoutMs must be > 0, was $systemChunkTimeoutMs" }
    }
}
