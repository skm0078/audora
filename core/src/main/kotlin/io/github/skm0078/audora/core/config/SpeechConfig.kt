// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SpeechConfig.kt
// Purpose: Tunables for the speech engines (P1-03): neural threads and loaded models, system TTS timeouts, paces.

package io.github.skm0078.audora.core.config

/**
 * @property sherpaThreads ONNX Runtime threads per sherpa-onnx voice. 2 is what S1 measured and what ADR-012's
 *   Kitten rule is stated in; more threads leave less CPU for the player, UI and PDF pipeline.
 * @property maxLoadedVoices sherpa-onnx models kept loaded at once, least recently used dropped first (P4-02). A
 *   premium model is ~150 MB on disk and more in RAM once ONNX Runtime has its arenas, so each extra slot costs
 *   that much memory, but only once a third voice is actually used; three covers a bilingual book (English + Hindi
 *   with both premium voices) or one read by a narrator, a voice for quotes and one for headings (plan item 7)
 *   without reloading a model at every switch. The service drops all but the voice in use when Android asks it to
 *   trim.
 * @property systemConnectTimeoutMs how long to wait for the phone's TTS engine to bind and initialise.
 * @property systemChunkTimeoutMs how long one `synthesizeToFile` call may take before it counts as hung. Generous:
 *   a chunk can be up to `getMaxSpeechInputLength()` (4000) characters on a slow engine.
 * @property blendHeadroomBytes free space a narrator blend's copy (plan item 75, ~28 MB) must leave on top of its
 *   own size, so the copy never fills the phone.
 * @property bengaliPace how fast the Bengali voices that read letters (SYSPIN, Rasa) speak at the listener's 1x,
 *   against their own speed: both were recorded faster than a story is told, and a native listener found 0.9 natural
 *   where 1.0 was "too fast" (ADR 2026-10-30-bengali-spoken-spelling). Every speed the listener picks scales it.
 */
data class SpeechConfig(
    val sherpaThreads: Int = 2,
    val maxLoadedVoices: Int = 3,
    val systemConnectTimeoutMs: Long = 15_000,
    val systemChunkTimeoutMs: Long = 120_000,
    val blendHeadroomBytes: Long = 67_108_864, // 64 MB
    val bengaliPace: Float = 0.9f,
) {
    init {
        require(sherpaThreads > 0) { "sherpaThreads must be > 0, was $sherpaThreads" }
        require(maxLoadedVoices >= 1) { "maxLoadedVoices must be >= 1, was $maxLoadedVoices" }
        require(systemConnectTimeoutMs > 0) { "systemConnectTimeoutMs must be > 0, was $systemConnectTimeoutMs" }
        require(systemChunkTimeoutMs > 0) { "systemChunkTimeoutMs must be > 0, was $systemChunkTimeoutMs" }
        require(blendHeadroomBytes >= 0) { "blendHeadroomBytes must be >= 0, was $blendHeadroomBytes" }
        require(bengaliPace > 0f && bengaliPace.isFinite()) { "bengaliPace must be > 0, was $bengaliPace" }
    }
}
