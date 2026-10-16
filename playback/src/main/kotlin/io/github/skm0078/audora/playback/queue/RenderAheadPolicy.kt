// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/queue/RenderAheadPolicy.kt
// Purpose: Decides how many sentences to render ahead: a fixed count, or enough audio for the voice's speed (P4-02).

package io.github.skm0078.audora.playback.queue

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.voices.VoiceSpeed

/** Picks the render window out of the sentences queued after the playhead. Called on the main thread. */
fun interface RenderAheadPolicy {
    /**
     * [candidates] are the sentence playing followed by up to `renderAhead.maxAheadUnits` after it, in playlist
     * order. Returns a prefix of them: the sentence playing plus those to render now.
     */
    fun window(candidates: List<ReadingUnit>, config: QueueConfig): List<ReadingUnit>

    /**
     * Whether the first sentence after a (re)start may play now. [bufferedSeconds] is the audio rendered from it on
     * without a gap; [pending] are the window's sentences after that, not rendered yet, in order. Called by the render
     * queue while it holds its lock, so it must be quick. Default: start at once.
     */
    fun canStart(bufferedSeconds: Double, pending: List<ReadingUnit>, config: QueueConfig): Boolean = true

    companion object {
        /** Build Spec §6's original rule: [QueueConfig.prefetchUnits] sentences, whatever the voice. */
        val FIXED = RenderAheadPolicy { candidates, config -> candidates.take(1 + config.prefetchUnits) }
    }
}

/**
 * Renders ahead enough *audio* to ride out one long sentence on a slow voice (ADR-027): the target is
 * `RenderAheadConfig.targetAheadSeconds(rtf)` for the slowest voice among the candidates, and sentences are added
 * until their estimated audio reaches it, never fewer than [QueueConfig.prefetchUnits]. A sentence's audio is
 * estimated from its length and its voice's measured speech rate; voices not measured yet use the config's
 * assumptions. The sentence playing is not counted: part of it has already played.
 *
 * @param speedOf the measured speed of the voice that reads a language now, or null if not measured.
 */
class MeasuredRenderAhead(private val speedOf: (Lang) -> VoiceSpeed?) : RenderAheadPolicy {
    override fun window(candidates: List<ReadingUnit>, config: QueueConfig): List<ReadingUnit> {
        if (candidates.isEmpty()) return candidates
        val settings = config.renderAhead
        val ahead = candidates.subList(1, candidates.size).take(settings.maxAheadUnits)
        val speeds = speedsOf(candidates)
        val rtf = speeds.values.maxOf { it?.rtf ?: settings.assumedRtf }
        val target = settings.targetAheadSeconds(rtf)
        var seconds = 0.0
        var count = 0
        while (count < ahead.size && (count < config.prefetchUnits || seconds < target)) {
            seconds += audioSeconds(ahead[count], speeds, config)
            count++
        }
        return candidates.take(1 + count)
    }

    /**
     * Plays once every pending sentence is predicted to be rendered before the playhead reaches it: rendering them in
     * order, each taking `rtf × startMargin × its length`, must stay behind the audio already buffered plus the
     * pending sentences before it.
     */
    override fun canStart(bufferedSeconds: Double, pending: List<ReadingUnit>, config: QueueConfig): Boolean {
        val speeds = speedsOf(pending)
        val margin = config.renderAhead.startMargin
        var rendering = 0.0
        var playable = bufferedSeconds
        for (unit in pending) {
            val seconds = audioSeconds(unit, speeds, config)
            rendering += (speeds[unit.lang]?.rtf ?: config.renderAhead.assumedRtf) * margin * seconds
            if (rendering > playable) return false
            playable += seconds
        }
        return true
    }

    private fun speedsOf(units: List<ReadingUnit>): Map<Lang, VoiceSpeed?> =
        units.map { it.lang }.distinct().associateWith(speedOf)

    /** A sentence's length in audio, guessed from its text and its voice's measured speech rate. */
    private fun audioSeconds(unit: ReadingUnit, speeds: Map<Lang, VoiceSpeed?>, config: QueueConfig): Double =
        unit.text.length / (speeds[unit.lang]?.charsPerSecond ?: config.renderAhead.assumedCharsPerSecond)
}
