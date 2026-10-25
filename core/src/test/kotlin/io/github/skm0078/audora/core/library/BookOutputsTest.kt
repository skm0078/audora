// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/BookOutputsTest.kt
// Purpose: Made files are listed as one set per run, newest first, with their parts in order (ADR-068).

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookOutputsTest {
    private val book = BookId("b")

    private fun output(set: String, part: Int, at: Long, kind: OutputKind = OutputKind.AUDIOBOOK, ms: Long? = 1_000) =
        BookOutput(book, kind, set, part, "$set-$part", "content://$set/$part", "audio/mp4", at, 10, durationMs = ms)

    @Test
    fun `outputs group into sets, newest set first, parts in order`() {
        val outputs = listOf(
            output("old", 2, at = 1),
            output("new", 1, at = 5, kind = OutputKind.STUDIO_AUDIO),
            output("old", 1, at = 1),
        )

        val sets = OutputSet.of(outputs)

        assertEquals(listOf("new", "old"), sets.map { it.key })
        assertEquals(listOf(1, 2), sets[1].files.map { it.part })
        assertEquals(OutputKind.AUDIOBOOK, sets[1].kind)
        assertEquals(2_000L, sets[1].durationMs)
        assertEquals(20L, sets[1].sizeBytes)
    }

    @Test
    fun `a document set has no duration`() {
        val copy = OutputSet.of(listOf(output("c", 1, at = 1, kind = OutputKind.ACCESSIBLE_COPY, ms = null))).single()

        assertNull(copy.durationMs)
        assertEquals(false, copy.kind.isAudio)
    }
}
