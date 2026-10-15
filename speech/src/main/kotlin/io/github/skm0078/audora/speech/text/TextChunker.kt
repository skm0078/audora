// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/text/TextChunker.kt
// Purpose: Cuts text longer than an engine's input limit at the most natural boundary that fits.

package io.github.skm0078.audora.speech.text

/**
 * Splits text for engines with an input limit (Android's `TextToSpeech.getMaxSpeechInputLength()`, 4000 chars).
 *
 * Reading units are already sentence-sized (≤ 400 chars, ReadingUnitConfig), so this rarely cuts anything; it exists
 * so that a limit lower than a unit can never make the engine refuse it. In each window it cuts after the last
 * sentence end, else the last clause mark, provided the cut keeps at least half the window (a cut right at the start
 * would produce a run of tiny chunks), else the last space. Failing all three it cuts at the limit, never
 * inside a surrogate pair. A space anywhere still beats cutting a word.
 */
object TextChunker {
    private val SENTENCE_ENDS = setOf('.', '!', '?', '।', '॥', '…')
    private val CLAUSE_MARKS = setOf(',', ';', ':', '—', '–')
    private const val MIN_CUT_SHARE = 2

    fun chunk(text: String, maxChars: Int): List<String> {
        require(maxChars > 0) { "maxChars must be > 0, was $maxChars" }
        val chunks = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > maxChars) {
            val cut = cutPoint(rest, maxChars)
            rest.substring(0, cut).trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) chunks += rest
        return chunks
    }

    /** End index (exclusive) of the next chunk of [text], which is longer than [maxChars]. */
    private fun cutPoint(text: String, maxChars: Int): Int {
        val minCut = maxOf(1, maxChars / MIN_CUT_SHARE)

        // A boundary character at index i is a cut at i + 1, which must still fit: i < maxChars.
        fun lastAfter(marks: (Char) -> Boolean): Int? = (maxChars - 1 downTo minCut - 1)
            .firstOrNull { i -> marks(text[i]) && (i + 1 == text.length || text[i + 1].isWhitespace()) }
            ?.let { it + 1 }

        val space = (maxChars downTo 1).firstOrNull { text[it].isWhitespace() }
        val hard = if (maxChars > 1 && text[maxChars - 1].isHighSurrogate()) maxChars - 1 else maxChars
        return lastAfter { it in SENTENCE_ENDS } ?: lastAfter { it in CLAUSE_MARKS } ?: space ?: hard
    }
}
