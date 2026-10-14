// File: core/src/test/kotlin/io/github/skm0078/audora/core/logging/LogFileConfigTest.kt
// Purpose: Verifies log file limits are validated and file names are stable.

package io.github.skm0078.audora.core.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class LogFileConfigTest {
    @Test
    fun `defaults match the spec of 2 MB times 3 files`() {
        val config = LogFileConfig()

        assertEquals(2L * 1024 * 1024, config.maxFileBytes)
        assertEquals(3, config.maxFiles)
    }

    @Test
    fun `names the current file without an index and older files with one`() {
        val config = LogFileConfig()

        assertEquals("audora.log", config.fileName(0))
        assertEquals("audora.2.log", config.fileName(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a non-positive size`() {
        LogFileConfig(maxFileBytes = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero files`() {
        LogFileConfig(maxFiles = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a path as base name`() {
        LogFileConfig(baseName = "../escape")
    }
}
