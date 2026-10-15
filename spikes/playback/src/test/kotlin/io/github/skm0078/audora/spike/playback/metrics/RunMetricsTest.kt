// File: spikes/playback/src/test/kotlin/io/github/skm0078/audora/spike/playback/metrics/RunMetricsTest.kt
// Purpose: The numbers behind the S3 verdict: stalls, ENDED with text left, gaps, CPU sleep, screen-off time.

package io.github.skm0078.audora.spike.playback.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunMetricsTest {
    private val metrics = RunMetrics(startRealtimeMs = 0)

    @Test
    fun `buffering after first ready is a stall, initial buffering is not`() {
        metrics.onPlayerState(PlayerPhase.BUFFERING, true, nowMs = 0, unitsRemaining = 10)
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 500, unitsRemaining = 10)
        metrics.onPlayerState(PlayerPhase.BUFFERING, true, nowMs = 5_000, unitsRemaining = 9)
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 6_200, unitsRemaining = 9)

        val snapshot = metrics.snapshot(nowMs = 10_000)
        assertEquals(1, snapshot.stallCount)
        assertEquals(1_200, snapshot.stallTotalMs)
        assertEquals(4_500 + 3_800L, snapshot.playingMs)
    }

    @Test
    fun `ended with text left is an invariant violation`() {
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 0, unitsRemaining = 3)
        metrics.onPlayerState(PlayerPhase.ENDED, true, nowMs = 100, unitsRemaining = 3)

        assertEquals(1, metrics.snapshot(200).invariantViolations)
    }

    @Test
    fun `idle after the user finished the run is not a violation`() {
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 0, unitsRemaining = 3)
        metrics.finish(nowMs = 100)
        metrics.onPlayerState(PlayerPhase.IDLE, false, nowMs = 100, unitsRemaining = 3)

        assertEquals(0, metrics.snapshot(200).invariantViolations)
    }

    @Test
    fun `ended on the last unit is legitimate`() {
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 0, unitsRemaining = 1)
        metrics.onPlayerState(PlayerPhase.ENDED, true, nowMs = 100, unitsRemaining = 0)

        assertEquals(0, metrics.snapshot(200).invariantViolations)
    }

    @Test
    fun `clean gap is time between transitions minus spoken length`() {
        metrics.onTransition(newUnit = 0, newUnitDurationMs = 3_000, automatic = false, nowMs = 0)
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 100, unitsRemaining = 10)
        metrics.onTransition(newUnit = 1, newUnitDurationMs = 2_000, automatic = true, nowMs = 3_100)
        metrics.onTransition(newUnit = 2, newUnitDurationMs = 4_000, automatic = true, nowMs = 5_140)
        metrics.onTransition(newUnit = 3, newUnitDurationMs = 4_000, automatic = true, nowMs = 9_160)

        val snapshot = metrics.snapshot(10_000)
        // Unit 0 started with initial buffering, so only units 1 and 2 count.
        assertEquals(2, snapshot.cleanGapCount)
        assertEquals(20L, snapshot.cleanGapP50Ms)
        assertEquals(40L, snapshot.cleanGapMaxMs)
        assertEquals(3, snapshot.unitsCompleted)
        assertEquals(9_000, snapshot.audioCompletedMs)
    }

    @Test
    fun `a stall inside a unit excludes it from the clean gaps`() {
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 0, unitsRemaining = 10)
        metrics.onTransition(newUnit = 1, newUnitDurationMs = 2_000, automatic = true, nowMs = 0)
        metrics.onPlayerState(PlayerPhase.BUFFERING, true, nowMs = 1_000, unitsRemaining = 10)
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 2_000, unitsRemaining = 10)
        metrics.onTransition(newUnit = 2, newUnitDurationMs = 2_000, automatic = true, nowMs = 3_000)

        val snapshot = metrics.snapshot(4_000)
        assertEquals(0, snapshot.cleanGapCount)
        assertEquals(1, snapshot.disturbedItems)
        assertNull(snapshot.cleanGapP50Ms)
    }

    @Test
    fun `realtime outrunning uptime while playing counts as deep sleep`() {
        metrics.onPlayerState(PlayerPhase.READY, true, nowMs = 0, unitsRemaining = 10)
        metrics.onHeartbeat(Heartbeat(realtimeMs = 0, uptimeMs = 0, screenOn = false))
        metrics.onHeartbeat(Heartbeat(realtimeMs = 30_000, uptimeMs = 30_000, screenOn = false))
        metrics.onHeartbeat(Heartbeat(realtimeMs = 90_000, uptimeMs = 40_000, screenOn = false))

        val snapshot = metrics.snapshot(90_000)
        assertEquals(50_000, snapshot.deepSleepWhilePlayingMs)
        assertEquals(90_000, snapshot.screenOffPlayingMs)
        assertEquals(60_000, snapshot.maxHeartbeatGapMs)
    }

    @Test
    fun `measured rtf is render time over audio time`() {
        metrics.onRendered(renderNanos = 3_000_000_000, audioMs = 2_500)
        metrics.onRendered(renderNanos = 3_000_000_000, audioMs = 2_500)

        assertEquals(1.2, metrics.snapshot(0).measuredRtf!!, 1e-9)
    }

    @Test
    fun `nearest-rank percentile`() {
        val sorted = (1L..20L).toList()
        assertEquals(10L, percentile(sorted, 0.5))
        assertEquals(19L, percentile(sorted, 0.95))
        assertNull(percentile(emptyList(), 0.5))
    }
}
