// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrLanguages.kt
// Purpose: Picks the Tesseract languages for each OCR page of a book, learning the book's languages as it reads.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageQuality
import io.github.skm0078.audora.pdf.quality.RouteReason
import io.github.skm0078.audora.pdf.quality.ocrLangs

/**
 * Tesseract must be told the languages: one model is most accurate, and every extra one costs time. In order:
 * 1. a page whose text layer is only broken (wrong glyph map, junk, impossible Indic sequences, unknown legacy glyphs,
 *    a failed sample) still shows which script it is in, so its own letters decide ([PageQuality.ocrLangs], measured
 *    after legacy-font conversion);
 * 2. a page with no text layer, or only a scanner's hidden one (often Latin OCR run over Devanagari), takes the
 *    languages the book has shown so far, on readable pages or in earlier OCR;
 * 3. a book that has shown nothing yet (a scanned book's first page) asks [PageOcr.detectLangs] once.
 *
 * One per book, used under the book's lock.
 */
internal class OcrLanguages(private val config: QualityConfig) {
    /** Pages that showed each language so far. */
    private val seen = LinkedHashMap<Lang, Int>()

    /**
     * The book's languages as seen so far, null until a page showed any: those seen on at least a quarter as many
     * pages as the most-seen one, at most [MAX_LANGS], most-seen first. One English copyright or index page
     * in a Hindi book therefore leaves the Hindi pages read as Hindi, while a book half in each keeps both.
     */
    var known: Set<Lang>? = null
        private set

    /** Remembers the languages a readable page showed; an empty set (a page without letters) changes nothing. */
    fun learn(langs: Set<Lang>) {
        if (langs.isEmpty()) return
        langs.forEach { seen[it] = (seen[it] ?: 0) + 1 }
        val most = seen.values.max()
        known = seen.entries
            .filter { it.value * MIN_SHARE_DIVISOR >= most }
            .sortedByDescending { it.value }
            .take(MAX_LANGS)
            .mapTo(LinkedHashSet()) { it.key }
    }

    /** Remembers the languages of OCR output. */
    fun learnFrom(ocrText: CharSequence) = learn(ocrLangs(ocrText, config))

    /**
     * @param pageLangs the languages of the page's own (converted) text layer.
     * @param remember whether detected languages become the book's; false for a look that must change nothing.
     */
    fun forPage(
        pageIndex: Int,
        reason: RouteReason,
        pageLangs: Set<Lang>,
        ocr: PageOcr,
        remember: Boolean = true,
    ): Outcome<Set<Lang>, OcrError> {
        val trusted = pageLangs.takeIf { reason !in UNTRUSTED_LAYER && it.isNotEmpty() } ?: known
        if (trusted != null) return Outcome.Success(trusted)
        val detected = ocr.detectLangs(pageIndex)
        if (remember && detected is Outcome.Success) learn(detected.value)
        return detected
    }

    private companion object {
        /** A language counts when it was on at least a quarter of the pages of the most-seen one. */
        const val MIN_SHARE_DIVISOR = 4

        /** Every extra Tesseract model slows a page, and two cover every book seen so far. */
        const val MAX_LANGS = 2

        /** Reasons that say the page's letters (if any) are not its real text. */
        val UNTRUSTED_LAYER = setOf(RouteReason.TOO_LITTLE_TEXT, RouteReason.HIDDEN_TEXT_LAYER)
    }
}
