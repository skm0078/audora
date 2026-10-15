// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/DiagnosticsConfig.kt
// Purpose: Tunables for process-exit logging and the shareable log bundle (P1-07, Build Spec §10).

package io.github.skm0078.audora.core.config

/**
 * @property maxExitRecords how many of Android's recorded process exits to read at startup. Android keeps
 *   a short per-app history (about 16 on current releases), so this reads all of it.
 * @property maxExitsInSummary how many recent exits the shared bundle's summary lists.
 * @property bundleBaseName file-name prefix of the shared zip; the timestamp follows it.
 */
data class DiagnosticsConfig(
    val maxExitRecords: Int = 16,
    val maxExitsInSummary: Int = 10,
    val bundleBaseName: String = "audora-logs",
) {
    init {
        require(maxExitRecords > 0) { "maxExitRecords must be > 0, was $maxExitRecords" }
        require(maxExitsInSummary >= 0) { "maxExitsInSummary must be >= 0, was $maxExitsInSummary" }
        require(bundleBaseName.isNotBlank() && '/' !in bundleBaseName) {
            "bundleBaseName must be a plain file name, was '$bundleBaseName'"
        }
    }
}
