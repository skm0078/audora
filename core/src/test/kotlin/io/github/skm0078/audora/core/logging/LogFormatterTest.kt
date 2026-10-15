// File: core/src/test/kotlin/io/github/skm0078/audora/core/logging/LogFormatterTest.kt
// Purpose: Pins the log line format that "Share logs" readers (and humans) rely on.

package io.github.skm0078.audora.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFormatterTest {
    @Test
    fun `formats timestamp in UTC with level, tag and thread`() {
        val entry = LogEntry(1_760_469_940_123L, LogLevel.WARN, "Playback", "buffering", "main")

        assertEquals("2025-10-14T19:25:40.123Z W/Playback [main]: buffering\n", LogFormatter.format(entry))
    }

    @Test
    fun `appends the stack trace when a throwable is present`() {
        val entry = LogEntry(0L, LogLevel.ERROR, "Pdf", "failed", "worker", IllegalArgumentException("bad page"))

        val text = LogFormatter.format(entry)

        assertTrue(text.startsWith("1970-01-01T00:00:00.000Z E/Pdf [worker]: failed\n"))
        assertTrue(text.contains("java.lang.IllegalArgumentException: bad page"))
        assertTrue(text.contains("at io.github.skm0078.audora.core.logging.LogFormatterTest"))
    }
}
