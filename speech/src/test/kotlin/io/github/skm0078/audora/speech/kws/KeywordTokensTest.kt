// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/KeywordTokensTest.kt
// Purpose: A command phrase naming a piece the model lacks is caught before sherpa-onnx would end the process.

package io.github.skm0078.audora.speech.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordTokensTest {
    private val pieces = VoiceCommand.entries.flatMap { c -> c.tokens.flatMap { it.split(' ') } }.distinct()

    /** A tokens.txt as sherpa-onnx writes it: "piece id" per line, with the blank and unknown pieces. */
    private fun tokensFile(pieces: List<String>): Sequence<String> =
        (listOf("<blk>", "<sos/eos>", "<unk>") + pieces).mapIndexed { id, piece -> "$piece $id" }.asSequence()

    @Test
    fun `every phrase of this build is made of pieces a complete tokens file has`() {
        val vocabulary = KeywordTokens.vocabulary(tokensFile(pieces + "▁THE"))
        assertTrue(KeywordTokens.unknown(vocabulary).isEmpty())
    }

    @Test
    fun `a missing piece is reported once, in phrase order`() {
        val vocabulary = KeywordTokens.vocabulary(tokensFile(pieces - setOf("▁PA", "U")))
        assertEquals(listOf("U", "▁PA"), KeywordTokens.unknown(vocabulary))
    }

    @Test
    fun `the vocabulary reads pieces, not ids, and skips blank lines`() {
        val vocabulary = KeywordTokens.vocabulary(sequenceOf("▁A 4", "", "  ", "OR 17"))
        assertEquals(setOf("▁A", "OR"), vocabulary)
    }
}
