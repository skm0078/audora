// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/SentenceSplitter.kt
// Purpose: Finds sentence boundaries in page text (Build Spec §5 step 7; Indic rules come in P3-02).

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.Lang
import java.text.BreakIterator
import java.util.Locale

fun interface SentenceSplitter {
    /** Sentence ranges over [text], start inclusive and end exclusive, covering it in order. */
    fun split(text: String, lang: Lang): List<IntRange>
}

/**
 * java.text.BreakIterator: on Android it is backed by ICU (the spec's ICU BreakIterator), and on the JVM it
 * runs in plain unit tests. Line breaks must be replaced by spaces first: ICU ends a sentence at every CR/LF.
 */
class BreakIteratorSentenceSplitter : SentenceSplitter {
    override fun split(text: String, lang: Lang): List<IntRange> {
        val iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag(lang.bcp47Tag))
        iterator.setText(text)
        val ranges = ArrayList<IntRange>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            ranges.add(start until end)
            start = end
            end = iterator.next()
        }
        return ranges
    }
}
