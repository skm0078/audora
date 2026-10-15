// File: core/src/test/kotlin/io/github/skm0078/audora/core/diagnostics/DiagnosticsStateStoreTest.kt
// Purpose: The diagnostics state file round-trips, tolerates damage, and is replaced atomically.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogEntry
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.LogSink
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsStateStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val logged = mutableListOf<LogEntry>()
    private lateinit var file: File
    private lateinit var store: DiagnosticsStateStore

    @Before
    fun setUp() {
        AudoraLog.install(LogSink { logged += it })
        file = File(temp.root, "diagnostics/state.properties")
        store = DiagnosticsStateStore(file)
    }

    @After
    fun tearDown() = AudoraLog.install()

    @Test
    fun `missing file means no session and no logged exit`() {
        assertNull(store.readSession())
        assertEquals(0L, store.lastLoggedExitMillis())
    }

    @Test
    fun `session round-trips through a fresh store and can be cleared`() {
        val session = ReadingSession("book-7", 14, 1_000L)

        assertEquals(Outcome.Success(Unit), store.writeSession(session))
        assertEquals(session, DiagnosticsStateStore(file).readSession())

        store.writeSession(null)
        assertNull(DiagnosticsStateStore(file).readSession())
    }

    @Test
    fun `session and last exit are independent keys`() {
        store.setLastLoggedExitMillis(500L)
        store.writeSession(ReadingSession("b", 0, 1L))
        store.writeSession(null)

        assertEquals(500L, DiagnosticsStateStore(file).lastLoggedExitMillis())
    }

    @Test
    fun `leaves no temp file behind`() {
        store.writeSession(ReadingSession("b", 1, 2L))

        assertEquals(listOf("state.properties"), file.parentFile.list()!!.toList())
    }

    @Test
    fun `a stale temp file from a killed write does not matter`() {
        store.writeSession(ReadingSession("b", 1, 2L))
        File(file.parentFile, "state.properties.tmp").writeText("half writ")

        assertEquals(ReadingSession("b", 1, 2L), DiagnosticsStateStore(file).readSession())
        store.writeSession(ReadingSession("b", 2, 2L))
        assertEquals(2, DiagnosticsStateStore(file).readSession()?.page)
    }

    @Test
    fun `malformed session is ignored and logged`() {
        file.parentFile.mkdirs()
        file.writeText("session.bookId=b\nsession.page=minus\nsession.startedAtMillis=5\n")

        assertNull(store.readSession())
        assertTrue(logged.any { it.level == LogLevel.WARN && "malformed" in it.message })
    }

    @Test
    fun `corrupt escape sequence reads as empty state`() {
        file.parentFile.mkdirs()
        file.writeText("session.bookId=\\uZZZZ\n")

        assertNull(store.readSession())
        assertTrue(logged.any { it.level == LogLevel.WARN })
    }

    @Test
    fun `unwritable location fails with a typed error and logs it`() {
        val blocker = temp.newFile("not-a-dir")
        val broken = DiagnosticsStateStore(File(blocker, "state.properties"))

        val result = broken.writeSession(ReadingSession("b", 0, 0L))

        assertTrue(result is Outcome.Failure)
        assertTrue(logged.any { it.level == LogLevel.ERROR })
        assertFalse(File(blocker, "state.properties").exists())
    }
}
