// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/QuoteSplitter.kt
// Purpose: Cuts a sentence into narration and quoted speech, so quotes can have their own voice (plan item 7).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.DialogueConfig

/** One run of a sentence: narration, or speech inside quote marks ([quoted]); the marks themselves are dropped. */
data class QuotePart(val text: String, val quoted: Boolean)

/**
 * Pure and stateless, one sentence at a time, so it needs nothing from the sentences around it:
 * - `“…”`, `«…»`, `„…“` and straight `"…"` are quotes; single quotes are not, since `'` is also an apostrophe.
 * - A sentence that ends inside an open quote is quoted to its end (a speech that runs on), and one that meets a
 *   closing mark before any opening one is quoted up to it (the last sentence of a speech).
 * - A quote shorter than [DialogueConfig.minQuoteWords] words stays narration, marks included.
 *
 * Known gap: the middle sentence of a speech three sentences long has no mark at all, so it stays with the narrator.
 *
 * Returns one unquoted part holding [text] unchanged when there is no speech, so a caller can read it as before.
 */
class QuoteSplitter(private val config: DialogueConfig = DialogueConfig()) {
    fun split(text: String): List<QuotePart> {
        val raw = scan(text)
        val parts = raw.map { part ->
            val tooShort = part.quoted && words(part.text) < config.minQuoteWords
            if (tooShort) QuotePart("“${part.text}”", quoted = false) else part
        }
        if (parts.none { it.quoted }) return listOf(QuotePart(text, quoted = false))
        return merged(parts)
    }

    private fun scan(text: String): List<QuotePart> {
        val parts = ArrayList<QuotePart>()
        val buffer = StringBuilder()
        var closer: Char? = null // the mark that ends the open quote; null outside quotes
        fun flush(quoted: Boolean) {
            val piece = buffer.trim().toString()
            buffer.setLength(0)
            if (piece.any(Char::isLetterOrDigit)) parts.add(QuotePart(piece, quoted))
        }
        for (c in text) {
            val open = closer
            when {
                open != null && c == open -> {
                    flush(quoted = true)
                    closer = null
                }

                open == null && c in OPENERS -> {
                    flush(quoted = false)
                    closer = OPENERS.getValue(c)
                }

                open == null && c in CLOSERS && parts.isEmpty() -> flush(quoted = true)

                else -> buffer.append(c)
            }
        }
        flush(quoted = closer != null)
        return parts
    }

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
        val WHITESPACE = Regex("""\s+""")
    }
}
