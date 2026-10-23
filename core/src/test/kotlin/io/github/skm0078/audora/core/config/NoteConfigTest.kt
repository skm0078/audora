// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/NoteConfigTest.kt
// Purpose: NoteConfig's defaults are valid and out-of-range tunables are rejected at construction.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Test

class NoteConfigTest {
    @Test
    fun `defaults are valid`() {
        NoteConfig()
        LayoutConfig()
    }

    @Test
    fun `out-of-range values are rejected`() {
        listOf(
            { NoteConfig(markerSizeRatio = 1.2f) },
            { NoteConfig(footnoteSizeRatio = -0.1f) },
            { NoteConfig(minCarriedNoteWidthShare = 1.5f) },
            { NoteConfig(markerRaiseEm = 0f) },
            { NoteConfig(footnoteGapEm = -1f) },
            { NoteConfig(maxMarkerDigits = 0) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }
}
