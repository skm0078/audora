// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/PageProfile.kt
// Purpose: The little a page's neighbours need for header and footer removal: its margin lines and text area.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.TextLine

/**
 * A page's summary for its neighbours (ADR-020): a few margin lines folded to signatures, their page numbers, and
 * the page's hyphenated words for de-hyphenation. A few hundred bytes, so a book keeps dozens of them while it holds
 * only a handful of whole pages. In memory only and never logged: signatures are the PDF's own words.
 *
 * @property extent union of the page's line boxes; null when no line has a box.
 * @property lineCount lines with a box on the page.
 * @property top the page's topmost lines, highest first; [bottom] its lowest, lowest first.
 * @property hyphenated words printed with a hyphen inside a line on this page, as lower-case pairs ("block-pulse"),
 *   so a word broken at that hyphen at a line end keeps it.
 */
class PageProfile internal constructor(
    val pageIndex: Int,
    val extent: TextBox?,
    val lineCount: Int,
    internal val top: List<MarginLine>,
    internal val bottom: List<MarginLine>,
    internal val hyphenated: Set<String>,
)

/**
 * @property signature the line's letters, lower-cased, each run of digits folded to one '#', so "Chapter 5 · 33"
 *   and "Chapter 5 · 34" match.
 * @property letters how many letters the signature holds.
 * @property folios page numbers the line carries (Folios).
 * @property standaloneFolio the whole line is a page number ("12", "Page 3 of 50", "– 7 –").
 */
internal data class MarginLine(
    val line: TextLine,
    val box: TextBox,
    val signature: String,
    val letters: Int,
    val folios: List<Int>,
    val standaloneFolio: Boolean,
)

internal class PageProfiler(private val config: CleanupConfig) {
    fun profile(page: PageText, layout: PageLayout): PageProfile {
        val lines = layout.blocks.flatMap { block -> block.paragraphs.flatMap { it.lines } }.filter { it.box != null }
        val extent = lines.mapNotNull { it.box }.reduceOrNull(TextBox::union)
        val k = config.maxMarginLines
        val top = lines.sortedByDescending { requireNotNull(it.box).top }.take(k).map { marginLine(page, it) }
        val bottom = lines.sortedBy { requireNotNull(it.box).bottom }.take(k).map { marginLine(page, it) }
        return PageProfile(page.pageIndex, extent, lines.size, top, bottom, hyphenatedPairs(page.text))
    }

    private fun marginLine(page: PageText, line: TextLine): MarginLine {
        val spans = line.spans.map { page.text.substring(it.start, it.end) }
        val folios = Folios.of(spans, config.maxFolioDigits)
        val signature = signature(spans.joinToString(" "))
        return MarginLine(
            line = line,
            box = requireNotNull(line.box),
            signature = signature,
            letters = signature.count { it != DIGITS },
            folios = folios.values,
            standaloneFolio = folios.standalone,
        )
    }

    private fun signature(text: String): String {
        val out = StringBuilder(text.length)
        text.codePoints().forEach { cp ->
            when {
                Character.isLetter(cp) -> out.appendCodePoint(Character.toLowerCase(cp))
                Character.isDigit(cp) -> if (out.isEmpty() || out.last() != DIGITS) out.append(DIGITS)
            }
        }
        return out.toString()
    }

    private companion object {
        const val DIGITS = '#'
    }
}

/** Lower-case "left-right" for every hyphen between two letters in [text]; U+2010 counts as a hyphen. */
internal fun hyphenatedPairs(text: String): Set<String> {
    val pairs = HashSet<String>()
    for (match in HYPHENATED.findAll(text)) {
        val parts = match.value.lowercase().split('-', '\u2010')
        for (i in 0 until parts.size - 1) pairs.add(parts[i] + "-" + parts[i + 1])
    }
    return pairs
}

private val HYPHENATED = Regex("\\p{L}+(?:[-\u2010]\\p{L}+)+")
