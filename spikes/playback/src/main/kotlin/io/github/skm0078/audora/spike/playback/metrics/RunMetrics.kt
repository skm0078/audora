// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/metrics/RunMetrics.kt
// Purpose: Turns player events, render timings and heartbeats into the numbers the S3 exit criteria need.

package io.github.skm0078.audora.spike.playback.metrics

/** Player states, mirrored so this class stays plain Kotlin and JVM-testable. */
enum class PlayerPhase { IDLE, BUFFERING, READY, ENDED }

/**
 * Thread-safe facade over the individual trackers: player events arrive on the main thread, render
 * timings on the worker thread. Times are elapsedRealtime milliseconds, which keep counting while the
 * CPU sleeps; heartbeat uptime deliberately does not.
 */
class RunMetrics(private val startRealtimeMs: Long, config: MetricsConfig = MetricsConfig()) {
    private val playback = PlaybackTracker()
    private val gaps = GapTracker(config.maxGapSamples)
    private val renders = RenderTracker()
    private val sleep = SleepTracker()
    private var invariantViolations = 0
    private var errorCount = 0
    private var lastError: String? = null
    private var finished = false

    @Synchronized
    fun onPlayerState(phase: PlayerPhase, playWhenReady: Boolean, nowMs: Long, unitsRemaining: Int) {
        val stallStarted = playback.update(phase, playWhenReady, nowMs)
        if (stallStarted || !playWhenReady) gaps.markDisturbed()
        sleep.playWhenReady = playWhenReady
        val stoppedEarly = phase == PlayerPhase.ENDED || (phase == PlayerPhase.IDLE && playback.hasBeenReady)
        if (!finished && stoppedEarly && unitsRemaining > 0) invariantViolations++
    }

    /**
     * A unit started playing. [newUnitDurationMs] is its spoken length, used to compute the gap at the next
     * transition: gap = time between transitions - spoken length of the unit in between.
     */
    @Synchronized
    fun onTransition(newUnit: Int, newUnitDurationMs: Long, automatic: Boolean, nowMs: Long) {
        gaps.onTransition(
            newUnit = newUnit,
            newUnitDurationMs = newUnitDurationMs,
            automatic = automatic,
            nowMs = nowMs,
            // A seek or a new playlist starts with buffering that is not a sentence gap.
            startsDisturbed = !automatic || !playback.playWhenReady || playback.isStalled,
        )
    }

    @Synchronized
    fun onRendered(renderNanos: Long, audioMs: Long) = renders.onRendered(renderNanos, audioMs)

    @Synchronized
    fun onRenderFailed() = renders.onFailed()

    @Synchronized
    fun onError(message: String) {
        errorCount++
        lastError = message
    }

    @Synchronized
    fun onHeartbeat(beat: Heartbeat) = sleep.onHeartbeat(beat)

    /** Stops counting violations: the user ended the run, so IDLE from here on is expected. */
    @Synchronized
    fun finish(nowMs: Long) {
        playback.close(nowMs)
        finished = true
    }

    @Synchronized
    fun snapshot(nowMs: Long): RunSnapshot = RunSnapshot(
        wallMs = nowMs - startRealtimeMs,
        playingMs = playback.playingMs(nowMs),
        phase = playback.phase,
        playWhenReady = playback.playWhenReady,
        currentUnit = gaps.currentUnit,
        unitsCompleted = gaps.unitsCompleted,
        audioCompletedMs = gaps.audioCompletedMs,
        stallCount = playback.stallCount(nowMs),
        stallTotalMs = playback.stallTotalMs(nowMs),
        stallMaxMs = playback.stallMaxMs(nowMs),
        invariantViolations = invariantViolations,
        errorCount = errorCount,
        lastError = lastError,
        cleanGapCount = gaps.sampleCount,
        cleanGapP50Ms = gaps.percentile(MEDIAN),
        cleanGapP95Ms = gaps.percentile(P95),
        cleanGapMaxMs = gaps.max(),
        disturbedItems = gaps.disturbedItems,
        measuredRtf = renders.measuredRtf(),
        renders = renders.count,
        renderFailures = renders.failures,
        deepSleepWhilePlayingMs = sleep.deepSleepWhilePlayingMs,
        screenOffPlayingMs = sleep.screenOffPlayingMs,
        maxHeartbeatGapMs = sleep.maxHeartbeatGapMs,
        finished = finished,
    )

    private companion object {
        const val MEDIAN = 0.50
        const val P95 = 0.95
    }
}

/** One timer tick: both clocks plus whether the screen was on. */
data class Heartbeat(val realtimeMs: Long, val uptimeMs: Long, val screenOn: Boolean)

data class MetricsConfig(
    /** Caps memory on very long runs; 5,000 gaps is well over an hour of sentences. */
    val maxGapSamples: Int = DEFAULT_MAX_GAP_SAMPLES,
) {
    companion object {
        const val DEFAULT_MAX_GAP_SAMPLES = 5_000
    }
}

/** Audible playing time and stalls (BUFFERING while playWhenReady, after playback first became READY). */
internal class PlaybackTracker {
    var phase = PlayerPhase.IDLE
        private set
    var playWhenReady = false
        private set
    var hasBeenReady = false
        private set
    private var playingSinceMs: Long? = null
    private var playingTotalMs = 0L
    private var stallStartMs: Long? = null
    private var stalls = 0
    private var stallTotal = 0L
    private var stallMax = 0L

    val isStalled: Boolean get() = stallStartMs != null

    /** Returns true when this update starts a stall. */
    fun update(newPhase: PlayerPhase, newPlayWhenReady: Boolean, nowMs: Long): Boolean {
        val wasPlaying = isPlaying()
        val wasStalled = isStalled
        phase = newPhase
        playWhenReady = newPlayWhenReady
        if (newPhase == PlayerPhase.READY) hasBeenReady = true

        if (wasPlaying != isPlaying()) {
            if (isPlaying()) playingSinceMs = nowMs else closePlaying(nowMs)
        }
        val stalledNow = hasBeenReady && playWhenReady && phase == PlayerPhase.BUFFERING
        if (wasStalled && !stalledNow) closeStall(nowMs)
        if (!wasStalled && stalledNow) stallStartMs = nowMs
        return !wasStalled && stalledNow
    }

    fun close(nowMs: Long) {
        closePlaying(nowMs)
        closeStall(nowMs)
    }

    fun playingMs(nowMs: Long) = playingTotalMs + (playingSinceMs?.let { nowMs - it } ?: 0L)

    fun stallCount(nowMs: Long) = stalls + if (liveStall(nowMs) > 0) 1 else 0

    fun stallTotalMs(nowMs: Long) = stallTotal + liveStall(nowMs)

    fun stallMaxMs(nowMs: Long) = maxOf(stallMax, liveStall(nowMs))

    private fun liveStall(nowMs: Long) = stallStartMs?.let { nowMs - it } ?: 0L

    private fun isPlaying() = playWhenReady && phase == PlayerPhase.READY

    private fun closePlaying(nowMs: Long) {
        playingSinceMs?.let { playingTotalMs += nowMs - it }
        playingSinceMs = null
    }

    private fun closeStall(nowMs: Long) {
        val start = stallStartMs ?: return
        val duration = nowMs - start
        stalls++
        stallTotal += duration
        stallMax = maxOf(stallMax, duration)
        stallStartMs = null
    }
}

/** Inter-item gaps; only automatic transitions over an undisturbed unit (no pause, no stall) count. */
internal class GapTracker(private val maxSamples: Int) {
    private val cleanGapsMs = ArrayList<Long>()
    private var lastTransitionMs: Long? = null
    private var lastItemDurationMs: Long? = null
    private var itemDisturbed = false
    var currentUnit = 0
        private set
    var unitsCompleted = 0
        private set
    var audioCompletedMs = 0L
        private set
    var disturbedItems = 0
        private set

    val sampleCount: Int get() = cleanGapsMs.size

    fun markDisturbed() {
        itemDisturbed = true
    }

    fun onTransition(newUnit: Int, newUnitDurationMs: Long, automatic: Boolean, nowMs: Long, startsDisturbed: Boolean) {
        val lastAt = lastTransitionMs
        val lastDuration = lastItemDurationMs
        if (automatic && lastAt != null && lastDuration != null) {
            unitsCompleted++
            audioCompletedMs += lastDuration
            if (itemDisturbed) {
                disturbedItems++
            } else if (cleanGapsMs.size < maxSamples) {
                cleanGapsMs += nowMs - lastAt - lastDuration
            }
        }
        currentUnit = newUnit
        lastTransitionMs = nowMs
        lastItemDurationMs = newUnitDurationMs
        itemDisturbed = startsDisturbed
    }

    fun percentile(fraction: Double): Long? = percentile(cleanGapsMs.sorted(), fraction)

    fun max(): Long? = cleanGapsMs.maxOrNull()
}

/** How fast the engine really rendered, measured, not configured. */
internal class RenderTracker {
    var count = 0
        private set
    var failures = 0
        private set
    private var nanosTotal = 0L
    private var audioMsTotal = 0L

    fun onRendered(renderNanos: Long, audioMs: Long) {
        count++
        nanosTotal += renderNanos
        audioMsTotal += audioMs
    }

    fun onFailed() {
        failures++
    }

    fun measuredRtf(): Double? = if (audioMsTotal > 0) nanosTotal / NANOS_PER_MILLI / audioMsTotal else null

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}

/**
 * Heartbeat clocks. Realtime advancing faster than uptime means the CPU was suspended; while
 * playWhenReady that is exactly the failure C5 is about.
 */
internal class SleepTracker {
    var playWhenReady = false
    var deepSleepWhilePlayingMs = 0L
        private set
    var screenOffPlayingMs = 0L
        private set
    var maxHeartbeatGapMs = 0L
        private set
    private var lastHeartbeat: Heartbeat? = null

    fun onHeartbeat(beat: Heartbeat) {
        val previous = lastHeartbeat
        lastHeartbeat = beat
        if (previous == null) return
        val realDelta = beat.realtimeMs - previous.realtimeMs
        val upDelta = beat.uptimeMs - previous.uptimeMs
        maxHeartbeatGapMs = maxOf(maxHeartbeatGapMs, realDelta)
        if (playWhenReady) {
            deepSleepWhilePlayingMs += (realDelta - upDelta).coerceAtLeast(0)
            if (!beat.screenOn) screenOffPlayingMs += realDelta
        }
    }
}

/** Nearest-rank percentile of an ascending list; null when empty. */
internal fun percentile(sorted: List<Long>, fraction: Double): Long? {
    if (sorted.isEmpty()) return null
    val rank = kotlin.math.ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}
