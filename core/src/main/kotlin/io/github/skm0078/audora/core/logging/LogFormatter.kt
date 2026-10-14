// File: core/src/main/kotlin/io/github/skm0078/audora/core/logging/LogFormatter.kt
// Purpose: Turns a LogEntry into the plain-text line format written to log files.

package io.github.skm0078.audora.core.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Format: `2026-10-14T19:25:40.123Z I/Tag [thread]: message`, followed by a stack trace if any. */
object LogFormatter {
    private val timestampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun format(entry: LogEntry): String = buildString {
        append(timestampFormat.format(Instant.ofEpochMilli(entry.timestampMillis)))
        append(' ').append(entry.level.code).append('/').append(entry.tag)
        append(" [").append(entry.threadName).append("]: ")
        append(entry.message)
        append('\n')
        entry.throwable?.let { append(stackTraceOf(it)) }
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString()
    }
}
