// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/bench/BenchReport.kt
// Purpose: Formats benchmark runs and listening ratings as the Markdown report the tester shares back. The same
//          tables go into research/spikes/S1-voice-benchmark/REPORT.md unchanged.

package io.github.skm0078.audora.spike.voices.bench

import io.github.skm0078.audora.spike.voices.listen.BlindEntry
import java.util.Locale

data class ListeningRow(val lang: String, val entry: BlindEntry, val label: String, val rating: Int?)

data class ReportInput(
    val appVersion: String,
    val startedAt: String,
    val device: List<Pair<String, String>>,
    val runs: List<VoiceRun>,
    val listening: List<ListeningRow>,
    val notes: List<String> = emptyList(),
)

object BenchReport {
    fun format(input: ReportInput, config: BenchConfig = BenchConfig()): String = buildString {
        appendLine("# Audora S1 voice benchmark")
        appendLine()
        appendLine("App ${input.appVersion}, run started ${input.startedAt}")
        input.device.forEach { (key, value) -> appendLine("- $key: $value") }
        appendLine()
        appendLine("## Speed")
        appendLine()
        appendLine(
            "First sentence = load + first render (what a reader waits for after Play). " +
                "RTF = render time / audio time; below ${config.realTimeRtfLimit} keeps up with render-ahead.",
        )
        appendLine()
        appendLine("| Voice | Threads | Load ms | First sentence ms | RTF mean | RTF max | RSS MB | Thermal | Result |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        input.runs.forEach { appendLine(row(it, config)) }
        val details = input.runs.filter { it.details.isNotEmpty() }.distinctBy { it.voiceId }
        if (details.isNotEmpty()) {
            appendLine()
            appendLine("## Voice details")
            details.forEach { appendLine("- ${it.voiceId}: ${it.details.joinToString("; ")}") }
        }
        appendLine()
        appendLine("## Listening (blind)")
        if (input.listening.isEmpty()) {
            appendLine("No ratings yet.")
        } else {
            appendLine()
            appendLine("| Lang | Letter | Voice | Rating 1-5 |")
            appendLine("|---|---|---|---|")
            input.listening.forEach {
                appendLine("| ${it.lang} | ${it.entry.letter} | ${it.label} | ${it.rating ?: "not rated"} |")
            }
        }
        if (input.notes.isNotEmpty()) {
            appendLine()
            appendLine("## Notes")
            input.notes.forEach { appendLine("- $it") }
        }
    }

    private fun row(run: VoiceRun, config: BenchConfig): String {
        val rtfMean = run.rtfMean
        val result = when {
            !run.ok -> "FAILED: ${run.error}"
            rtfMean != null && rtfMean < config.realTimeRtfLimit -> "real time"
            else -> "too slow"
        }
        val thermal = run.thermalStart.takeIf { it == run.thermalEnd } ?: "${run.thermalStart}→${run.thermalEnd}"
        val rss = run.rssAfterMb?.toString() ?: "n/a"
        return "| ${run.voiceId} | ${run.threads ?: "system"} | ${run.loadMs} | ${run.firstSentenceMs} | " +
            "${fmt(rtfMean)} | ${fmt(run.rtfMax)} | $rss | $thermal | $result |"
    }

    private fun fmt(value: Double?): String = value?.let { "%.3f".format(Locale.US, it) } ?: "n/a"
}
