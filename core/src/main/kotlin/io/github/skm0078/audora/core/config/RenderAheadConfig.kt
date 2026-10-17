// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/RenderAheadConfig.kt
// Purpose: Tunables for sizing the render-ahead window from each voice's measured speed (P4-02, ADR-027).

package io.github.skm0078.audora.core.config

/**
 * The window holds enough rendered audio ahead of the playhead to cover rendering one long sentence while the phone
 * runs slower than usual: `rtf × longSentenceSeconds × slowdownMargin`, kept within [minAheadSeconds] and
 * [maxAheadSeconds]. Rendering a sentence of `d` seconds takes `rtf × d`; while it renders the player drains that
 * much of the queue, so the queue must hold at least that much or the player waits (ADR-027). Every duration here is
 * audio at 1.0×: at playback speed `s` the player drains the queue `s` times faster, so the policy passes `rtf × s`.
 *
 * At a (re)start the queue is empty, so a short first sentence followed by a long one would stall at once: the first
 * sentence is held back until the sentences already in the window are predicted to render in time
 * ([startMargin]), for at most [maxStartHoldSeconds]. A fast voice is never held; a slow one starts a few seconds
 * later instead of pausing after its first sentence.
 *
 * @property assumedRtf real-time factor (render time / audio time) of a voice never measured on this phone. 0.5 is
 *   pessimistic for Piper (S1 predicts 0.15–0.25 on the Moto) and about right for Supertonic (0.35–0.5).
 * @property assumedCharsPerSecond speech rate used to guess a sentence's length before its voice was measured:
 *   about 160 words a minute.
 * @property longSentenceSeconds the long sentence the window must absorb. Most sentences read in under 15 s; legal
 *   and academic run-ons reach 30 s. Chosen with RenderAheadSimulationTest: 20 s still stalled at RTF 0.4.
 * @property slowdownMargin headroom for thermal throttling, another app on the CPU, or a model swap mid-book.
 * @property startMargin how much slower than measured the start check assumes rendering will be.
 * @property maxStartHoldSeconds the longest the first sentence is held back; a voice that can never be predicted to
 *   keep up (slower than real time) starts after this and stalls later.
 * @property minAheadSeconds never less than this ahead, even for the fastest voice.
 * @property maxAheadSeconds never more: bounds memory (90 s of 44.1 kHz audio is ~7.6 MiB) and the work thrown away
 *   when the reader skips. A voice slower than real time stalls whatever the window; this only delays it.
 * @property maxAheadUnits cap on sentences rendered ahead, whatever their length.
 */
data class RenderAheadConfig(
    val assumedRtf: Double = 0.5,
    val assumedCharsPerSecond: Double = 14.0,
    val longSentenceSeconds: Double = 30.0,
    val slowdownMargin: Double = 1.5,
    val startMargin: Double = 1.2,
    val maxStartHoldSeconds: Double = 15.0,
    val minAheadSeconds: Double = 8.0,
    val maxAheadSeconds: Double = 90.0,
    val maxAheadUnits: Int = 40,
) {
    init {
        require(assumedRtf > 0) { "assumedRtf must be > 0, was $assumedRtf" }
        require(assumedCharsPerSecond > 0) { "assumedCharsPerSecond must be > 0, was $assumedCharsPerSecond" }
        require(longSentenceSeconds > 0) { "longSentenceSeconds must be > 0, was $longSentenceSeconds" }
        require(slowdownMargin >= 1) { "slowdownMargin must be >= 1, was $slowdownMargin" }
        require(startMargin >= 1) { "startMargin must be >= 1, was $startMargin" }
        require(maxStartHoldSeconds >= 0) { "maxStartHoldSeconds must be >= 0, was $maxStartHoldSeconds" }
        require(minAheadSeconds >= 0) { "minAheadSeconds must be >= 0, was $minAheadSeconds" }
        require(maxAheadSeconds >= minAheadSeconds) {
            "maxAheadSeconds ($maxAheadSeconds) must be >= minAheadSeconds ($minAheadSeconds)"
        }
        require(maxAheadUnits >= 1) { "maxAheadUnits must be >= 1, was $maxAheadUnits" }
    }

    /** Seconds of audio to keep rendered ahead for a voice with real-time factor [rtf]. */
    fun targetAheadSeconds(rtf: Double): Double =
        (rtf * longSentenceSeconds * slowdownMargin).coerceIn(minAheadSeconds, maxAheadSeconds)
}

/**
 * How a voice's speed is measured on this phone and when it counts as too slow (P4-02, ADR-027).
 *
 * @property horizonSeconds measurements are averaged over roughly this much recent audio: each sentence of `d`
 *   seconds moves the average by `d / horizonSeconds`, so one slow sentence (a GC pause) barely shifts it while a
 *   phone that got hot shows up within a couple of minutes.
 * @property maxUsableRtf Build Spec §3 and §13: a premium voice is offered only where it measures faster than this.
 *   Below 1.0 so the voice leaves headroom for the player, the UI and PDF extraction.
 * @property minMeasuredSeconds audio a voice must have rendered before it is judged too slow; one cold sentence is
 *   not a verdict.
 * @property saveEveryRenders measurements are written to disk after this many renders (and lost at most this many
 *   on a kill).
 */
data class VoiceSpeedConfig(
    val horizonSeconds: Double = 120.0,
    val maxUsableRtf: Double = 0.8,
    val minMeasuredSeconds: Double = 30.0,
    val saveEveryRenders: Int = 20,
) {
    init {
        require(horizonSeconds > 0) { "horizonSeconds must be > 0, was $horizonSeconds" }
        require(maxUsableRtf > 0) { "maxUsableRtf must be > 0, was $maxUsableRtf" }
        require(minMeasuredSeconds >= 0) { "minMeasuredSeconds must be >= 0, was $minMeasuredSeconds" }
        require(saveEveryRenders >= 1) { "saveEveryRenders must be >= 1, was $saveEveryRenders" }
    }
}
