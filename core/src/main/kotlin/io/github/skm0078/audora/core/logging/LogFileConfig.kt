// File: core/src/main/kotlin/io/github/skm0078/audora/core/logging/LogFileConfig.kt
// Purpose: Size and naming limits for the rolling log file (Build Spec §10: 2 MB × 3).

package io.github.skm0078.audora.core.logging

data class LogFileConfig(
    val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    val maxFiles: Int = DEFAULT_MAX_FILES,
    val baseName: String = DEFAULT_BASE_NAME,
) {
    init {
        require(maxFileBytes > 0) { "maxFileBytes must be positive, was $maxFileBytes" }
        require(maxFiles >= 1) { "maxFiles must be at least 1, was $maxFiles" }
        require(baseName.isNotBlank() && '/' !in baseName) { "baseName must be a plain file name, was '$baseName'" }
    }

    /** Index 0 is the file being written; higher indexes are older. */
    fun fileName(index: Int): String = if (index == 0) "$baseName.log" else "$baseName.$index.log"

    companion object {
        const val DEFAULT_MAX_FILE_BYTES: Long = 2L * 1024 * 1024
        const val DEFAULT_MAX_FILES: Int = 3
        const val DEFAULT_BASE_NAME: String = "audora"
    }
}
