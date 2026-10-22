// File: core/src/main/kotlin/io/github/skm0078/audora/core/tutor/WordAligner.kt
// Purpose: Lines up a stretch of heard words with the page's words from the next unread one (plan item 67).

package io.github.skm0078.audora.core.tutor

import io.github.skm0078.audora.core.config.TutorConfig
import kotlin.math.max

/** How the tutor marks a page word. */
enum class WordMark {
    /** Not reached yet. */
    UNREAD,

    /** Heard as written. */
    READ,

    /** Something else was heard in its place (or the recogniser misheard it: the tutor says "check", not "wrong"). */
    MISREAD,

    /** Passed over: words after it were heard. */
    SKIPPED,

    /** Reached, but not judged (a number, a symbol, another script). */
    PASSED,
}

/**
 * [marks] for the page words from the start index on, in order; [next] the first page word after them; [cost] how
 * badly the stretch fits there (0 when every heard word matched).
 */
internal data class Alignment(val marks: List<WordMark>, val next: Int, val cost: Int)

/**
 * An edit-distance alignment of the heard words against the page's words from [from], over at most as many page
 * words as were heard plus [TutorConfig.lookaheadWords]. A heard word matching a page word costs nothing; a mismatch
 * and an extra heard word (a repeat, a filler) cost [TutorConfig.misreadCost] and [TutorConfig.extraCost]; a run of
 * page words passed over costs [TutorConfig.skipOpenCost] plus [TutorConfig.skipExtendCost] per further word (an
 * affine gap, Gotoh's algorithm), so a skipped line is found instead of being read as a row of mismatches. A word the
 * tutor does not judge costs nothing to pass over and may take up to [TutorConfig.maxNumberWords] heard words, each
 * after the first at [TutorConfig.numberWordCost].
 *
 * The end is free: page words after the last one matched stay unread, so a stretch cut mid-sentence marks only what
 * was read. Ties go to the shorter reach (a last word heard wrong stays unread, and is marked skipped if the next
 * stretch goes on after it), except with [align]'s `toPageEnd` when reaching the page's last word costs no more: a
 * final stretch whose last word was misread there marks it, so the page can finish (unless that heard word repeats
 * the page word before the last, a filler such as "on the the").
 */
internal class WordAligner(private val config: TutorConfig) {
    fun align(page: List<PageWord>, from: Int, heard: List<String>, toPageEnd: Boolean = false): Alignment {
        val spoken = heard.take(config.maxUtteranceWords)
        val reach = minOf(page.size - from, spoken.size + config.lookaheadWords)
        if (reach <= 0 || spoken.isEmpty()) return Alignment(emptyList(), from, spoken.size * config.extraCost)
        val table = Table(page.subList(from, from + reach), spoken, freeStart = false)
        val costs = table.main[spoken.size]
        val best = costs.min()
        // A last heard word that repeats the word before the page's last ("on the the") is an extra, not a misread.
        val repeatsWordBefore = page.getOrNull(page.size - 2)?.let { similar(it.key, spoken.last()) } == true
        val reachesPageEnd = toPageEnd && from + reach == page.size && costs[reach] == best && !repeatsWordBefore
        val end = if (reachesPageEnd) reach else (0..reach).first { costs[it] == best }
        return Alignment(table.trace(end), from + end, best)
    }

    /**
     * How well the heard words fit somewhere among the up to [TutorConfig.lookaheadWords] page words before [until]
     * (start and end both free): a reader going back over what they read. Null when there is nothing before.
     */
    fun repeatCost(page: List<PageWord>, until: Int, heard: List<String>): Int? {
        val spoken = heard.take(config.maxUtteranceWords)
        val from = maxOf(0, until - config.lookaheadWords)
        if (until <= from || spoken.isEmpty()) return null
        return Table(page.subList(from, until), spoken, freeStart = true).main[spoken.size].min()
    }

    /**
     * main[i][j]: the least cost of lining up the first i heard words with the first j page words; gap[i][j]: the same,
     * ending in a run of passed-over page words. With [freeStart] the heard words may begin at any page word.
     */
    private inner class Table(
        private val words: List<PageWord>,
        private val spoken: List<String>,
        freeStart: Boolean,
    ) {
        val main = Array(spoken.size + 1) { IntArray(words.size + 1) }
        private val gap = Array(spoken.size + 1) { IntArray(words.size + 1) { UNREACHABLE } }

        init {
            for (i in 0..spoken.size) {
                for (j in 0..words.size) {
                    if (j > 0) gap[i][j] = gapSteps(i, j).minOf { it.cost }
                    main[i][j] = if (i == 0 && (j == 0 || freeStart)) 0 else mainSteps(i, j).minOf { it.cost }
                }
            }
        }

        fun trace(end: Int): List<WordMark> {
            val marks = MutableList(end) { WordMark.UNREAD }
            var at = Step(spoken.size, end, main[spoken.size][end], mark = null, inGap = false)
            while (at.i > 0 || at.j > 0) {
                val target = at.cost
                val steps = if (at.inGap) gapSteps(at.i, at.j) else mainSteps(at.i, at.j)
                val step = steps.first { it.cost == target }
                step.mark?.let { marks[at.j - 1] = it }
                at =
                    Step(
                        step.i,
                        step.j,
                        if (step.inGap) gap[step.i][step.j] else main[step.i][step.j],
                        null,
                        step.inGap,
                    )
            }
            return marks
        }

        /** Every way into main (i, j), in the order ties are broken; each step's cost is the total through it. */
        private fun mainSteps(i: Int, j: Int): List<Step> = buildList {
            if (i > 0 && j > 0) {
                val word = words[j - 1]
                val mark = if (!word.checked) WordMark.PASSED else markOf(word.key, spoken[i - 1])
                val cost = if (mark == WordMark.MISREAD) config.misreadCost else 0
                add(Step(i - 1, j - 1, main[i - 1][j - 1] + cost, mark, inGap = false))
            }
            if (j > 0 && !words[j - 1].checked) {
                for (taken in 2..minOf(i, config.maxNumberWords)) {
                    val cost = main[i - taken][j - 1] + (taken - 1) * config.numberWordCost
                    add(Step(i - taken, j - 1, cost, WordMark.PASSED, inGap = false))
                }
                add(Step(i, j - 1, main[i][j - 1], WordMark.PASSED, inGap = false))
            }
            if (j > 0) add(Step(i, j, gap[i][j], mark = null, inGap = true))
            if (i > 0) add(Step(i - 1, j, main[i - 1][j] + config.extraCost, mark = null, inGap = false))
        }

        /** Every way into gap (i, j): opening or extending a run with page word j - 1. */
        private fun gapSteps(i: Int, j: Int): List<Step> = if (words[j - 1].checked) {
            listOf(
                Step(i, j - 1, add(gap[i][j - 1], config.skipExtendCost), WordMark.SKIPPED, inGap = true),
                Step(i, j - 1, main[i][j - 1] + config.skipOpenCost, WordMark.SKIPPED, inGap = false),
            )
        } else {
            listOf(Step(i, j - 1, gap[i][j - 1], WordMark.PASSED, inGap = true))
        }
    }

    private fun markOf(key: String, heard: String): WordMark =
        if (similar(key, heard)) WordMark.READ else WordMark.MISREAD

    private fun similar(a: String, b: String): Boolean {
        val longer = max(a.length, b.length)
        return a == b ||
            (longer > config.exactUpToChars && 1.0 - distance(a, b).toDouble() / longer >= config.matchSimilarity)
    }

    /**
     * One way into a cell: from (i, j) in the main or gap table, at [cost] in total, marking the page word it takes
     * with [mark] (none for an extra heard word or for moving between tables).
     */
    private data class Step(val i: Int, val j: Int, val cost: Int, val mark: WordMark?, val inGap: Boolean)

    companion object {
        private const val UNREACHABLE = Int.MAX_VALUE / 2

        /** A cost plus [extra], staying unreachable if it was. */
        private fun add(cost: Int, extra: Int) = if (cost >= UNREACHABLE) UNREACHABLE else cost + extra

        /** Levenshtein distance over characters (code units; keys are NFKC, so marks are their own units). */
        fun distance(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val substitute = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(substitute, previous[j] + 1, current[j - 1] + 1)
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }
    }
}
