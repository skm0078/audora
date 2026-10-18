// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrMargins.kt
// Purpose: Finds running headers, footers and page numbers on a scanned page against its neighbours' margins.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.pdf.cleanup.Folios
import io.github.skm0078.audora.pdf.cleanup.SIGNATURE_DIGITS
import io.github.skm0078.audora.pdf.cleanup.marginSignature
import io.github.skm0078.audora.pdf.quality.CharacterErrorRate
import io.github.skm0078.audora.pdf.render.ViewRect

/** The lines OCR read in the top and bottom strips of a page, each strip top to bottom. */
class OcrMargins(val top: List<String>, val bottom: List<String>)

/**
 * Running-header removal for scanned pages (P3-05 follow-up, ADR-032), the OCR counterpart of `ClutterRemover`.
 * Neighbouring pages are not OCR'd whole for this: only strips at their top and bottom
 * ([CleanupConfig.ocrMarginShare] of the page each, `PageOcr.margins`), so the result never depends on which pages
 * happen to be cached and the reader and the player always drop the same lines.
 *
 * A line of the page's own OCR is a candidate when it sits in the top or bottom band (one of the
 * [CleanupConfig.maxMarginLines] lines nearest that edge) and the page has text outside both bands. It is dropped
 * when it is a page number by itself, carries a number that steps with the page number across a neighbour's strip
 * (a running folio), or its letters repeat in the same strip on enough neighbours (a running header), allowing for
 * a letter or two misread ([CleanupConfig.ocrMatchSimilarity]), or it is a printer's slug: a layout file's name
 * ("HUD3_25_09.indd", ".qxd") or a print date with its time ("5/13/11 6:45 AM").
 */
internal class OcrClutter(private val config: CleanupConfig) {
    /**
     * Indices into [text]'s lines to drop. [neighbours] maps the page offset (−2, −1, 1, 2…) to that page's strips,
     * read in the same orientation as this page; it is asked for only when the page has candidates, so a page without
     * boxes or margin lines costs no extra OCR.
     */
    fun droppedLines(text: OcrText, neighbours: () -> Map<Int, OcrMargins>): Set<Int> {
        val placed = text.lines.mapIndexedNotNull { i, line ->
            boxOf(line)?.let { Placed(i, line.text, text.readingFrame(it)) }
        }
        val share = config.ocrMarginShare
        val top = placed.filter { it.center < share }.sortedBy { it.center }.take(config.maxMarginLines)
        val bottom = placed.filter {
            it.center > 1 - share
        }.sortedByDescending { it.center }.take(config.maxMarginLines)
        val bands = (top + bottom).map { it.index }.toSet()
        if (bands.isEmpty() || placed.none { it.index !in bands }) return emptySet()
        val strips = neighbours()
        val needed = minOf(config.minRepeatPages, strips.size + 1) - 1
        val dropped = HashSet<Int>()
        for ((band, lines) in listOf(Band.TOP to top, Band.BOTTOM to bottom)) {
            val others = strips.mapValues { (_, margins) -> if (band == Band.TOP) margins.top else margins.bottom }
            lines.filter { isClutter(it.text, others, needed) }.forEach { dropped.add(it.index) }
        }
        return dropped
    }

    private fun isClutter(line: String, neighbours: Map<Int, List<String>>, needed: Int): Boolean {
        val folios = Folios.of(listOf(line), config.maxFolioDigits)
        val signature = marginSignature(line)
        val repeatable = needed > 0 && signature.count { it != SIGNATURE_DIGITS } >= config.minSignatureLetters
        val repeated = repeatable &&
            neighbours.values.count { lines -> lines.any { similar(signature, marginSignature(it)) } } >= needed
        return folios.standalone || isRunningFolio(folios.values, neighbours) || repeated ||
            isPrintSlug(line, neighbours)
    }

    /**
     * A printer's slug: a layout file's name, or a print date and time that a neighbour's strip carries too. A date
     * and time alone is an exam date sheet's row, a notice's date or a receipt's first line.
     */
    private fun isPrintSlug(line: String, neighbours: Map<Int, List<String>>): Boolean =
        LAYOUT_FILE.containsMatchIn(line) ||
            (
                PRINT_TIME.containsMatchIn(line) &&
                    neighbours.values.any { lines -> lines.any(PRINT_TIME::containsMatchIn) }
                )

    /** A number on the line that a neighbour's strip carries shifted by the page offset: 12 here, 13 a page on. */
    private fun isRunningFolio(values: List<Int>, neighbours: Map<Int, List<String>>): Boolean =
        values.isNotEmpty() && neighbours.any { (offset, lines) ->
            lines.any { other -> Folios.of(listOf(other), config.maxFolioDigits).values.any { it - offset in values } }
        }

    private fun similar(a: String, b: String): Boolean {
        val longest = maxOf(a.length, b.length)
        return longest > 0 &&
            1f - CharacterErrorRate.editDistance(a, b).toFloat() / longest >= config.ocrMatchSimilarity
    }

    private fun boxOf(line: OcrLine): ViewRect? {
        val boxes = line.words.mapNotNull { it.box }
        if (boxes.isEmpty()) return null
        return ViewRect(
            boxes.minOf {
                it.left
            },
            boxes.minOf { it.top },
            boxes.maxOf { it.right },
            boxes.maxOf { it.bottom },
        )
    }

    private class Placed(val index: Int, val text: String, box: ViewRect) {
        val center: Float = (box.top + box.bottom) / 2
    }

    private enum class Band { TOP, BOTTOM }
}

/** A layout program's file name in a printer's slug: "HUD3_25_09Single-Bengali-BE.indd", "ch04.qxd". */
private val LAYOUT_FILE = Regex("""\S\.(?i:indd|qxd|qxp)\b""")

/**
 * A print date with its time in a printer's slug: "5/13/11 6:45 AM", "2019-03-04 10:12". ASCII digits only, as
 * typesetting software writes them (`\d` would also take Devanagari and Bengali digits on Android's ICU regex).
 */
private val PRINT_TIME = Regex("""[0-9]{1,4}[/.-][0-9]{1,2}[/.-][0-9]{2,4},?\s+[0-9]{1,2}:[0-9]{2}""")
