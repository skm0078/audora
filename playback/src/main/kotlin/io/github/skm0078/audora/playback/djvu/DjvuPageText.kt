// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/djvu/DjvuPageText.kt
// Purpose: A DjVu page's hidden text (its OCR layer) as headings and paragraphs to read aloud
//          (ADR 2026-10-27-djvu-books).

package io.github.skm0078.audora.playback.djvu

import io.github.skm0078.audora.playback.text.BlockKind
import io.github.skm0078.audora.playback.text.TextBlock

/**
 * The text layer is a tree of zones: page, column, region, paragraph, line, word, character, each with its box (y
 * counts up from the page's foot). Lines are what every OCR tool writes; paragraphs only some. So:
 * - with paragraph zones, each is a block;
 * - without, lines are joined into paragraphs by their spacing: a gap clearly wider than the page's usual one (its
 *   lower quartile), or a short line that ends a sentence, ends a paragraph;
 * - a one-line paragraph set clearly larger than the page's usual line is a heading;
 * - a line that is only a page number (digits or a roman numeral) is dropped;
 * - a word broken by a hyphen at a line's end is joined again.
 * A page whose text is one flat string (no zones below the page) is split at DjVu's own separators instead.
 */
object DjvuPageText {
    private const val WIDE_GAP = 1.6
    private const val SHORT_LINE = 0.75
    private const val HEADING_HEIGHT = 1.2
    private const val HEADING_MAX_CHARS = 120
    private val SENTENCE_END = setOf('.', '!', '?', ':', '।', '॥', '"', '”', '’', ')')
    private val PAGE_NUMBER = Regex("""^(\d{1,4}|[ivxlcdm]{1,7}|[IVXLCDM]{1,7})$""")
    private val HYPHENS = setOf('-', '‐', '­')

    // DjVu's flat text: column, region and paragraph separators, then line breaks.
    private val FLAT_PARAGRAPH = Regex("[\u000B\u001D\u001F]+|\n[ \t]*\n")

    /** The page's blocks, in reading order; empty for a page with no text layer. */
    fun blocks(page: DjvuExpression?): List<TextBlock> = (page as? DjvuExpression.Items)?.let(::zoneBlocks).orEmpty()

    private fun zoneBlocks(root: DjvuExpression.Items): List<TextBlock> {
        val lines = LineCollector().apply { collect(root, paragraph = NO_PARAGRAPH) }.lines
        val paragraphs = when {
            lines.isEmpty() -> return flatBlocks(root)
            lines.any { it.paragraph != NO_PARAGRAPH } -> lines.groupBy { it.paragraph }.values.toList()
            else -> bySpacing(lines)
        }
        val usualHeight = median(lines.map { it.height })
        return paragraphs.mapNotNull { block(it, usualHeight) }
    }

    private class Line(val text: String, val paragraph: Int, val box: Box) {
        val height: Int get() = box.top - box.bottom
        val width: Int get() = box.right - box.left
    }

    private data class Box(val left: Int, val bottom: Int, val right: Int, val top: Int)

    /** Gathers the lines in reading order, each with the number of the paragraph zone around it (if any). */
    private class LineCollector {
        val lines = ArrayList<Line>()
        private var paragraphs = 0

        fun collect(zone: DjvuExpression.Items, paragraph: Int) {
            val kind = zone.head
            if (kind == "line") {
                val text = words(zone).joinToString(" ").trim()
                if (text.isNotEmpty()) lines += Line(text, paragraph, box(zone))
                return
            }
            val inner = if (kind == "para") paragraphs++ else paragraph
            zone.items.filterIsInstance<DjvuExpression.Items>().forEach { collect(it, inner) }
        }
    }

    /** A line's words: its word zones' strings, or its own string; character zones run together into words. */
    private fun words(zone: DjvuExpression.Items): List<String> {
        val strings = zone.items.filterIsInstance<DjvuExpression.Text>().map { it.value }
        val children = zone.items.filterIsInstance<DjvuExpression.Items>()
        return when {
            children.isEmpty() -> strings
            children.all { it.head == "char" } -> listOf(children.joinToString("") { words(it).joinToString("") })
            else -> children.flatMap { words(it) }
        }
    }

    private fun box(zone: DjvuExpression.Items): Box {
        val numbers = zone.items.drop(1).take(BOX_VALUES).map { (it as? DjvuExpression.Number)?.value ?: 0 }
        if (numbers.size != BOX_VALUES) return Box(0, 0, 0, 0)
        return Box(numbers[LEFT], numbers[BOTTOM], numbers[RIGHT], numbers[TOP])
    }

    private fun bySpacing(lines: List<Line>): List<List<Line>> {
        // The lower quartile, not the median: on a short page the paragraph gaps can be half of all gaps.
        val gaps = lines.zipWithNext { above, below -> above.box.bottom - below.box.top }.filter { it >= 0 }.sorted()
        val usualGap = gaps.getOrNull(gaps.size / QUARTILE) ?: 0
        val widest = lines.maxOf { it.width }
        val paragraphs = ArrayList<MutableList<Line>>()
        lines.forEachIndexed { index, line ->
            val previous = lines.getOrNull(index - 1)
            val breaks = previous == null ||
                previous.box.bottom - line.box.top > usualGap * WIDE_GAP + 1 ||
                (previous.width < widest * SHORT_LINE && previous.text.last() in SENTENCE_END)
            if (breaks) paragraphs += mutableListOf(line) else paragraphs.last() += line
        }
        return paragraphs
    }

    private fun block(lines: List<Line>, usualHeight: Int): TextBlock? {
        val kept = lines.filterNot { PAGE_NUMBER.matches(it.text) }
        if (kept.isEmpty()) return null
        val text = joined(kept.map { it.text })
        val heading = kept.size == 1 && text.length <= HEADING_MAX_CHARS &&
            kept[0].height > usualHeight * HEADING_HEIGHT
        return TextBlock(if (heading) BlockKind.HEADING else BlockKind.BODY, text)
    }

    /** Lines joined with spaces; "remark-" then "able" is "remarkable". */
    private fun joined(lines: List<String>): String {
        val out = StringBuilder()
        for (line in lines) {
            val glued = out.isNotEmpty() && out.last() in HYPHENS && line.first().isLowerCase()
            if (glued) {
                out.setLength(out.length - 1)
            } else if (out.isNotEmpty()) {
                out.append(' ')
            }
            out.append(line)
        }
        return out.toString().replace(WHITESPACE, " ").trim()
    }

    private fun flatBlocks(root: DjvuExpression.Items): List<TextBlock> {
        val text = root.items.filterIsInstance<DjvuExpression.Text>().joinToString("\n") { it.value }
        return text.split(FLAT_PARAGRAPH)
            .map { paragraph -> paragraph.lines().map { it.trim() }.filter(::isTextLine) }
            .filter { it.isNotEmpty() }
            .map { TextBlock(BlockKind.BODY, joined(it)) }
    }

    private fun isTextLine(line: String): Boolean = line.isNotEmpty() && !PAGE_NUMBER.matches(line)

    private fun median(values: List<Int>): Int = values.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }

    private const val BOX_VALUES = 4
    private const val LEFT = 0
    private const val BOTTOM = 1
    private const val RIGHT = 2
    private const val TOP = 3
    private const val QUARTILE = 4
    private const val NO_PARAGRAPH = -1
    private val WHITESPACE = Regex("\\s+")
}
