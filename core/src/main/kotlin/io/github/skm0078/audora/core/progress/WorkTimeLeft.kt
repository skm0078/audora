// File: core/src/main/kotlin/io/github/skm0078/audora/core/progress/WorkTimeLeft.kt
// Purpose: The honest time left of a job measured as it runs (a download's bytes, an export's percent), like studio's.

package io.github.skm0078.audora.core.progress

/**
 * Measures from the first amount it is given, not from zero, so a resumed download or export, or the set-up before
 * the first step, never counts as slow work. The speed is the average since then (steadier than the last step, and
 * a stall shows as a growing time), and nothing is claimed until [minMeasureMs] has passed with some work done: the
 * same "measuring, then a real figure" rule as studio mode (ADR-063). Work going backwards (a restarted step) starts
 * the measure again. Not thread-safe; pure, with the clock passed in.
 */
class WorkTimeLeft(private val minMeasureMs: Long) {
    private var startMs = NOT_STARTED
    private var startDone = 0L

    init {
        require(minMeasureMs > 0) { "minMeasureMs must be > 0, was $minMeasureMs" }
    }

    /**
     * Milliseconds still needed for [done] of [total] at [nowMs] (any monotonic clock); null while measuring, or when
     * the total is unknown.
     */
    fun update(nowMs: Long, done: Long, total: Long): Long? {
        if (total <= 0L) return null
        if (startMs == NOT_STARTED || done < startDone) {
            startMs = nowMs
            startDone = done
        }
        val elapsed = nowMs - startMs
        val made = done - startDone
        val measured = elapsed >= minMeasureMs && made > 0L
        return if (measured) ((total - done).coerceAtLeast(0L).toDouble() * elapsed / made).toLong() else null
    }

    /** A new job, or a new step measured on its own. */
    fun reset() {
        startMs = NOT_STARTED
        startDone = 0L
    }

    private companion object {
        const val NOT_STARTED = -1L
    }
}
