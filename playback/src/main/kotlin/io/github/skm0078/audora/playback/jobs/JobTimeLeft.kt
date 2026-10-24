// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/jobs/JobTimeLeft.kt
// Purpose: An export's or a copy's progress line with its honest time left, worded like studio mode's (ADR-063).

package io.github.skm0078.audora.playback.jobs

import android.content.Context
import android.os.SystemClock
import io.github.skm0078.audora.core.config.WorkTimeLeftConfig
import io.github.skm0078.audora.core.progress.WorkTimeLeft
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.studio.studioDuration

/**
 * One per job's notifications: "Chapter 2 of 9 · 14% · about 25 min left" once the speed is measured, "… · measuring
 * this phone's speed…" before. The notification and the app's job card show the same line. Fed from the job's thread.
 */
internal class JobTimeLeft(private val context: Context, config: WorkTimeLeftConfig = WorkTimeLeftConfig()) {
    private val estimate = WorkTimeLeft(config.jobMeasureMs)

    /** A new job, or a step whose percent starts again from zero. */
    fun reset() = estimate.reset()

    fun line(text: String, percent: Int): String =
        when (val left = estimate.update(SystemClock.elapsedRealtime(), percent.toLong(), PERCENT)) {
            null -> context.getString(R.string.studio_measuring, text)
            else -> context.getString(R.string.studio_time_left, text, context.studioDuration(left))
        }

    private companion object {
        const val PERCENT = 100L
    }
}
