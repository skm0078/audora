// File: spikes/playback/src/test/kotlin/io/github/skm0078/audora/spike/playback/metrics/GateVerdictTest.kt
// Purpose: The S3 exit criteria give PASS only for a full, clean, screen-off hour.

package io.github.skm0078.audora.spike.playback.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GateVerdictTest {
    private val hour = 60L * 60 * 1_000
    private val clean = RunSnapshot(
        wallMs = hour, playingMs = hour - 600_000, phase = PlayerPhase.READY, playWhenReady = true,
        currentUnit = 600, unitsCompleted = 599, audioCompletedMs = hour - 600_000, stallCount = 599,
        stallTotalMs = 600_000, stallMaxMs = 2_000, invariantViolations = 0, errorCount = 0, lastError = null,
        cleanGapCount = 0, cleanGapP50Ms = null, cleanGapP95Ms = null, cleanGapMaxMs = null, disturbedItems = 599,
        measuredRtf = 1.2, renders = 600, renderFailures = 0, deepSleepWhilePlayingMs = 0,
        screenOffPlayingMs = hour - 60_000, maxHeartbeatGapMs = 30_000, finished = false,
    )

    @Test
    fun `clean hour passes`() {
        assertEquals(Verdict.PASS, clean.evaluate().verdict)
    }

    @Test
    fun `short run in progress is running, stopped short run fails`() {
        val short = clean.copy(wallMs = hour / 2)
        assertEquals(Verdict.RUNNING, short.evaluate().verdict)
        assertEquals(Verdict.FAIL, short.copy(finished = true).evaluate().verdict)
    }

    @Test
    fun `process death, violations or cpu sleep fail at once`() {
        assertEquals(Verdict.FAIL, clean.evaluate(processDied = true).verdict)
        assertEquals(Verdict.FAIL, clean.copy(invariantViolations = 1).evaluate().verdict)
        val slept = clean.copy(deepSleepWhilePlayingMs = 120_000).evaluate()
        assertEquals(Verdict.FAIL, slept.verdict)
        assertTrue(slept.reasons.single().contains("CPU slept"))
    }
}
