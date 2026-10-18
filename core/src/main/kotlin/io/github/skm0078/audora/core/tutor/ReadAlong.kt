// File: core/src/main/kotlin/io/github/skm0078/audora/core/tutor/ReadAlong.kt
// Purpose: Follows a reader through one page from what a recogniser hears, and sums it up (plan item 67).

package io.github.skm0078.audora.core.tutor

import io.github.skm0078.audora.core.config.TutorConfig

/**
 * The tutor's view of one page, immutable. Recognisers report a stretch of speech (until a pause) as it grows, then
 * once more as final: [heard] lines the whole stretch up again from where the last final one ended, so a word the
 * recogniser corrects mid-stretch is re-marked, and only a final stretch moves the starting point on.
 *
 * @property marks one per page word, in order.
 * @property next the first page word not yet reached: where the reader is.
 */
class ReadAlong private constructor(
    val words: List<PageWord>,
    val marks: List<WordMark>,
    val next: Int,
    private val settled: Settled,
    private val aligner: WordAligner,
) {
    /** The marks and position as of the last final stretch. */
    private data class Settled(val marks: List<WordMark>, val next: Int)

    /** The reader reached the page's last word. */
    val finished: Boolean get() = next >= words.size

    /** [heardWords] the current stretch's words so far (all of them, each time); [final] once it ended. */
    fun heard(heardWords: List<String>, final: Boolean): ReadAlong {
        val aligned = aligner.align(words, settled.next, heardWords)
        val marked = settled.marks.toMutableList()
        aligned.marks.forEachIndexed { offset, mark -> marked[settled.next + offset] = mark }
        val moved = Settled(marked, aligned.next)
        return ReadAlong(words, marked, aligned.next, if (final) moved else settled, aligner)
    }

    /** What the reader did on this page, from the settled stretches and the current one. */
    fun report(): TutorReport = TutorReport(
        read = marks.count { it == WordMark.READ },
        toCheck = words.indices.filter { marks[it] == WordMark.MISREAD },
        skipped = words.indices.filter { marks[it] == WordMark.SKIPPED },
        judged = marks.count { it == WordMark.READ || it == WordMark.MISREAD || it == WordMark.SKIPPED },
    )

    companion object {
        fun start(words: List<PageWord>, config: TutorConfig = TutorConfig()): ReadAlong {
            val unread = List(words.size) { WordMark.UNREAD }
            return ReadAlong(words, unread, 0, Settled(unread, 0), WordAligner(config))
        }
    }
}

/**
 * @property read words heard as written.
 * @property toCheck indices of words heard as something else (worth practising, or misheard by the recogniser).
 * @property skipped indices of words passed over.
 * @property judged every word read, to check or skipped; numbers, symbols and other scripts are left out.
 */
data class TutorReport(val read: Int, val toCheck: List<Int>, val skipped: List<Int>, val judged: Int) {
    /** Words read as written out of the words judged, 0–100; null before any. */
    val accuracyPercent: Int? get() = if (judged == 0) null else read * PERCENT / judged

    private companion object {
        const val PERCENT = 100
    }
}
