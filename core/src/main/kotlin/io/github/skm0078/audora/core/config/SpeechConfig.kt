// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SpeechConfig.kt
// Purpose: Tunables for the speech engines (P1-03): neural thread count and system TTS timeouts.

package io.github.skm0078.audora.core.config

/**
 * @property sherpaThreads ONNX Runtime threads per sherpa-onnx voice. 2 is what S1 measured and what ADR-012's
 *   Kitten rule is stated in; more threads leave less CPU for the player, UI and PDF pipeline.
 * @property systemConnectTimeoutMs how long to wait for the phone's TTS engine to bind and initialise.
 * @property systemChunkTimeoutMs how long one `synthesizeToFile` call may take before it counts as hung. Generous:
 *   a chunk can be up to `getMaxSpeechInputLength()` (4000) characters on a slow engine.
 */
data class SpeechConfig(
    val sherpaThreads: Int = 2,
    val systemConnectTimeoutMs: Long = 15_000,
    val systemChunkTimeoutMs: Long = 120_000,
) {
    init {
        require(sherpaThreads > 0) { "sherpaThreads must be > 0, was $sherpaThreads" }
        require(systemConnectTimeoutMs > 0) { "systemConnectTimeoutMs must be > 0, was $systemConnectTimeoutMs" }
        require(systemChunkTimeoutMs > 0) { "systemChunkTimeoutMs must be > 0, was $systemChunkTimeoutMs" }
    }
}
