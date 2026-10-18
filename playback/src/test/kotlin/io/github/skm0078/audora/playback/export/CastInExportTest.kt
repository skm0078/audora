// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/CastInExportTest.kt
// Purpose: An export offers character voices only when every language it reads has a narrator with other speakers.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class CastInExportTest {
    /** Built-in Kokoro English narrator: 27 other speakers; Hindi and Bengali voices have one speaker. */
    private val kokoro: (Lang) -> Int = { if (it == Lang.EN) KOKORO_OTHERS else 0 }

    /** An imported Kokoro or Kitten folder (custom-* id) or any one-speaker voice: no table, no cast. */
    private val imported: (Lang) -> Int = { 0 }

    @Test
    fun `the cast applies only when every language read has other speakers`() {
        val cases = listOf(
            Case(false, setOf(Lang.EN), kokoro, CastInExport.OFF),
            Case(true, emptySet(), kokoro, CastInExport.OFF),
            Case(true, setOf(Lang.EN), kokoro, CastInExport.CAST),
            Case(true, setOf(Lang.EN), imported, CastInExport.NEEDS_BUILT_IN_VOICE),
            Case(true, setOf(Lang.HI), kokoro, CastInExport.ENGLISH_ONLY),
            Case(true, setOf(Lang.BN), imported, CastInExport.ENGLISH_ONLY),
            Case(true, setOf(Lang.EN, Lang.HI), kokoro, CastInExport.ENGLISH_ONLY),
            Case(true, setOf(Lang.EN, Lang.BN), imported, CastInExport.ENGLISH_ONLY),
        )
        cases.forEach { case ->
            assertEquals(case.toString(), case.expected, CastInExport.of(case.on, case.languages, case.castSize))
        }
    }

    private data class Case(
        val on: Boolean,
        val languages: Set<Lang>,
        val castSize: (Lang) -> Int,
        val expected: CastInExport,
    )

    private companion object {
        const val KOKORO_OTHERS = 27
    }
}
