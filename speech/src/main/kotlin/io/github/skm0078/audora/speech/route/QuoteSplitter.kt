// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/QuoteSplitter.kt
// Purpose: Cuts a sentence into narration and quoted speech, so quotes can have their own voice (plan item 7).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.DialogueConfig

/** One run of a sentence: narration, or speech inside quote marks ([quoted]); the marks themselves are dropped. */
data class QuotePart(val text: String, val quoted: Boolean)

/**
 * Pure, one sentence at a time; the only state it takes from the sentences before is whether this one starts inside
 * a quote ([endsInQuote] of the one before, within a paragraph):
 * - `“…”`, `«…»`, `„…“` and straight `"…"` are quotes, and so are curly single quotes `‘…’`, which many British,
 *   Hindi and Bengali books print dialogue in. `‘` opens only at a word's start and `’` closes only at a word's end,
 *   so an apostrophe inside a word ("don’t") never does; a `’` with no `‘` before it closes a speech only right after
 *   the speech's own punctuation ("…hold it.’"), never after a letter ("James’ hat"). Straight `'` stays an apostrophe.
 * - A quote shorter than [DialogueConfig.minQuoteWords] words is still speech when it ends on speech punctuation
 *   ("Alive?", "Two hundred!", "At home."); one without ("so-called “experts”") stays narration, marks included.
 * - A sentence that ends inside an open quote is quoted to its end (a speech that runs on), and one that meets a
 *   closing mark before any opening one is quoted up to it (the last sentence of a speech). One that starts inside
 *   a quote is quoted up to its first closing mark, or whole if it has none (the middle of a long speech).
 *
 * Returns one unquoted part holding [text] unchanged when there is no speech, so a caller can read it as before.
 */
class QuoteSplitter(private val config: DialogueConfig = DialogueConfig()) {
    fun split(text: String, startsInQuote: Boolean = false): List<QuotePart> {
        val raw = scan(text, startsInQuote).parts
        val parts = raw.map { part ->
            val tooShort = part.quoted && words(part.text) < config.minQuoteWords && !utterance(part.text)
            if (tooShort) QuotePart("“${part.text}”", quoted = false) else part
        }
        if (parts.none { it.quoted }) return listOf(QuotePart(text, quoted = false))
        return merged(parts)
    }

    /** Whether [text] starts with an opening quote mark: a new paragraph that reopens a speech, as books print it. */
    fun opensWithQuote(text: String): Boolean =
        text.trimStart().firstOrNull()?.let { it in OPENERS || it == SINGLE_OPEN } == true

    /** Whether a quote is still open at the end of [text]: the next sentence of the paragraph starts inside it. */
    fun endsInQuote(text: String, startsInQuote: Boolean = false): Boolean = scan(text, startsInQuote).open

    private class Scan(val parts: List<QuotePart>, val open: Boolean)

    private fun scan(text: String, startsInQuote: Boolean): Scan {
        val parts = ArrayList<QuotePart>()
        val buffer = StringBuilder()
        // The marks that end the open quote; null outside quotes.
        var closers: Set<Char>? = if (startsInQuote) CARRIED_CLOSERS else null
        fun flush(quoted: Boolean) {
            val piece = buffer.trim().toString()
            buffer.setLength(0)
            if (piece.any(Char::isLetterOrDigit)) parts.add(QuotePart(piece, quoted))
        }
        text.forEachIndexed { i, c ->
            val open = closers
            val before = text.getOrNull(i - 1)
            val after = text.getOrNull(i + 1)
            when {
                open != null && c in open && (c != SINGLE_CLOSE || endsWord(after)) -> {
                    flush(quoted = true)
                    closers = null
                }

                open == null && c == SINGLE_OPEN && startsWord(before, after) -> {
                    flush(quoted = false)
                    closers = setOf(SINGLE_CLOSE)
                }

                open == null && c in OPENERS -> {
                    flush(quoted = false)
                    closers = setOf(OPENERS.getValue(c))
                }

                open == null && parts.isEmpty() && closesSpeech(c, before, after) -> flush(quoted = true)

                else -> buffer.append(c)
            }
        }
        val open = closers != null
        flush(quoted = open)
        return Scan(parts, open)
    }

    /** A closing mark with no opening one before it: the last sentence of a speech an earlier sentence opened. */
    private fun closesSpeech(c: Char, before: Char?, after: Char?): Boolean = when (c) {
        in CLOSERS -> true
        SINGLE_CLOSE -> before != null && before in SPEECH_END && endsWord(after)
        else -> false
    }

    /** `‘` opens a quote at a word's start: after a space, a bracket or a dash, and before a letter or a mark. */
    private fun startsWord(before: Char?, after: Char?): Boolean =
        (before == null || before.isWhitespace() || before in "([—–-") && after != null && !after.isWhitespace()

    /** `’` closes a quote at a word's end; inside a word ("don’t", "ma’am") it is an apostrophe. */
    private fun endsWord(after: Char?): Boolean = after == null || !after.isLetterOrDigit()

    /** A short quote that is a whole utterance: it ends on speech punctuation inside the marks. */
    private fun utterance(quote: String): Boolean = quote.trimEnd().lastOrNull()?.let { it in SPEECH_END } == true

    /** Neighbours of one kind become one part, so the voice keeps a whole clause's intonation. */
    private fun merged(parts: List<QuotePart>): List<QuotePart> = parts.fold(ArrayList()) { out, part ->
        val last = out.lastOrNull()
        if (last != null && last.quoted == part.quoted) {
            out[out.lastIndex] = last.copy(text = "${last.text} ${part.text}")
        } else {
            out.add(part)
        }
        out
    }

    private fun words(text: String): Int = text.split(WHITESPACE).count { word -> word.any(Char::isLetterOrDigit) }

    private companion object {
        /** Each opening mark and the mark that closes it. */
        val OPENERS = mapOf('“' to '”', '«' to '»', '„' to '“', '"' to '"')
        val CLOSERS = setOf('”', '»')

        /** Curly single quotes: `’` is also the apostrophe, so both are read with their neighbours (see the class). */
        const val SINGLE_OPEN = '‘'
        const val SINGLE_CLOSE = '’'

        /** Punctuation a speech ends on inside its marks, Hindi and Bengali danda included. */
        const val SPEECH_END = ".?!,…।॥;:—"

        /** A quote opened in an earlier sentence: any closing mark ends it (not `“`, which opens English quotes). */
        val CARRIED_CLOSERS = CLOSERS + '"' + SINGLE_CLOSE
        val WHITESPACE = Regex("""\s+""")
    }
}
