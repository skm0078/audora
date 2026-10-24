// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/WorkTimeLeftConfig.kt
// Purpose: Tunables for the time left of a long job other than studio mode: downloads, exports and copies.

package io.github.skm0078.audora.core.config

/**
 * @property downloadMeasureMs how long a download is watched before its time left is shown: a few seconds of
 *   transfer are enough to know the connection, and the first second (connecting) would make it look far slower.
 * @property jobMeasureMs the same for an export or a copy, which report in steps of a few percent: long enough for
 *   two steps on a short book, so the first page's set-up (opening the book, loading the voice) does not count alone.
 */
data class WorkTimeLeftConfig(
    val downloadMeasureMs: Long = DEFAULT_DOWNLOAD_MEASURE_MS,
    val jobMeasureMs: Long = DEFAULT_JOB_MEASURE_MS,
) {
    init {
        require(downloadMeasureMs > 0) { "downloadMeasureMs must be > 0, was $downloadMeasureMs" }
        require(jobMeasureMs > 0) { "jobMeasureMs must be > 0, was $jobMeasureMs" }
    }

    private companion object {
        const val DEFAULT_DOWNLOAD_MEASURE_MS = 4_000L
        const val DEFAULT_JOB_MEASURE_MS = 20_000L
    }
}
