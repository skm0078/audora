// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/SentenceSplitter.kt
// Purpose: Finds sentence boundaries in page text (Build Spec §5 step 7), with the Indic rules of P3-02.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.SentenceConfig
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
 * [IndicSentenceRules] then correct the breaks around dandas, abbreviations and closing quotes, so both platforms
 * split the same way.
 */
class BreakIteratorSentenceSplitter(config: SentenceConfig = SentenceConfig()) : SentenceSplitter {
    private val rules = IndicSentenceRules(config)

    override fun split(text: String, lang: Lang): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag(lang.bcp47Tag))
        iterator.setText(text)
        val breaks = ArrayList<Int>()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (end < text.length) breaks.add(end)
            end = iterator.next()
        }
        val ends = rules.refine(text, breaks) + text.length
        return ends.mapIndexed { i, sentenceEnd -> (if (i == 0) 0 else ends[i - 1]) until sentenceEnd }
    }
}
