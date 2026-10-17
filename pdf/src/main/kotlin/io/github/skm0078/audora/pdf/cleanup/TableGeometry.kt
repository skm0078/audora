// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableGeometry.kt
// Purpose: Box tests shared by the table finder, its header band and the grid: union, row overlap, pairing, pitch.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.layout.TextLine

/** The box around the lines that have one; null when none has. */
internal fun Collection<TextLine>.unionBox(): TextBox? = mapNotNull { it.box }.reduceOrNull(TextBox::union)

/** [a] and [b] sit on one row: they overlap vertically by [ratio] of the smaller height. */
internal fun overlapsVertically(a: TextBox, b: TextBox, ratio: Float): Boolean {
    val overlap = minOf(a.top, b.top) - maxOf(a.bottom, b.bottom)
    return overlap >= minOf(a.height, b.height) * ratio
}

/** The one of [rows] that [line] sits on ([overlapsVertically] by [ratio]); null when it sits on none or several. */
internal fun pairedRow(line: TextLine, rows: List<TextLine>, ratio: Float): Int? = line.box?.let { box ->
    rows.indices.singleOrNull { rows[it].box?.let { row -> overlapsVertically(box, row, ratio) } == true }
}

/** Whether [line] sits beside any of [others] on one row ([overlapsVertically] by [ratio]). */
internal fun besideAny(line: TextLine, others: Collection<TextLine>, ratio: Float): Boolean = line.box?.let { box ->
    others.any { other -> other.box?.let { overlapsVertically(box, it, ratio) } == true }
} == true

/** The median distance between the tops of consecutive rows (boxes side by side are one row); null under two rows. */
internal fun typicalPitch(boxes: List<TextBox>, ratio: Float): Float? {
    val rows = ArrayList<TextBox>()
    for (box in boxes.sortedByDescending { it.top }) {
        if (rows.isEmpty() || !overlapsVertically(rows.last(), box, ratio)) rows.add(box)
    }
    return if (rows.size < 2) null else rows.zipWithNext { a, b -> a.top - b.top }.sorted()[(rows.size - 2) / 2]
}
