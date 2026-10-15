// File: app/src/main/kotlin/io/github/skm0078/audora/diagnostics/AndroidProcessExitSource.kt
// Purpose: Reads Android's record of how previous Audora processes died and maps it to :core's ProcessExit.

package io.github.skm0078.audora.diagnostics

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.skm0078.audora.core.config.DiagnosticsConfig
import io.github.skm0078.audora.core.diagnostics.ProcessExit
import io.github.skm0078.audora.core.diagnostics.ProcessExitKind
import io.github.skm0078.audora.core.diagnostics.ProcessExitSource
import io.github.skm0078.audora.core.logging.AudoraLog
import javax.inject.Inject

class AndroidProcessExitSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: DiagnosticsConfig,
) : ProcessExitSource {
    override fun recentExits(): List<ProcessExit> {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        if (activityManager == null) {
            AudoraLog.w(TAG, "ActivityManager unavailable; process exits not read")
            return emptyList()
        }
        return try {
            activityManager.getHistoricalProcessExitReasons(context.packageName, ALL_PIDS, config.maxExitRecords)
                .map(::toProcessExit)
        } catch (e: SecurityException) {
            AudoraLog.e(TAG, "Not allowed to read process exits for ${context.packageName}", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            AudoraLog.e(TAG, "Could not read process exits for ${context.packageName}", e)
            emptyList()
        }
    }

    private fun toProcessExit(info: ApplicationExitInfo) = ProcessExit(
        timestampMillis = info.timestamp,
        kind = ExitInfoMapping.kindOf(info.reason),
        status = info.status,
        importance = ExitInfoMapping.importanceLabel(info.importance),
        processName = info.processName,
        description = info.description,
        pssKb = info.pss,
        rssKb = info.rss,
    )

    private companion object {
        const val TAG = "ProcessExits"

        /** Android's "every process of this package" pid filter. */
        const val ALL_PIDS = 0
    }
}

/** Platform constants → :core names. Kept separate so a JVM unit test covers it (the constants are inlined). */
internal object ExitInfoMapping {
    private val kinds = mapOf(
        ApplicationExitInfo.REASON_EXIT_SELF to ProcessExitKind.EXIT_SELF,
        ApplicationExitInfo.REASON_SIGNALED to ProcessExitKind.SIGNALED,
        ApplicationExitInfo.REASON_LOW_MEMORY to ProcessExitKind.LOW_MEMORY,
        ApplicationExitInfo.REASON_CRASH to ProcessExitKind.CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE to ProcessExitKind.CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR to ProcessExitKind.ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to ProcessExitKind.INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE to ProcessExitKind.PERMISSION_CHANGE,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE to ProcessExitKind.EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_USER_REQUESTED to ProcessExitKind.USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED to ProcessExitKind.USER_STOPPED,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED to ProcessExitKind.DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_OTHER to ProcessExitKind.OTHER,
        ApplicationExitInfo.REASON_FREEZER to ProcessExitKind.FREEZER,
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE to ProcessExitKind.PACKAGE_STATE_CHANGE,
        ApplicationExitInfo.REASON_PACKAGE_UPDATED to ProcessExitKind.PACKAGE_UPDATED,
    )

    private val importanceNames = mapOf(
        RunningAppProcessInfo.IMPORTANCE_FOREGROUND to "FOREGROUND",
        RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE to "FOREGROUND_SERVICE",
        RunningAppProcessInfo.IMPORTANCE_VISIBLE to "VISIBLE",
        RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE to "PERCEPTIBLE",
        RunningAppProcessInfo.IMPORTANCE_SERVICE to "SERVICE",
        RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE to "CANT_SAVE_STATE",
        RunningAppProcessInfo.IMPORTANCE_CACHED to "CACHED",
        RunningAppProcessInfo.IMPORTANCE_GONE to "GONE",
    )

    fun kindOf(reason: Int): ProcessExitKind = kinds[reason] ?: ProcessExitKind.UNKNOWN

    /** e.g. `FOREGROUND_SERVICE(125)`: the name for the common levels, always the raw number. */
    fun importanceLabel(importance: Int): String = "${importanceNames[importance] ?: "OTHER"}($importance)"
}
