// File: core/src/main/kotlin/io/github/skm0078/audora/core/logging/LogEntry.kt
// Purpose: One immutable log record, created once and handed to every sink.

package io.github.skm0078.audora.core.logging

data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val threadName: String,
    val throwable: Throwable? = null,
)
