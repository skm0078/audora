// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/TextBox.kt
// Purpose: A rectangle on a PDF page, used to highlight the sentence being spoken (P4-03).

package io.github.skm0078.audora.core.model

/**
 * Axis-aligned box in PDF user-space points, origin at the bottom-left of the page, so [top] >= [bottom].
 * Pure Kotlin (no android.graphics.RectF) so :core stays a JVM module.
 */
data class TextBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = top - bottom

    fun union(other: TextBox): TextBox = TextBox(
        left = minOf(left, other.left),
        top = maxOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = minOf(bottom, other.bottom),
    )
}
