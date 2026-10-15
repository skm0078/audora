// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/DiagnosticsText.kt
// Purpose: One-line, log-safe descriptions of exits and sessions, shared by the log and the shared summary.

package io.github.skm0078.audora.core.diagnostics

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DiagnosticsText {
    private val timestampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun timestamp(millis: Long): String = timestampFormat.format(Instant.ofEpochMilli(millis))

    /** e.g. `SIGNALED status=9 at 2026-10-15T21:04:11.000Z importance=FOREGROUND_SERVICE(125) ...` */
    fun describe(exit: ProcessExit): String = buildString {
        append(exit.kind.name)
        append(" status=").append(exit.status)
        append(" at ").append(timestamp(exit.timestampMillis))
        append(" importance=").append(exit.importance)
        append(" process=").append(exit.processName)
        append(" pssKb=").append(exit.pssKb)
        append(" rssKb=").append(exit.rssKb)
        exit.description?.takeIf { it.isNotBlank() }?.let { append(" description=\"").append(it).append('"') }
    }

    fun describe(session: ReadingSession): String =
        "book=${session.bookId} page=${session.page} started=${timestamp(session.startedAtMillis)}"
}
