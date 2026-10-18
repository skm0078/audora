// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PageCarryConfig.kt
// Purpose: Tunables for carrying a table or a sentence over a page break (RI-4a, plan item 7).

package io.github.skm0078.audora.core.config

/**
 * @property minHeaderMatchCells a table opening a page repeats the header of the table the page before ended with
 *   when more than half of its header cells, and at least this many, read alike.
 * @property minRowFormatShare a table's first row is data rather than a header when, in at least this share of the
 *   columns whose data rows are mostly written one way ("9", "9,9", "a"), it is written that way too.
 * @property minBrokenWords a page ends inside a sentence only when its last sentence has at least this many words
 *   (a page number, running footer or print slug kept at its foot has fewer).
 * @property fullLineShare in a script without capitals, a page ends inside a paragraph only when its last line
 *   reaches the column's right edge, to within (1 − this share) of the column's width (a paragraph's last line stops
 *   short)
 * @property maxIndentEm and the next page's first line starts within this many line heights of the paragraph's
 *   other lines (a new paragraph is indented).
 * @property maxCarryPages a page read out of order works out what the pages before it hand on, one after another,
 *   at most this far back (a table running over more pages numbers its rows from that far back only).
 */
data class PageCarryConfig(
    val minHeaderMatchCells: Int = 2,
    val minRowFormatShare: Float = 0.75f,
    val minBrokenWords: Int = 3,
    val fullLineShare: Float = 0.9f,
    val maxIndentEm: Float = 0.5f,
    val maxCarryPages: Int = 10,
) {
    init {
        require(minHeaderMatchCells >= 1) { "minHeaderMatchCells must be >= 1, was $minHeaderMatchCells" }
        require(minRowFormatShare in 0f..1f && fullLineShare in 0f..1f) { "carry shares must be in 0..1" }
        require(minBrokenWords >= 1) { "minBrokenWords must be >= 1, was $minBrokenWords" }
        require(maxIndentEm >= 0f) { "maxIndentEm must be >= 0, was $maxIndentEm" }
        require(maxCarryPages >= 0) { "maxCarryPages must be >= 0, was $maxCarryPages" }
    }
}
