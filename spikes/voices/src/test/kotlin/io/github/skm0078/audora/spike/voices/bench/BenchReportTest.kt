// File: spikes/voices/src/test/kotlin/io/github/skm0078/audora/spike/voices/bench/BenchReportTest.kt
// Purpose: The shared report carries the numbers, a verdict per run, failures and blind ratings.

package io.github.skm0078.audora.spike.voices.bench

import io.github.skm0078.audora.spike.voices.catalog.TextKind
import io.github.skm0078.audora.spike.voices.listen.BlindEntry
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchReportTest {
    private val fast = VoiceRun(
        voiceId = "piper-en-lessac",
        label = "Piper",
        lang = "en",
        threads = 2,
        loadMs = 400,
        coldFirstMs = 120,
        warm = listOf(TextTiming("en-novel", TextKind.PROSE, renderMs = 1_000, audioMs = 10_000)),
        rssAfterMb = 180,
        thermalStart = "none",
        thermalEnd = "light",
    )
    private val slow = fast.copy(
        voiceId = "kokoro-en-heart",
        warm = listOf(TextTiming("en-novel", TextKind.PROSE, 12_000, 10_000)),
    )
    private val broken = fast.copy(voiceId = "mms-bn", error = "missing model files")

    private val text = BenchReport.format(
        ReportInput(
            appVersion = "0.0.1-s1",
            startedAt = "2026-10-15 20:00",
            device = listOf("Phone" to "motorola edge 50 fusion"),
            runs = listOf(fast, slow, broken),
            listening = listOf(ListeningRow("en", BlindEntry("B", "piper-en-lessac"), "Piper", 4)),
        ),
    )

    @Test
    fun `rows carry timings and a verdict`() {
        assertTrue(text.contains("| piper-en-lessac | 2 | 400 | 520 | 0.100 | 0.100 | 180 | none→light | real time |"))
        assertTrue(text.contains("| kokoro-en-heart | 2 | 400 | 520 | 1.200 | 1.200 | 180 | none→light | too slow |"))
        assertTrue(text.contains("FAILED: missing model files"))
    }

    @Test
    fun `device and ratings are included`() {
        assertTrue(text.contains("- Phone: motorola edge 50 fusion"))
        assertTrue(text.contains("| en | B | Piper | 4 |"))
    }
}
