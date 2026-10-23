// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/acted/ChatterboxTokenizerTest.kt
// Purpose: The Kotlin tokenizer gives the ids Hugging Face tokenizers gave for the same sentences (ADR-059).

package io.github.skm0078.audora.speech.acted

import java.io.File
import java.io.StringReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatterboxTokenizerTest {
    private val tokenizer = File("src/main/assets/${ChatterboxTokenizer.ASSET}").reader(Charsets.UTF_8).use {
        ChatterboxTokenizer.read(it)
    }

    @Test
    fun `every reference sentence gets the original tokenizer's ids`() {
        val cases = requireNotNull(javaClass.getResourceAsStream(CASES)) { "missing $CASES" }
            .bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        assertEquals(12, cases.size)
        for (case in cases) {
            val (text, ids) = case.split('\t')
            val expected = ids.split(',').map { it.toLong() }.toLongArray()
            assertArrayEquals(text, expected, tokenizer.encode(text))
        }
    }

    @Test
    fun `merges apply by rank, spaces become their own token and the template wraps the text`() {
        val table = """
            template	9	8,7
            unknown	1
            added	2	1	[SPACE]
            added	3	0	[hi]
            vocab	10	a
            vocab	11	b
            vocab	12	ab
            vocab	13	c
            vocab	14	abc
            vocab	15	,
            merge	a	b
            merge	ab	c
        """.trimIndent()
        val small = ChatterboxTokenizer.read(StringReader(table))

        assertArrayEquals(longArrayOf(9, 3, 14, 2, 11, 1, 15, 8, 7), small.encode("ABC bz,"))
    }

    private companion object {
        const val CASES = "/acted/tokenizer_cases.tsv"
    }
}
