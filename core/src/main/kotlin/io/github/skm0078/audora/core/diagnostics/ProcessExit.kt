// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/ProcessExit.kt
// Purpose: Why a previous Audora process died, as Android recorded it, in platform-free terms.

package io.github.skm0078.audora.core.diagnostics

/**
 * One past process death, mapped from Android's `ApplicationExitInfo` by the app module.
 *
 * @property status the signal number for [ProcessExitKind.SIGNALED], the exit code for
 *   [ProcessExitKind.EXIT_SELF], otherwise 0.
 * @property importance how visible the process was when it died, e.g. `FOREGROUND_SERVICE(125)`. A media
 *   service killed at foreground-service importance is the OEM-kill signature (Build Spec §9).
 * @property description Android's own free-text explanation, if any (no user content).
 */
data class ProcessExit(
    val timestampMillis: Long,
    val kind: ProcessExitKind,
    val status: Int,
    val importance: String,
    val processName: String,
    val description: String?,
    val pssKb: Long,
    val rssKb: Long,
)

/** [expected] exits are ones the user or an update caused; the rest are logged as warnings. */
enum class ProcessExitKind(val expected: Boolean) {
    EXIT_SELF(expected = true),
    SIGNALED(expected = false),
    LOW_MEMORY(expected = false),
    CRASH(expected = false),
    CRASH_NATIVE(expected = false),
    ANR(expected = false),
    INITIALIZATION_FAILURE(expected = false),
    PERMISSION_CHANGE(expected = true),
    EXCESSIVE_RESOURCE_USAGE(expected = false),
    USER_REQUESTED(expected = true),
    USER_STOPPED(expected = true),
    DEPENDENCY_DIED(expected = false),
    OTHER(expected = false),
    FREEZER(expected = false),
    PACKAGE_STATE_CHANGE(expected = true),
    PACKAGE_UPDATED(expected = true),
    UNKNOWN(expected = false),
}

/** Source of past exits; the Android implementation reads `ActivityManager.getHistoricalProcessExitReasons`. */
fun interface ProcessExitSource {
    /** Most recent first. Returns an empty list (and logs) when the history cannot be read. */
    fun recentExits(): List<ProcessExit>
}
