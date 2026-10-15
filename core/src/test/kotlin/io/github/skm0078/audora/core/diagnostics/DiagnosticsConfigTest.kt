// File: core/src/test/kotlin/io/github/skm0078/audora/core/diagnostics/DiagnosticsConfigTest.kt
// Purpose: DiagnosticsConfig and ReadingSession reject values that would break exit logging or the shared zip.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.config.DiagnosticsConfig
import org.junit.Test

class DiagnosticsConfigTest {
    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero exit records`() {
        DiagnosticsConfig(maxExitRecords = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a bundle name with a path`() {
        DiagnosticsConfig(bundleBaseName = "../logs")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a negative page`() {
        ReadingSession("book", -1, 0)
    }
}
