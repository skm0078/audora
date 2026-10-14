// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/device/ExitReasons.kt
// Purpose: Asks Android why our last process died, so an OEM kill is named in the report instead of guessed.

package io.github.skm0078.audora.spike.playback.device

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.text.DateFormat
import java.util.Date

object ExitReasons {
    private val reasonNames = mapOf(
        ApplicationExitInfo.REASON_EXIT_SELF to "EXIT_SELF",
        ApplicationExitInfo.REASON_SIGNALED to "SIGNALED (killed by a signal, often an OEM task killer)",
        ApplicationExitInfo.REASON_LOW_MEMORY to "LOW_MEMORY",
        ApplicationExitInfo.REASON_CRASH to "CRASH",
        ApplicationExitInfo.REASON_CRASH_NATIVE to "CRASH_NATIVE",
        ApplicationExitInfo.REASON_ANR to "ANR",
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to "INITIALIZATION_FAILURE",
        ApplicationExitInfo.REASON_PERMISSION_CHANGE to "PERMISSION_CHANGE",
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE to "EXCESSIVE_RESOURCE_USAGE",
        ApplicationExitInfo.REASON_USER_REQUESTED to "USER_REQUESTED (force stop or swipe-kill)",
        ApplicationExitInfo.REASON_USER_STOPPED to "USER_STOPPED",
        ApplicationExitInfo.REASON_DEPENDENCY_DIED to "DEPENDENCY_DIED",
        ApplicationExitInfo.REASON_OTHER to "OTHER",
        ApplicationExitInfo.REASON_FREEZER to "FREEZER",
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE to "PACKAGE_STATE_CHANGE",
        ApplicationExitInfo.REASON_PACKAGE_UPDATED to "PACKAGE_UPDATED",
    )

    /** The most recent recorded exit of this app, or null if Android has none. */
    fun lastExitSummary(context: Context): ExitSummary? = context.getSystemService(ActivityManager::class.java)
        ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
        ?.firstOrNull()
        ?.let { info ->
            val reason = reasonNames[info.reason] ?: "UNKNOWN(${info.reason})"
            val time = DateFormat.getDateTimeInstance().format(Date(info.timestamp))
            val description = info.description?.let { ", \"$it\"" }.orEmpty()
            ExitSummary(info.timestamp, "$reason at $time, importance ${info.importance}$description")
        }
}

data class ExitSummary(val timestampMs: Long, val text: String)
