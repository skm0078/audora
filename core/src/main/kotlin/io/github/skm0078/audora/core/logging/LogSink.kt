// File: core/src/main/kotlin/io/github/skm0078/audora/core/logging/LogSink.kt
// Purpose: Destination for log entries (logcat, rolling file, test capture).

package io.github.skm0078.audora.core.logging

fun interface LogSink {
    /** Must not throw for ordinary I/O problems; [AudoraLog] reports any exception to stderr. */
    fun write(entry: LogEntry)
}
