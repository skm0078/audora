// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/VoiceDownloadConfig.kt
// Purpose: Tunables for the voice manager (P4-01): network timeouts, retries, progress cadence and import limits.

package io.github.skm0078.audora.core.config

/**
 * @property connectTimeoutMs how long to wait for the model host to accept a connection.
 * @property readTimeoutMs how long a read may stall before the attempt counts as a network failure. The bytes already
 *   written stay on disk and the next attempt resumes from them.
 * @property maxAttempts download attempts (WorkManager runs) before a network failure is shown as final. Each attempt
 *   resumes where the last one stopped, so a flaky connection still finishes.
 * @property retryBackoffMs first WorkManager backoff delay; it grows exponentially.
 * @property progressStepBytes how often progress is reported while bytes arrive.
 * @property bufferBytes copy buffer for downloads, unpacking and imports.
 * @property installHeadroomBytes free space kept beyond the download and its unpacked copy, so a download never
 *   fills the phone.
 * @property maxUnpackedBytes refuse an archive or imported folder that unpacks to more than this (a zip bomb, or the
 *   wrong folder picked).
 * @property maxImportFiles refuse an imported folder with more files than this; a sherpa-onnx voice with
 *   espeak-ng-data has about 400.
 * @property maxImportDepth how deep the import looks for a voice inside the picked folder.
 * @property maxLabelChars longest name a user can give an imported voice.
 * @property stopWaitMs how long a model delete waits for a download it cancelled to stop, so the worker cannot
 *   install into the folder being deleted.
 */
data class VoiceDownloadConfig(
    val connectTimeoutMs: Int = 15_000,
    val readTimeoutMs: Int = 30_000,
    val maxAttempts: Int = 8,
    val retryBackoffMs: Long = 30_000,
    val progressStepBytes: Long = DEFAULT_PROGRESS_STEP_BYTES,
    val bufferBytes: Int = DEFAULT_BUFFER_BYTES,
    val installHeadroomBytes: Long = DEFAULT_INSTALL_HEADROOM_BYTES,
    val maxUnpackedBytes: Long = DEFAULT_MAX_UNPACKED_BYTES,
    val maxImportFiles: Int = 2_000,
    val maxImportDepth: Int = 4,
    val maxLabelChars: Int = 60,
    val stopWaitMs: Long = 10_000,
) {
    init {
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be > 0, was $connectTimeoutMs" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be > 0, was $readTimeoutMs" }
        require(maxAttempts > 0) { "maxAttempts must be > 0, was $maxAttempts" }
        require(retryBackoffMs > 0) { "retryBackoffMs must be > 0, was $retryBackoffMs" }
        require(stopWaitMs >= 0) { "stopWaitMs must be >= 0, was $stopWaitMs" }
        require(progressStepBytes > 0) { "progressStepBytes must be > 0, was $progressStepBytes" }
        require(bufferBytes > 0) { "bufferBytes must be > 0, was $bufferBytes" }
        require(installHeadroomBytes >= 0) { "installHeadroomBytes must be >= 0, was $installHeadroomBytes" }
        require(maxUnpackedBytes > 0) { "maxUnpackedBytes must be > 0, was $maxUnpackedBytes" }
        require(maxImportFiles > 0) { "maxImportFiles must be > 0, was $maxImportFiles" }
        require(maxImportDepth > 0) { "maxImportDepth must be > 0, was $maxImportDepth" }
        require(maxLabelChars > 0) { "maxLabelChars must be > 0, was $maxLabelChars" }
    }

    companion object {
        const val DEFAULT_PROGRESS_STEP_BYTES = 512L shl 10 // 512 KiB
        const val DEFAULT_BUFFER_BYTES = 64 shl 10 // 64 KiB
        const val DEFAULT_INSTALL_HEADROOM_BYTES = 50L shl 20 // 50 MiB
        const val DEFAULT_MAX_UNPACKED_BYTES = 1L shl 30 // 1 GiB
    }
}
