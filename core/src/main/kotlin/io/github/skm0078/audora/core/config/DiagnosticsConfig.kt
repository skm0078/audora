// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/DiagnosticsConfig.kt
// Purpose: Tunables for process-exit logging and the shareable log bundle (P1-07, Build Spec §10).

package io.github.skm0078.audora.core.config

/**
 * @property maxExitRecords how many of Android's recorded process exits to read at startup. Android keeps
 *   a short per-app history (about 16 on current releases), so this reads all of it.
 * @property maxExitsInSummary how many recent exits the shared bundle's summary lists.
 * @property bundleBaseName file-name prefix of the shared zip; the timestamp follows it.
 * @property stallAfterMs the stall watchdog (debug builds) logs the main thread as stuck once it has not answered
 *   for this long: well under Android's 5 s "isn't responding" dialog, well over a slow frame.
 * @property stallCheckEveryMs how often the watchdog asks the main thread to answer while it is healthy.
 * @property stallStackFrames how many frames of the stuck main thread's stack the log line carries.
 */
data class DiagnosticsConfig(
    val maxExitRecords: Int = 16,
    val maxExitsInSummary: Int = 10,
    val bundleBaseName: String = "audora-logs",
    val stallAfterMs: Long = 2_000L,
    val stallCheckEveryMs: Long = 1_000L,
    val stallStackFrames: Int = 20,
) {
    init {
        require(stallAfterMs > 0 && stallCheckEveryMs > 0) { "stall timings must be > 0" }
        require(stallStackFrames > 0) { "stallStackFrames must be > 0, was $stallStackFrames" }
        require(maxExitRecords > 0) { "maxExitRecords must be > 0, was $maxExitRecords" }
        require(maxExitsInSummary >= 0) { "maxExitsInSummary must be >= 0, was $maxExitsInSummary" }
        require(bundleBaseName.isNotBlank() && '/' !in bundleBaseName) {
            "bundleBaseName must be a plain file name, was '$bundleBaseName'"
        }
    }
}
