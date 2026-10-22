// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrNoteMarkers.kt
// Purpose: Leaves note markers out of a scanned page's words: signs OCR glued to a word, small raised numbers (RI-5a).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.NoteConfig
import io.github.skm0078.audora.pdf.layout.NoteMarkers.Companion.isOperator
import io.github.skm0078.audora.pdf.render.ViewRect

/**
 * OCR gives no font sizes, so the text-layer marker finder (`NoteMarkers`) cannot run on a scanned page, and
 * Tesseract reads a superscript marker one of two ways:
 *
 * - **Glued to the word**, as a sign: "Vaswani*", "Eich*,", "Haghighat®,", "Smith#,", "double.!". A run of note signs
 *   ([MARKS]: stars, daggers, "#", "®", "™", "§", "¶", superscript digits) after a word of at least two letters is
 *   left out, punctuation after it kept ("Eich,"); so is a "!" glued after a full stop. Plain digits glued to a word
 *   are kept: "Fig.1" and "p.12" are references, not markers, and OCR's word box cannot tell them apart. Next to a
 *   maths operator (the word before or after starts or ends with one) the signs are an exponent and stay: "E = mc²",
 *   "ax² + bx"; so does a raised sign of its own beside one.
 * - **As a word of its own**: a run of note signs right after another word (within [HUG_EM] of the line's median
 *   word height), small (`NoteConfig.markerSizeRatio` of that height) and raised (`markerRaiseEm` of it above the
 *   line's median word bottom), as on a text layer. A number on its own is kept even when it looks raised: on 364
 *   scanned test pages every one was a table figure or chart label, the bottoms of Hindi and Bengali words vary
 *   too much to tell, and dropping a real figure is worse than reading a marker.
 *
 * Boxes are compared in the reading frame (`frame`), so a page OCR'd turned is measured upright.
 */
internal class OcrNoteMarkers(private val config: NoteConfig) {
    /** [words] (one OCR line) without its note markers. */
    fun silence(words: List<OcrWord>, frame: (ViewRect) -> ViewRect): List<OcrWord> {
        if (!config.silenceMarkers || words.isEmpty()) return words
        val raised = raisedMarkers(words, frame)
        return words.mapIndexedNotNull { i, word ->
            val glued = if (besideOperator(words, i, i)) {
                word.text
            } else {
                GLUED.replace(word.text) { it.groupValues[1] + it.groupValues[2] }
            }
            val text = STRAY_BANG.replace(glued, ".")
            when {
                i in raised && !besideOperator(words, i - 1, i) -> null
                text == word.text -> word
                else -> OcrWord(text, word.box, word.confidence)
            }
        }
    }

    /** The word before [from] ends, or the word after [to] starts, with a maths operator. */
    private fun besideOperator(words: List<OcrWord>, from: Int, to: Int): Boolean =
        words.getOrNull(from - 1)?.text?.lastOrNull().isOperator() ||
            words.getOrNull(to + 1)?.text?.firstOrNull().isOperator()

    private fun raisedMarkers(words: List<OcrWord>, frame: (ViewRect) -> ViewRect): Set<Int> {
        val boxes = words.map { word -> word.box?.let(frame) }
        val known = boxes.filterNotNull()
        if (known.size < 2) return emptySet()
        val height = known.map { it.bottom - it.top }.sorted()[known.size / 2]
        val baseline = known.map { it.bottom }.sorted()[known.size / 2]
        return words.indices.filterTo(HashSet()) { i ->
            val box = boxes[i]
            val previous = boxes.getOrNull(i - 1)
            previous != null && box != null && SIGNS.matches(words[i].text) &&
                box.left - previous.right <= height * HUG_EM &&
                box.bottom - box.top <= height * config.markerSizeRatio &&
                baseline - box.bottom >= height * config.markerRaiseEm
        }
    }

    private companion object {
        const val HUG_EM = 0.35f
        const val MARKS = "*∗†‡#®™§¶¹²³⁴⁵⁶⁷⁸⁹⁰"

        val SIGNS = Regex("[$MARKS]+")

        /** A word of two letters or more (then perhaps a stop or quote), a run of note signs, closing punctuation. */
        val GLUED = Regex("""^(.*[\p{L}\p{M}]{2}[.,;:'’”]?)[$MARKS]+([.,;:)]*)$""")

        /** "double.!": a marker read as "!" after a full stop. */
        val STRAY_BANG = Regex("""(?<=[\p{L}\p{M}]{2})\.!+$""")
    }
}
