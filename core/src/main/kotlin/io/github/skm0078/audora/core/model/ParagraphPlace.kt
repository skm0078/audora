// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/ParagraphPlace.kt
// Purpose: Where a sentence sits in its paragraph, for the narration's rise and fall (Q-3, ADR natural-narration).

package io.github.skm0078.audora.core.model

/**
 * The sentence is number [index] (0-based) of the [size] sentences of its paragraph on the page; [afterHeading]: the
 * paragraph comes straight after a heading, so its first sentence opens a section.
 */
data class ParagraphPlace(val index: Int, val size: Int, val afterHeading: Boolean = false) {
    init {
        require(size >= 1 && index in 0 until size) { "need 0 <= index < size, was $index of $size" }
    }

    val first: Boolean get() = index == 0
    val last: Boolean get() = index == size - 1
}
