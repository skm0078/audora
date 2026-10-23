// File: app/src/main/kotlin/io/github/skm0078/audora/ui/jobs/LongJobStarts.kt
// Purpose: A long job asked for while one of its kind still runs is answered in the app at once, not only by a
//          "busy" notification in the shade.

package io.github.skm0078.audora.ui.jobs

import android.content.Context
import android.widget.Toast
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs

/** Runs [start] unless a job of [kind] runs; then a toast says so and where to cancel it. */
internal fun Context.startLongJob(kind: LongJobKind, start: () -> Unit) {
    if (LongJobs.busy(kind)) {
        AudoraLog.i(TAG, "Not started: a $kind job still runs")
        Toast.makeText(this, R.string.long_job_busy, Toast.LENGTH_LONG).show()
    } else {
        start()
    }
}

private const val TAG = "Audora.LongJobs"
