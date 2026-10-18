// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ColumnCue.kt
// Purpose: The layout cue for a sentence carried over a page break in a script without capitals: full line, no indent.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.PageCarryConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.layout.TextTurn

/**
 * Hindi and Bengali have no capitals to show that a page goes on with the sentence the page before broke off, so the
 * layout shows it ([PageCarry]): the sentence's last line runs to the column's edge and the next page's first line is
 * not indented. Lines are measured in the page's reading frame ([readingTurn]: a sideways scan's boxes are turned
 * upright) against at least `PageCarryConfig.minColumnLines` other prose lines of the column, never against
 * themselves alone.
 */
internal class ColumnCue(private val config: PageCarryConfig) {
    /**
     * [unit]'s first line starts more than `maxIndentEm` line heights in from the left edge of its column on [page];
     * true when that cannot be told.
     */
    fun isIndented(page: List<ReadingUnit>, unit: ReadingUnit): Boolean =
        framed(page, unit.bounds?.firstOrNull())?.let { (first, others) ->
            first.left - others.minOf { it.left } > first.height * config.maxIndentEm
        } ?: true

    /**
     * [open]'s last line reaches the usual right edge of its column on [page] (the median of the other lines'), to
     * within (1 − `fullLineShare`) of the widest line; false when that cannot be told. Its box covers the sentence
     * only, so it is its right end that counts.
     */
    fun endsFullLine(page: List<ReadingUnit>, open: ReadingUnit): Boolean =
        framed(page, open.bounds?.lastOrNull())?.let { (last, others) ->
            val usualRight = others.map { it.right }.sorted()[(others.size - 1) / 2]
            last.right >= usualRight - others.maxOf { it.width } * (1f - config.fullLineShare)
        } ?: false

    /** [line] and the other prose lines of its column on [page], upright; null when too few or the frame is unknown. */
    private fun framed(page: List<ReadingUnit>, line: TextBox?): Pair<TextBox, List<TextBox>>? {
        val turn = readingTurn(page)
        val upright = line?.let { turn?.apply(it) }
        val others = upright?.let { box ->
            page.filter { it.kind in PROSE }.flatMap { it.bounds.orEmpty() }.filter { it != line }
                .map { requireNotNull(turn).apply(it) }
                .filter { minOf(it.right, box.right) > maxOf(it.left, box.left) }
        }
        return if (upright != null && others != null &&
            others.size >= config.minColumnLines
        ) {
            upright to others
        } else {
            null
        }
    }
}

/**
 * The turn that sets [page]'s lines upright, from how each sentence's lines follow one another (the next line is
 * below in upright text, to the right in text running up the page, and so on); null when the steps disagree evenly,
 * or the page shows no step and its lines are not mostly wider than tall (a sideways page of one-line sentences).
 */
internal fun readingTurn(page: List<ReadingUnit>): TextTurn? {
    val votes = page.flatMap { unit -> unit.bounds.orEmpty().zipWithNext(::stepTurn) }.groupingBy { it }.eachCount()
    val lines = page.flatMap { it.bounds.orEmpty() }
    val best = votes.maxByOrNull { it.value }
    return when {
        best == null -> TextTurn.NONE.takeIf { lines.count { it.width >= it.height } * 2 > lines.size }
        votes.count { it.value == best.value } > 1 -> null
        else -> best.key
    }
}

/**
 * The turn under which line [b] comes below line [a]: a line wider than tall runs across the page, so the next one
 * is below it (or above, upside down); a taller one runs up or down the page, so the next is beside it. Only the step
 * across the lines counts, so lines of different lengths do not mislead it.
 */
private fun stepTurn(a: TextBox, b: TextBox): TextTurn {
    val across = a.width >= a.height
    val step = if (across) b.top + b.bottom - a.top - a.bottom else b.left + b.right - a.left - a.right
    return when {
        across -> if (step < 0f) TextTurn.NONE else TextTurn.HALF
        step > 0f -> TextTurn.CLOCKWISE
        else -> TextTurn.COUNTER_CLOCKWISE
    }
}
