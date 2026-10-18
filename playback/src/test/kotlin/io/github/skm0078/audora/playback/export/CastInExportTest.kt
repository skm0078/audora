// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/CastInExportTest.kt
// Purpose: An export offers character voices only when the narrator's model has other speakers.

package io.github.skm0078.audora.playback.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CastInExportTest {
    @Test
    fun `the cast applies only when the setting is on and the narrator has other speakers`() {
        val cases = listOf(
            Triple(false, 0, CastInExport.OFF),
            Triple(false, KOKORO_ENGLISH, CastInExport.OFF),
            // An imported Kokoro or Kitten folder (custom-* id) and every one-speaker voice: no table, empty cast.
            Triple(true, 0, CastInExport.NARRATOR_ONLY),
            Triple(true, KOKORO_ENGLISH, CastInExport.CAST),
        )
        cases.forEach { (on, speakers, expected) ->
            assertEquals("on=$on speakers=$speakers", expected, CastInExport.of(on, speakers))
        }
    }

    private companion object {
        const val KOKORO_ENGLISH = 28
    }
}
