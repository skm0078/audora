// File: core/src/main/kotlin/io/github/skm0078/audora/core/share/CardQuote.kt
// Purpose: The words a quote card shows: the listener's lines tidied and cut to a short quote, and their script.

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.ShareCardConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ScriptLang

/**
 * A quote ready for a card. [cut] when the selection was longer than [ShareCardConfig.maxWords]; [script] is the
 * script most of its letters are in (English when it has none), which picks the card's font.
 */
data class CardQuote(val text: String, val cut: Boolean, val script: Lang) {
    companion object {
        private const val ELLIPSIS = "…"
        private val WHITESPACE = Regex("\\s+")

        // A sentence ends at these, optionally followed by closing quotes or brackets.
        private val SENTENCE_ENDS = setOf('.', '!', '?', '।', '॥')
        private val CLOSERS = setOf('"', '\'', '”', '’', ')', ']', '»')

        // The card draws its own quote mark, so the book's own marks around the whole quote are dropped; not when
        // other double marks inside show they only open a line of speech ("Yes," she said, "go.").
        private val OPENERS = setOf('"', '\'', '“', '‘', '«')
        private val ENDERS = setOf('"', '\'', '”', '’', '»')
        private val DOUBLE_MARKS = setOf('"', '“', '”', '«', '»')

        // A cut never leaves these dangling before the "…".
        private val TRAILING = setOf(',', ';', ':', '-', '–', '—')

        /** Pure. Line breaks and runs of spaces become one space; a word is what lies between spaces. */
        fun of(selection: String, config: ShareCardConfig = ShareCardConfig()): CardQuote {
            val tidy = unwrap(selection.trim().split(WHITESPACE).filter(String::isNotEmpty).joinToString(" "))
            val words = tidy.split(' ').filter(String::isNotEmpty)
            if (words.size <= config.maxWords) return CardQuote(tidy, cut = false, script = scriptOf(tidy))
            val kept = words.take(config.maxWords)
            val sentenceEnd = kept.indexOfLast(::endsSentence)
            val text = if (sentenceEnd + 1 >= config.maxWords * config.sentenceEndShare) {
                kept.take(sentenceEnd + 1).joinToString(" ")
            } else {
                kept.joinToString(" ").trimEnd { it in TRAILING || it.isWhitespace() } + ELLIPSIS
            }
            return CardQuote(text, cut = true, script = scriptOf(text))
        }

        /**
         * A whole page for a "Whole page" card: tidied like a quote, cut at [ShareCardConfig.maxPageWords] instead
         * of the quote's limit. Pure.
         */
        fun ofPage(page: String, config: ShareCardConfig = ShareCardConfig()): CardQuote =
            of(page, config.copy(maxWords = config.maxPageWords, maxTextWords = config.maxPageWords))

        /** The script most letters belong to; ties and text without letters read as English. */
        fun scriptOf(text: String): Lang {
            val letters = text.codePoints().toArray().asList().mapNotNull(ScriptLang::of)
            val counts = letters.groupingBy { it }.eachCount()
            val best = counts.entries.maxByOrNull { (lang, count) -> count * Lang.entries.size - lang.ordinal }
            return best?.key ?: Lang.EN
        }

        private fun endsSentence(word: String): Boolean = word.trimEnd { it in CLOSERS }.lastOrNull() in SENTENCE_ENDS

        private fun unwrap(text: String): String {
            if (text.length < 2 || text.first() !in OPENERS || text.last() !in ENDERS) return text
            val inner = text.substring(1, text.length - 1)
            return if (inner.none { it in DOUBLE_MARKS }) inner.trim() else text
        }
    }
}
