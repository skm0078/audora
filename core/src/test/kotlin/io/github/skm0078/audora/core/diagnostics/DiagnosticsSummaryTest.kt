// File: core/src/test/kotlin/io/github/skm0078/audora/core/diagnostics/DiagnosticsSummaryTest.kt
// Purpose: The shared summary lists build, phone, unfinished read and exits.

package io.github.skm0078.audora.core.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsSummaryTest {
    private val build =
        BuildAndDevice("io.github.skm0078.audora.debug", "0.0.1-debug", 1, "Google", "Pixel 8", "15", 35)

    @Test
    fun `lists build, phone, session and exits`() {
        val text = DiagnosticsSummary.render(
            createdAtMillis = 0,
            build = build,
            exits = listOf(exit(1_000, ProcessExitKind.SIGNALED, description = "killed by task manager")),
            unfinished = ReadingSession("book-1", 14, 500),
        )

        assertTrue("App: io.github.skm0078.audora.debug 0.0.1-debug (1)" in text)
        assertTrue("Phone: Google Pixel 8, Android 15 (SDK 35)" in text)
        assertTrue("book=book-1 page=14" in text)
        assertTrue("SIGNALED status=9 at 1970-01-01T00:00:01.000Z" in text)
        assertTrue("description=\"killed by task manager\"" in text)
    }

    @Test
    fun `says none when nothing happened`() {
        val text = DiagnosticsSummary.render(0, build, emptyList(), null)

        assertTrue("  none\n" in text)
        assertTrue("  none recorded" in text)
    }
}
