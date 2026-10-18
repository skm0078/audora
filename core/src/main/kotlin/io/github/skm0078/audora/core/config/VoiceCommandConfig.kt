// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/VoiceCommandConfig.kt
// Purpose: Tunables for spoken commands ("Audora, pause") heard by the on-phone keyword spotter (plan item 38).

package io.github.skm0078.audora.core.config

/**
 * @property sampleRate what the microphone is read at; the keyword spotter's features are 16 kHz.
 * @property chunkMs audio handed to the spotter at a time: short enough to answer within a word, long enough that
 *   the loop's own cost stays small on a phone.
 * @property keywordsScore sherpa-onnx's boost for the command phrases during decoding; its default.
 * @property keywordsThreshold how sure the spotter must be before it reports a phrase. Higher misses more commands,
 *   lower acts on more words that only sound alike; the book's own voice through the speaker is the likely culprit,
 *   so this sits above sherpa-onnx's 0.25.
 * @property numThreads ONNX Runtime threads; one is plenty for a 3.3M-parameter model and leaves the rest to the
 *   voice.
 * @property repeatGapMs a phrase heard again within this time is the same utterance, not a second command.
 * @property narratorHoldMs commands stay ignored this long after the book itself said the app's name: past the
 *   spotter's lag after a phrase ends.
 */
data class VoiceCommandConfig(
    val sampleRate: Int = 16_000,
    val chunkMs: Int = 100,
    val keywordsScore: Float = 1.5f,
    val keywordsThreshold: Float = 0.3f,
    val numThreads: Int = 1,
    val repeatGapMs: Long = 1_500,
    val narratorHoldMs: Long = 2_000,
) {
    init {
        require(sampleRate > 0 && chunkMs > 0) { "sampleRate and chunkMs must be > 0" }
        require(keywordsThreshold > 0f && keywordsThreshold < 1f) { "keywordsThreshold must be in (0, 1)" }
        require(keywordsScore > 0f && numThreads >= 1) { "keywordsScore must be > 0 and numThreads >= 1" }
        require(repeatGapMs >= 0 && narratorHoldMs >= 0) { "repeatGapMs and narratorHoldMs must be >= 0" }
    }

    /** Samples per chunk at [sampleRate]. */
    val chunkSamples: Int get() = sampleRate * chunkMs / MS_PER_SECOND

    private companion object {
        const val MS_PER_SECOND = 1000
    }
}
