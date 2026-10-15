// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/bench/BenchModel.kt
// Purpose: Benchmark configuration and result types. Plain data, shared by the runner, the report and the UI.

package io.github.skm0078.audora.spike.voices.bench

import io.github.skm0078.audora.spike.voices.catalog.TextKind

data class BenchConfig(
    /**
     * sherpa-onnx thread counts to measure. 2 leaves cores free for the player and UI (the real app's case);
     * 4 matches the Moto's four performance cores and shows the ceiling.
     */
    val threadCounts: List<Int> = DEFAULT_THREAD_COUNTS,
    /** Texts concatenated into each voice's listening sample: a natural passage, then the hard one. */
    val sampleKinds: List<TextKind> = listOf(TextKind.PROSE, TextKind.HARD),
    val sampleGapMs: Int = DEFAULT_SAMPLE_GAP_MS,
    /** RTF above this cannot keep up even with render-ahead (spec §9: premium voices need RTF < 0.8). */
    val realTimeRtfLimit: Double = DEFAULT_REAL_TIME_RTF_LIMIT,
) {
    companion object {
        val DEFAULT_THREAD_COUNTS = listOf(2, 4)
        const val DEFAULT_SAMPLE_GAP_MS = 700
        const val DEFAULT_REAL_TIME_RTF_LIMIT = 0.8
    }
}

data class TextTiming(val textId: String, val kind: TextKind, val renderMs: Long, val audioMs: Long) {
    val rtf: Double get() = if (audioMs > 0) renderMs.toDouble() / audioMs else Double.POSITIVE_INFINITY
}

data class VoiceRun(
    val voiceId: String,
    val label: String,
    val lang: String,
    val threads: Int?,
    val loadMs: Long = 0,
    /** First render right after loading, of the first-sentence text: what a reader waits for after "play". */
    val coldFirstMs: Long = 0,
    val warm: List<TextTiming> = emptyList(),
    val rssBeforeMb: Long? = null,
    val rssAfterMb: Long? = null,
    val thermalStart: String = "",
    val thermalEnd: String = "",
    val details: List<String> = emptyList(),
    val error: String? = null,
) {
    /** Tap-to-first-audio when the voice is not loaded yet: load + first sentence. */
    val firstSentenceMs: Long get() = loadMs + coldFirstMs

    /** Mean RTF over the warm renders of the longer texts (the first sentence is too short to be stable). */
    val rtfMean: Double? get() = warm.filter { it.kind != TextKind.FIRST }.map { it.rtf }.takeIf { it.isNotEmpty() }
        ?.average()

    val rtfMax: Double? get() = warm.maxOfOrNull { it.rtf }

    val ok: Boolean get() = error == null
}
