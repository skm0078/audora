// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableGeometry.kt
// Purpose: Box tests shared by the table finder, its header band and the grid: union, row overlap, row pitch.

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

/** The median distance between the tops of consecutive rows (boxes side by side are one row); null under two rows. */
internal fun typicalPitch(boxes: List<TextBox>, ratio: Float): Float? {
    val rows = ArrayList<TextBox>()
    for (box in boxes.sortedByDescending { it.top }) {
        if (rows.isEmpty() || !overlapsVertically(rows.last(), box, ratio)) rows.add(box)
    }
    return if (rows.size < 2) null else rows.zipWithNext { a, b -> a.top - b.top }.sorted()[(rows.size - 2) / 2]
}
