// File: core/src/test/kotlin/io/github/skm0078/audora/core/logging/RollingFileLogSinkTest.kt
// Purpose: Verifies the rolling log file writes, rotates, and caps the number of files.

package io.github.skm0078.audora.core.logging

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingFileLogSinkTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun entry(message: String) = LogEntry(
        timestampMillis = 0L,
        level = LogLevel.INFO,
        tag = "Test",
        message = message,
        threadName = "main",
    )

    @Test
    fun `writes formatted entries to the current file`() {
        val dir = temp.newFolder("logs")
        RollingFileLogSink(dir).use { sink ->
            sink.write(entry("first"))
            sink.write(entry("second"))
        }

        val lines = File(dir, "audora.log").readLines()
        assertEquals(
            listOf(
                "1970-01-01T00:00:00.000Z I/Test [main]: first",
                "1970-01-01T00:00:00.000Z I/Test [main]: second",
            ),
            lines,
        )
    }

    @Test
    fun `creates the log directory when missing`() {
        val dir = File(temp.root, "nested/logs")
        RollingFileLogSink(dir).use { it.write(entry("hello")) }

        assertTrue(File(dir, "audora.log").exists())
    }

    @Test
    fun `rotates when the next entry would exceed the size cap`() {
        val dir = temp.newFolder("logs")
        val lineBytes = LogFormatter.format(entry("x".repeat(10))).toByteArray().size.toLong()
        val config = LogFileConfig(maxFileBytes = lineBytes * 2, maxFiles = 3)

        RollingFileLogSink(dir, config).use { sink ->
            repeat(3) { sink.write(entry("x".repeat(10))) }
        }

        assertEquals(1, File(dir, "audora.log").readLines().size)
        assertEquals(2, File(dir, "audora.1.log").readLines().size)
        assertFalse(File(dir, "audora.2.log").exists())
    }

    @Test
    fun `never keeps more than maxFiles files and drops the oldest`() {
        val dir = temp.newFolder("logs")
        val lineBytes = LogFormatter.format(entry("0")).toByteArray().size.toLong()
        val config = LogFileConfig(maxFileBytes = lineBytes, maxFiles = 3)

        RollingFileLogSink(dir, config).use { sink ->
            (0..5).forEach { sink.write(entry(it.toString())) }
        }

        assertEquals(setOf("audora.log", "audora.1.log", "audora.2.log"), dir.list()!!.toSet())
        assertTrue(File(dir, "audora.log").readText().endsWith(": 5\n"))
        assertTrue(File(dir, "audora.2.log").readText().endsWith(": 3\n"))
    }

    @Test
    fun `continues an existing file after restart and counts its size`() {
        val dir = temp.newFolder("logs")
        val lineBytes = LogFormatter.format(entry("a")).toByteArray().size.toLong()
        val config = LogFileConfig(maxFileBytes = lineBytes, maxFiles = 2)

        RollingFileLogSink(dir, config).use { it.write(entry("a")) }
        RollingFileLogSink(dir, config).use { it.write(entry("b")) }

        assertTrue(File(dir, "audora.1.log").readText().endsWith(": a\n"))
        assertTrue(File(dir, "audora.log").readText().endsWith(": b\n"))
    }

    @Test
    fun `lists log files oldest first`() {
        val dir = temp.newFolder("logs")
        val lineBytes = LogFormatter.format(entry("a")).toByteArray().size.toLong()
        val sink = RollingFileLogSink(dir, LogFileConfig(maxFileBytes = lineBytes, maxFiles = 3))

        sink.use { repeat(3) { _ -> it.write(entry("a")) } }

        assertEquals(listOf("audora.2.log", "audora.1.log", "audora.log"), sink.logFiles().map { it.name })
    }

    @Test
    fun `an unwritable directory drops the entry instead of throwing`() {
        val notADirectory = temp.newFile("occupied")
        RollingFileLogSink(File(notADirectory, "logs")).use { it.write(entry("lost")) }
    }
}
