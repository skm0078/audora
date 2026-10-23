// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/VoiceCommandConfig.kt
// Purpose: Tunables for spoken commands ("ListenLock, pause") heard by the on-phone keyword spotter (plan item 38).

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
 * @property screenOffIdleMs screen-off listening ends once reading has stayed paused this long and the reader is
 *   not on screen: a battery choice (the microphone and the spotter cost power for as long as they run). It matches
 *   Media3's foreground timeout after a pause (its default and maximum, 10 minutes), after which the playback
 *   notification goes too, so the clock starts at the pause, not when the reader is hidden (ADR-051).
 * @property gateDbfs a chunk quieter than this (its RMS, in dB below full scale) is not decoded: a quiet room costs
 *   only the read. Above a quiet room's noise floor with VOICE_RECOGNITION (no gain), below speech a metre away.
 * @property preRollChunks quiet chunks kept and decoded first when sound starts, so a softly spoken "Au-" is heard.
 * @property hangoverMs chunks keep being decoded this long after the last loud one, so the spotter sees a phrase's
 *   tail and the pause between "ListenLock" and the command.
 * @property silenceCheckMs how often the listener asks Android whether its capture is silenced (a call, the
 *   microphone toggle); a binder call, so not every chunk.
 * @property toneVolume the short tone that confirms each command heard (ToneGenerator's 0..100).
 * @property toneMs how long that tone plays.
 */
data class VoiceCommandConfig(
    val sampleRate: Int = 16_000,
    val chunkMs: Int = 100,
    val keywordsScore: Float = 1.5f,
    val keywordsThreshold: Float = 0.3f,
    val numThreads: Int = 1,
    val repeatGapMs: Long = 1_500,
    val narratorHoldMs: Long = 2_000,
    val screenOffIdleMs: Long = 600_000,
    val gateDbfs: Float = DEFAULT_GATE_DBFS,
    val preRollChunks: Int = 3,
    val hangoverMs: Long = 1_500,
    val silenceCheckMs: Long = 1_000,
    val toneVolume: Int = 60,
    val toneMs: Int = 120,
) {
    init {
        require(sampleRate > 0 && chunkMs > 0) { "sampleRate and chunkMs must be > 0" }
        require(keywordsThreshold > 0f && keywordsThreshold < 1f) { "keywordsThreshold must be in (0, 1)" }
        require(keywordsScore > 0f && numThreads >= 1) { "keywordsScore must be > 0 and numThreads >= 1" }
        require(repeatGapMs >= 0 && narratorHoldMs >= 0) { "repeatGapMs and narratorHoldMs must be >= 0" }
        require(screenOffIdleMs >= 0) { "screenOffIdleMs must be >= 0, was $screenOffIdleMs" }
        require(gateDbfs < 0f && preRollChunks >= 0) { "gateDbfs must be < 0 and preRollChunks >= 0" }
        require(hangoverMs >= 0 && silenceCheckMs > 0) { "hangoverMs must be >= 0 and silenceCheckMs > 0" }
        require(toneVolume in 0..MAX_TONE_VOLUME && toneMs > 0) { "toneVolume must be in 0..100 and toneMs > 0" }
    }

    /** Samples per chunk at [sampleRate]. */
    val chunkSamples: Int get() = sampleRate * chunkMs / MS_PER_SECOND

    /** [hangoverMs] in whole chunks, rounded up. */
    val hangoverChunks: Int get() = ((hangoverMs + chunkMs - 1) / chunkMs).toInt()

    /** Whole minutes of [screenOffIdleMs], for the texts that name it. */
    val screenOffIdleMinutes: Int get() = (screenOffIdleMs / MS_PER_MINUTE).toInt()

    private companion object {
        const val MS_PER_SECOND = 1000
        const val MS_PER_MINUTE = 60_000L
        const val MAX_TONE_VOLUME = 100
        const val DEFAULT_GATE_DBFS = -45f
    }
}
