// File: core/src/test/kotlin/io/github/skm0078/audora/core/diagnostics/StartupDiagnosticsTest.kt
// Purpose: Each process death is logged exactly once, at the right level, and an unfinished read names its cause.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogEntry
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.LogSink
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StartupDiagnosticsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val logged = mutableListOf<LogEntry>()
    private lateinit var file: File
    private var exits: List<ProcessExit> = emptyList()

    @Before
    fun setUp() {
        AudoraLog.install(LogSink { logged += it })
        file = File(temp.root, "state.properties")
    }

    @After
    fun tearDown() = AudoraLog.install()

    private fun startProcess(): StartupReport {
        val store = DiagnosticsStateStore(file)
        return StartupDiagnostics({ exits }, store, ReadingSessionTracker(store) { 0L }).run()
    }

    private fun exitLines() = logged.filter { "Previous process ended" in it.message }

    @Test
    fun `logs new exits oldest first, warnings for abnormal ones`() {
        exits = listOf(exit(300, ProcessExitKind.USER_REQUESTED), exit(200, ProcessExitKind.SIGNALED))

        val report = startProcess()

        assertEquals(listOf(200L, 300L), report.newExits.map { it.timestampMillis })
        assertEquals(listOf(LogLevel.WARN, LogLevel.INFO), exitLines().map { it.level })
        assertTrue("SIGNALED status=9" in exitLines().first().message)
        assertTrue("importance=FOREGROUND_SERVICE(125)" in exitLines().first().message)
    }

    @Test
    fun `an exit is logged only once across restarts`() {
        exits = listOf(exit(200))
        startProcess()
        exits = listOf(exit(500, ProcessExitKind.ANR), exit(200))
        logged.clear()

        val report = startProcess()

        assertEquals(listOf(500L), report.newExits.map { it.timestampMillis })
        assertEquals(1, exitLines().size)
    }

    @Test
    fun `no exits and no session logs nothing`() {
        val report = startProcess()

        assertEquals(StartupReport(emptyList(), null, null), report)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `unfinished read is reported with the exit that ended it`() {
        val store = DiagnosticsStateStore(file)
        ReadingSessionTracker(store) { 1_000L }.onPlaybackActive("book-1", 14)
        exits = listOf(exit(2_000, ProcessExitKind.SIGNALED), exit(500, ProcessExitKind.CRASH))

        val report = startProcess()

        assertEquals(ReadingSession("book-1", 14, 1_000L), report.unfinishedSession)
        assertEquals(2_000L, report.likelyCause?.timestampMillis)
        val warning = logged.single { "Reading stopped unexpectedly" in it.message }
        assertEquals(LogLevel.WARN, warning.level)
        assertTrue("book=book-1 page=14" in warning.message)
        assertTrue("cause: SIGNALED" in warning.message)
    }

    @Test
    fun `unfinished read without a recorded exit says the cause is unknown`() {
        ReadingSessionTracker(DiagnosticsStateStore(file)) { 1_000L }.onPlaybackActive("book-1", 0)
        exits = listOf(exit(500))

        val report = startProcess()

        assertNull(report.likelyCause)
        assertTrue(logged.any { "cause: unknown" in it.message })
    }
}
