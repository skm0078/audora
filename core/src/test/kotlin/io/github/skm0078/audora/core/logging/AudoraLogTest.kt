// File: core/src/test/kotlin/io/github/skm0078/audora/core/logging/AudoraLogTest.kt
// Purpose: Verifies the logging facade fans out to sinks and isolates a failing sink.

package io.github.skm0078.audora.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AudoraLogTest {
    @After
    fun tearDown() = AudoraLog.install()

    @Test
    fun `delivers each entry to every installed sink`() {
        val first = mutableListOf<LogEntry>()
        val second = mutableListOf<LogEntry>()
        AudoraLog.install(LogSink { first += it }, LogSink { second += it }, clock = { 42L })

        AudoraLog.i("Reader", "page 3 extracted")

        val expected = LogEntry(42L, LogLevel.INFO, "Reader", "page 3 extracted", Thread.currentThread().name)
        assertEquals(listOf(expected), first)
        assertEquals(listOf(expected), second)
    }

    @Test
    fun `keeps the throwable for warnings and errors`() {
        val captured = mutableListOf<LogEntry>()
        AudoraLog.install(LogSink { captured += it })
        val failure = IllegalStateException("boom")

        AudoraLog.e("Engine", "render failed", failure)

        assertEquals(LogLevel.ERROR, captured.single().level)
        assertSame(failure, captured.single().throwable)
    }

    @Test
    fun `a throwing sink does not stop the others`() {
        val captured = mutableListOf<LogEntry>()
        AudoraLog.install(LogSink { error("disk full") }, LogSink { captured += it })

        AudoraLog.w("Queue", "slow render")

        assertEquals(1, captured.size)
    }

    @Test
    fun `drops entries when no sink is installed`() {
        AudoraLog.install()
        AudoraLog.d("Any", "ignored")
    }
}
