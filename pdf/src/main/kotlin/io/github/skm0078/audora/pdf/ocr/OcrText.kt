// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrText.kt
// Purpose: One page as OCR read it: words with their place on the page, lines, paragraphs and the page's turn.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.pdf.render.ViewRect

/**
 * A word and where it is on the page as displayed, in fractions of the page's width and height from its top-left
 * corner ([ViewRect], the same frame the reader draws in); null when the engine gave no box. [confidence] is the
 * engine's confidence in the word, 0–100, or null when it gave none (the speakability score reads it, ADR-035).
 */
class OcrWord(val text: String, val box: ViewRect?, val confidence: Int? = null) {
    init {
        require(confidence == null || confidence in 0..MAX_CONFIDENCE) {
            "confidence must be in 0..100, was $confidence"
        }
    }

    companion object {
        const val MAX_CONFIDENCE = 100
    }
}

/** One line of words, left to right in reading order; [paragraph] numbers the page's paragraphs from 0. */
class OcrLine(val words: List<OcrWord>, val paragraph: Int) {
    val text: String get() = words.joinToString(" ") { it.text }
}

/**
 * The text of a page read by OCR (P3-05, ADR-032), lines in reading order.
 *
 * @property quarterTurns how many quarter turns clockwise the page image was turned before it read upright (a
 *   sideways scan): 0 to 3. Word boxes are always on the page as displayed, never in the turned image.
 */
class OcrText(val lines: List<OcrLine>, val quarterTurns: Int = 0) {
    init {
        require(quarterTurns in 0 until FULL_TURN) { "quarterTurns must be in 0..3, was $quarterTurns" }
    }

    /**
     * The engine's confidence in the page, 0–100: its words' confidences weighted by their length. Null when no word
     * has one (fakes, engines without confidences). Shown as the page's OCR quality (RI-9).
     */
    val meanConfidence: Int? by lazy {
        val scored = lines.flatMap { it.words }.mapNotNull { word -> word.confidence?.let { word.text.length to it } }
        val chars = scored.sumOf { (length, _) -> length }
        if (chars ==
            0
        ) {
            null
        } else {
            (scored.sumOf { (length, confidence) -> length.toLong() * confidence } / chars).toInt()
        }
    }

    /** Lines separated by a line break, paragraphs by a blank line: what language checks and the text sample read. */
    val text: String by lazy {
        val out = StringBuilder()
        lines.forEachIndexed { i, line ->
            if (i > 0) out.append(if (line.paragraph != lines[i - 1].paragraph) "\n\n" else "\n")
            out.append(line.text)
        }
        out.toString()
    }

    /** [box] in the page's reading frame: as it sits in the image once the image is turned upright. */
    fun readingFrame(box: ViewRect): ViewRect = turnView(box, quarterTurns)

    companion object {
        const val FULL_TURN = 4

        /** Text without positions (fakes, and engines without boxes): blank lines start paragraphs. */
        fun plain(text: String): OcrText {
            val lines = ArrayList<OcrLine>()
            var paragraph = 0
            var blank = false
            for (raw in text.lineSequence()) {
                val words = raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }
                if (words.isEmpty()) {
                    blank = lines.isNotEmpty()
                    continue
                }
                if (blank) paragraph++
                blank = false
                lines.add(OcrLine(words.map { OcrWord(it, null) }, paragraph))
            }
            return OcrText(lines)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}

/**
 * [rect] (fractions of an image) after turning the image [quarterTurns] quarter turns clockwise. Turning by
 * `FULL_TURN - quarterTurns` maps it back.
 */
internal fun turnView(rect: ViewRect, quarterTurns: Int): ViewRect {
    var out = rect
    // A point (u, v) goes to (1 − v, u) per clockwise quarter turn.
    repeat(Math.floorMod(quarterTurns, OcrText.FULL_TURN)) {
        out = ViewRect(left = 1 - out.bottom, top = out.left, right = 1 - out.top, bottom = out.right)
    }
    return out
}
