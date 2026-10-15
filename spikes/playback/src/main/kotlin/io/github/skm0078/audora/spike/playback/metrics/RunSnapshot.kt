// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/metrics/RunSnapshot.kt
// Purpose: An immutable copy of a run's numbers, plus the S3 pass/fail verdict computed from them.

package io.github.skm0078.audora.spike.playback.metrics

data class RunSnapshot(
    val wallMs: Long,
    val playingMs: Long,
    val phase: PlayerPhase,
    val playWhenReady: Boolean,
    val currentUnit: Int,
    val unitsCompleted: Int,
    val audioCompletedMs: Long,
    val stallCount: Int,
    val stallTotalMs: Long,
    val stallMaxMs: Long,
    val invariantViolations: Int,
    val errorCount: Int,
    val lastError: String?,
    val cleanGapCount: Int,
    val cleanGapP50Ms: Long?,
    val cleanGapP95Ms: Long?,
    val cleanGapMaxMs: Long?,
    val disturbedItems: Int,
    val measuredRtf: Double?,
    val renders: Int,
    val renderFailures: Int,
    val deepSleepWhilePlayingMs: Long,
    val screenOffPlayingMs: Long,
    val maxHeartbeatGapMs: Long,
    val finished: Boolean,
)

/** The S3 exit criteria from docs/TICKET-PLAN.md, as numbers. */
data class GateCriteria(
    val minWallMs: Long = DEFAULT_MIN_WALL_MS,
    /** Most of the run must be screen-off for it to prove anything. */
    val minScreenOffPlayingMs: Long = DEFAULT_MIN_SCREEN_OFF_MS,
    /** Heartbeat clocks are coarse; a few seconds of measured suspension is noise, minutes is a failure. */
    val maxDeepSleepWhilePlayingMs: Long = DEFAULT_MAX_DEEP_SLEEP_MS,
) {
    companion object {
        const val DEFAULT_MIN_WALL_MS = 60L * 60 * 1_000
        const val DEFAULT_MIN_SCREEN_OFF_MS = 50L * 60 * 1_000
        const val DEFAULT_MAX_DEEP_SLEEP_MS = 5_000L
    }
}

enum class Verdict { PASS, FAIL, RUNNING }

data class GateResult(val verdict: Verdict, val reasons: List<String>)

fun RunSnapshot.evaluate(criteria: GateCriteria = GateCriteria(), processDied: Boolean = false): GateResult {
    val failures = buildList {
        if (processDied) add("the app process was killed during the run")
        if (invariantViolations > 0) add("player stopped (ENDED/IDLE) with text left $invariantViolations time(s)")
        if (errorCount > 0) add("$errorCount player error(s), last: $lastError")
        if (renderFailures > 0) add("$renderFailures render failure(s)")
        if (deepSleepWhilePlayingMs > criteria.maxDeepSleepWhilePlayingMs) {
            add("CPU slept ${deepSleepWhilePlayingMs / MS_PER_SECOND} s while it should have been playing")
        }
    }
    if (failures.isNotEmpty()) return GateResult(Verdict.FAIL, failures)
    val pending = buildList {
        if (wallMs < criteria.minWallMs) add("run time below ${criteria.minWallMs / MS_PER_MINUTE} min")
        if (screenOffPlayingMs < criteria.minScreenOffPlayingMs) {
            add("screen-off play time below ${criteria.minScreenOffPlayingMs / MS_PER_MINUTE} min")
        }
    }
    return when {
        pending.isEmpty() -> GateResult(Verdict.PASS, emptyList())
        finished -> GateResult(Verdict.FAIL, pending.map { "stopped early: $it" })
        else -> GateResult(Verdict.RUNNING, pending)
    }
}

private const val MS_PER_SECOND = 1_000L
private const val MS_PER_MINUTE = 60_000L
