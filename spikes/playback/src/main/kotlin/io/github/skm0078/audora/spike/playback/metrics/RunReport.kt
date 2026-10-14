// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/metrics/RunReport.kt
// Purpose: Formats a run as the plain-text report the tester shares back; the same text feeds REPORT.md.

package io.github.skm0078.audora.spike.playback.metrics

import java.util.Locale

data class RunReportInput(
    val presetLabel: String,
    val isGateRun: Boolean,
    val configSummary: String,
    val startedAt: String,
    val snapshot: RunSnapshot,
    /** Phone, Android version, battery settings; one "key: value" pair each. */
    val device: List<Pair<String, String>>,
    /** Notable events such as a detected process death and the system's exit reason. */
    val notes: List<String>,
    val processDied: Boolean,
)

object RunReport {
    fun format(input: RunReportInput, criteria: GateCriteria = GateCriteria()): String = buildString {
        val s = input.snapshot
        appendLine("Audora S3 playback spike report")
        appendLine("Run: ${input.presetLabel}, started ${input.startedAt}")
        if (input.isGateRun) {
            val result = s.evaluate(criteria, input.processDied)
            appendLine("Verdict: ${result.verdict}")
            result.reasons.forEach { appendLine("  - $it") }
        }
        appendLine()
        appendLine("Wall time: ${minutes(s.wallMs)}")
        appendLine("Playing (audible) time: ${minutes(s.playingMs)}")
        appendLine("Screen-off while playing: ${minutes(s.screenOffPlayingMs)}")
        appendLine("CPU deep sleep while playing: ${seconds(s.deepSleepWhilePlayingMs)}")
        appendLine("Longest heartbeat gap: ${seconds(s.maxHeartbeatGapMs)}")
        appendLine("Player state now: ${s.phase}, playWhenReady=${s.playWhenReady}, unit ${s.currentUnit}")
        appendLine("Units completed: ${s.unitsCompleted} (${minutes(s.audioCompletedMs)} of audio)")
        appendLine("ENDED/IDLE with text left: ${s.invariantViolations}")
        appendLine("Player errors: ${s.errorCount}${s.lastError?.let { " (last: $it)" } ?: ""}")
        appendLine("Stalls: ${s.stallCount}, total ${seconds(s.stallTotalMs)}, longest ${seconds(s.stallMaxMs)}")
        val rtf = s.measuredRtf?.let { "%.2f".format(Locale.US, it) } ?: "n/a"
        appendLine("Measured RTF: $rtf over ${s.renders} renders")
        appendLine("Render failures: ${s.renderFailures}")
        val gaps = "p50 ${ms(s.cleanGapP50Ms)}, p95 ${ms(s.cleanGapP95Ms)}, max ${ms(s.cleanGapMaxMs)}"
        appendLine("Clean inter-item gap: $gaps over ${s.cleanGapCount} items")
        appendLine("Items with a stall or pause (not in the gap figures): ${s.disturbedItems}")
        appendLine()
        appendLine("Config: ${input.configSummary}")
        input.device.forEach { (key, value) -> appendLine("$key: $value") }
        if (input.notes.isNotEmpty()) {
            appendLine()
            appendLine("Notes:")
            input.notes.forEach { appendLine("  - $it") }
        }
    }

    private fun minutes(ms: Long) = "%.1f min".format(Locale.US, ms / MS_PER_MINUTE)

    private fun seconds(ms: Long) = "%.1f s".format(Locale.US, ms / MS_PER_SECOND)

    private fun ms(value: Long?) = value?.let { "$it ms" } ?: "n/a"

    private const val MS_PER_MINUTE = 60_000.0
    private const val MS_PER_SECOND = 1_000.0
}
