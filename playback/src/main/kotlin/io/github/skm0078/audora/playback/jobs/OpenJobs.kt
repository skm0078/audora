// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/jobs/OpenJobs.kt
// Purpose: A long job's notification opens the app's Jobs screen (ADR-067), where its progress, time left and
//          Cancel are, instead of whichever screen the app was last on.

package io.github.skm0078.audora.playback.jobs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.playback.contract.PendingIntentCodes

/**
 * :playback does not know the app's activity; the launcher does. The app reads [EXTRA_OPEN_JOBS] when it starts or
 * is brought back with a new intent ([opensJobs]) and goes to its Jobs screen.
 */
object OpenJobs {
    const val EXTRA_OPEN_JOBS = "io.github.skm0078.audora.OPEN_JOBS"

    /** Tapping a job's notification: the app, on its Jobs screen; null when the launcher has no intent for it. */
    fun pendingIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            // Its own code: launch intents differ only in extras, which PendingIntent matching ignores.
            PendingIntentCodes.OPEN_JOBS,
            launch.putExtra(EXTRA_OPEN_JOBS, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Whether [intent] came from a job's notification. */
    fun opensJobs(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_OPEN_JOBS, false) == true

    /** Tapping the saved-audio player's notification: the app as it was. */
    fun appIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            PendingIntentCodes.OPEN_FROM_MADE_AUDIO,
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
