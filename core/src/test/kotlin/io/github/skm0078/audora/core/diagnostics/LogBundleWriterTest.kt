// File: core/src/test/kotlin/io/github/skm0078/audora/core/diagnostics/LogBundleWriterTest.kt
// Purpose: The shared zip holds the summary then the logs, skips vanished files, and fails cleanly.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogBundleWriterTest {
    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun tearDown() = AudoraLog.install()

    private fun entries(zip: File): Map<String, String> = ZipFile(zip).use { file ->
        file.entries().toList().associate { it.name to file.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
    }

    @Test
    fun `zip holds summary first then each log file`() {
        val logs = temp.newFolder("logs")
        val older = File(logs, "audora.1.log").apply { writeText("old line\n") }
        val current = File(logs, "audora.log").apply { writeText("new line\n") }
        val target = File(temp.root, "share/audora-logs.zip")

        val result = LogBundleWriter().write(target, "summary text", listOf(older, current))

        assertEquals(Outcome.Success(target), result)
        val contents = entries(target)
        assertEquals(listOf("summary.txt", "audora.1.log", "audora.log"), contents.keys.toList())
        assertEquals("summary text", contents["summary.txt"])
        assertEquals("new line\n", contents["audora.log"])
        assertFalse(File(target.parentFile, "audora-logs.zip.tmp").exists())
    }

    @Test
    fun `missing log file is skipped`() {
        val target = File(temp.root, "bundle.zip")

        LogBundleWriter().write(target, "s", listOf(File(temp.root, "gone.log")))

        assertEquals(listOf("summary.txt"), entries(target).keys.toList())
    }

    @Test
    fun `unwritable target fails without leaving files`() {
        val blocker = temp.newFile("blocker")

        val result = LogBundleWriter().write(File(blocker, "bundle.zip"), "s", emptyList())

        assertTrue(result is Outcome.Failure)
    }
}
