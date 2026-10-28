// File: core/src/main/kotlin/io/github/skm0078/audora/core/share/QuoteReading.kt
// Purpose: A card's quote read aloud a sentence at a time, and the word starts of the whole reading (ADR
//          2026-10-28-quote-video).

package io.github.skm0078.audora.core.share

/** One sentence as the voice read it: when it starts in the reading, how long it speaks, and its words' starts. */
data class SpokenSentence(val startMs: Long, val durationMs: Long, val wordStartsMs: List<Long>?)

/**
 * The quote's [sentences], in order, whose words (what whitespace separates) are exactly the quote's words: word i of
 * the reading is word i on the card, so the video lights the word the voice is saying.
 */
class QuoteReading private constructor(val sentences: List<String>) {
    /**
     * Every word's start in the whole reading, ascending: each sentence's own starts (from its audio) moved to where
     * the sentence begins. A sentence whose audio gave none ([SpokenSentence.wordStartsMs] null, or not one per word)
     * shares its time among its words by their letters, as the reader's highlight does without timings.
     */
    fun wordStarts(spoken: List<SpokenSentence>): List<Long> {
        require(spoken.size == sentences.size) { "${spoken.size} readings for ${sentences.size} sentences" }
        val starts = sentences.zip(spoken).flatMap { (sentence, said) ->
            val words = wordsOf(sentence)
            val own = said.wordStartsMs?.takeIf { it.size == words.size } ?: byLetters(words, said.durationMs)
            own.map { said.startMs + it.coerceIn(0, said.durationMs) }
        }
        // Rounding inside a sentence can put a start a millisecond before the one ahead of it; never let it go back.
        return starts.runningReduce(::maxOf)
    }

    companion object {
        /** A word: what whitespace separates, as the reader's highlight and the video's painter count them. */
        val WORD = Regex("[^\\p{javaWhitespace}\\p{Z}]+")
        private val SENTENCE_ENDS = setOf('.', '!', '?', '।', '॥')
        private val CLOSERS = setOf('"', '\'', '”', '’', ')', ']', '»', '…')

        /** Pure. A sentence ends with a word ending in . ! ? । or ॥ (closing marks after it allowed). */
        fun of(quote: String): QuoteReading {
            val sentences = mutableListOf<String>()
            val current = mutableListOf<String>()
            wordsOf(quote).forEach { word ->
                current += word
                if (word.trimEnd { it in CLOSERS }.lastOrNull() in SENTENCE_ENDS) {
                    sentences += current.joinToString(" ")
                    current.clear()
                }
            }
            if (current.isNotEmpty()) sentences += current.joinToString(" ")
            return QuoteReading(sentences)
        }

        fun wordsOf(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

        private fun byLetters(words: List<String>, durationMs: Long): List<Long> {
            val weights = words.map { word -> word.count(Char::isLetterOrDigit).coerceAtLeast(1) }
            val total = weights.sum().toLong()
            return weights.runningFold(0L) { before, weight -> before + weight }
                .dropLast(1)
                .map { before -> before * durationMs / total }
        }
    }
}
