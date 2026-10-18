// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/KeywordTokensTest.kt
// Purpose: A command phrase naming a piece the model lacks is caught before sherpa-onnx would end the process;
// every phrase is checked against the shipped model's own token list.

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

    /**
     * The shipped keyword model's token list, unchanged: tokens.txt from
     * sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01 (Apache-2.0; archive sha256 as in models.json, file sha256
     * fd2ded40…). A new phrase with a piece outside it would end the process when the spotter loads.
     */
    @Test
    fun `every phrase of this build is made of pieces the shipped model has`() {
        val lines = requireNotNull(javaClass.getResourceAsStream(SHIPPED_TOKENS)) { "missing $SHIPPED_TOKENS" }
            .bufferedReader().useLines { it.toList() }
        assertEquals(SHIPPED_TOKEN_COUNT, lines.size)
        assertEquals(emptyList<String>(), KeywordTokens.unknown(KeywordTokens.vocabulary(lines.asSequence())))
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

    private companion object {
        const val SHIPPED_TOKENS = "/kws/gigaspeech-3.3M-tokens.txt"
        const val SHIPPED_TOKEN_COUNT = 500
    }
}
