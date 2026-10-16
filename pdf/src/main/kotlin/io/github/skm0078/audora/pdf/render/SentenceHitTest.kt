// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/render/SentenceHitTest.kt
// Purpose: Finds the sentence under a tap on the rendered page from its line boxes (P4-03).

package io.github.skm0078.audora.pdf.render

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TextBox
import kotlin.math.hypot
import kotlin.math.max

object SentenceHitTest {
    /**
     * The unit whose line boxes ([ReadingUnit.bounds]) are nearest to [point], if one is within [slopPoints].
     * A finger is wider than a line of 10 pt text, so a tap in the gap between two lines still picks one; the
     * nearest box wins, so a tap on a box always picks that box's sentence. Units without boxes (OCR'd text)
     * cannot be tapped on the page.
     */
    fun unitAt(units: List<ReadingUnit>, point: PagePoint, slopPoints: Float): ReadingUnit? {
        var best: ReadingUnit? = null
        var bestDistance = Float.MAX_VALUE
        for (unit in units) {
            val distance = unit.bounds?.minOfOrNull { distance(it, point) } ?: continue
            if (distance < bestDistance) {
                best = unit
                bestDistance = distance
            }
        }
        return best?.takeIf { bestDistance <= slopPoints }
    }

    /** 0 inside the box, else the distance to its nearest edge or corner. */
    private fun distance(box: TextBox, point: PagePoint): Float {
        val dx = max(max(box.left - point.x, point.x - box.right), 0f)
        val dy = max(max(box.bottom - point.y, point.y - box.top), 0f)
        return hypot(dx, dy)
    }
}
