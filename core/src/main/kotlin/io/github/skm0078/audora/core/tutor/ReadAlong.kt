// File: core/src/main/kotlin/io/github/skm0078/audora/core/tutor/ReadAlong.kt
// Purpose: Follows a reader through one page from what a recogniser hears, and sums it up (plan item 67).

package io.github.skm0078.audora.core.tutor

import io.github.skm0078.audora.core.config.TutorConfig

/**
 * The tutor's view of one page, immutable. Recognisers report a stretch of speech (until a pause) as it grows, then
 * once more as final: [heard] lines the whole stretch up again from where the last final one ended, so a word the
 * recogniser corrects mid-stretch is re-marked, and only a final stretch moves the starting point on. A stretch of at
 * least [TutorConfig.minRepeatWords] words, most of which match the words just read and better than the words ahead,
 * is the reader going back over them: it changes nothing.
 *
 * Heard words a final stretch left unexplained (a stretch that did not move the place, or extras after its last
 * matched word) are carried into the next stretch, up to [TutorConfig.carryWords]. A slow reader pauses after every
 * word or two, and one word alone can never pay for passing over a word the recogniser missed (a skip costs more
 * than an extra word); without the carry the place stopped moving for good. Once enough words are carried, a final
 * stretch that matches well further on moves the place there (WordAligner.resync).
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
    private val config: TutorConfig,
) {
    /** How [follow] took a stretch: it moved the place on, held it (nothing matched ahead), or was a re-read. */
    enum class Taken { MOVED, HELD, REPEAT }

    /** The page after a stretch, how it was taken, and how many heard words (carried ones included) were lined up. */
    class Step(val along: ReadAlong, val taken: Taken, val words: Int)

    /** The marks and position as of the last final stretch, and the heard words it left unexplained. */
    private data class Settled(val marks: List<WordMark>, val next: Int, val carried: List<String> = emptyList())

    /** The reader reached the page's last judged word (numbers or other-script words after it may be left out). */
    val finished: Boolean get() = words.subList(next, words.size).none { it.checked }

    /** [heardWords] the current stretch's words so far (all of them, each time); [final] once it ended. */
    fun heard(heardWords: List<String>, final: Boolean): ReadAlong = follow(heardWords, final).along

    /** As [heard], also saying how the stretch was taken, for the tutor's log (counts only, never words). */
    fun follow(heardWords: List<String>, final: Boolean): Step {
        if (isRepeat(heardWords)) {
            // What was carried was waiting for words ahead; after going back it no longer leads anywhere.
            val kept = if (final) settled.copy(carried = emptyList()) else settled
            val same = ReadAlong(words, settled.marks, settled.next, kept, aligner, config)
            return Step(same, Taken.REPEAT, heardWords.size)
        }
        val spoken = settled.carried + heardWords
        val ahead = aligner.align(words, settled.next, spoken, toPageEnd = final)
        val aligned = if (final) resyncOr(ahead, spoken) else ahead
        val marked = settled.marks.toMutableList()
        aligned.marks.forEachIndexed { offset, mark -> marked[settled.next + offset] = mark }
        val left = spoken.drop(aligned.used).takeLast(config.carryWords)
        val moved = Settled(marked, aligned.next, left)
        val taken = if (aligned.next > settled.next) Taken.MOVED else Taken.HELD
        val next = ReadAlong(words, marked, aligned.next, if (final) moved else settled, aligner, config)
        return Step(next, taken, spoken.size)
    }

    /**
     * A final stretch of at least [TutorConfig.resyncWords] words that mostly matches further on, better than where the
     * reader was: the place jumps there and the words passed over are marked skipped.
     */
    private fun resyncOr(ahead: Alignment, spoken: List<String>): Alignment {
        // A jump costs at least one skip, so a stretch that fits ahead as well as that never jumps (most of them).
        val jump = if (spoken.size < config.resyncWords || ahead.cost <= config.skipOpenCost) {
            null
        } else {
            aligner.resync(words, settled.next, spoken, toPageEnd = true)
        }
        return jump?.takeIf { it.cost < ahead.cost && mostlyMatched(it.cost, spoken.size) } ?: ahead
    }

    private fun mostlyMatched(cost: Int, heard: Int) = cost <= heard * config.extraCost * (1 - config.repeatMatchShare)

    /**
     * The stretch alone (never what was carried: a carried word fits behind as often as ahead) is long enough, mostly
     * matches words just read, and fits them better than the words ahead.
     */
    private fun isRepeat(heardWords: List<String>): Boolean {
        val repeat = heardWords.takeIf { it.size >= config.minRepeatWords }
            ?.let { aligner.repeatCost(words, settled.next, it) }
        return repeat != null && mostlyMatched(repeat, heardWords.size) &&
            repeat < aligner.align(words, settled.next, heardWords).cost
    }

    /** What the reader did on this page, from the settled stretches and the current one. */
    fun report(): TutorReport = TutorReport(
        read = marks.count { it == WordMark.READ },
        toCheck = words.indices.filter { marks[it] == WordMark.MISREAD },
        skipped = words.indices.filter { marks[it] == WordMark.SKIPPED },
        judged = marks.count { it == WordMark.READ || it == WordMark.MISREAD || it == WordMark.SKIPPED },
    )

    /** The settled marks and place as text, to keep across a screen rotation or process death ([resume]). */
    fun saved(): String = settled.next.toString() + SAVED_SEPARATOR + settled.marks.joinToString("") {
        it.ordinal.toString()
    }

    companion object {
        private const val SAVED_SEPARATOR = ':'

        fun start(words: List<PageWord>, config: TutorConfig = TutorConfig()): ReadAlong {
            val unread = List(words.size) { WordMark.UNREAD }
            return ReadAlong(words, unread, 0, Settled(unread, 0), WordAligner(config), config)
        }

        /** [words] with what [saved] kept; a fresh start when it does not fit them (another page, an older form). */
        fun resume(words: List<PageWord>, saved: String?, config: TutorConfig = TutorConfig()): ReadAlong {
            val next = saved?.substringBefore(SAVED_SEPARATOR, "")?.toIntOrNull()
            val marks = saved?.substringAfter(SAVED_SEPARATOR, "")?.map { WordMark.entries.getOrNull(it - '0') }
            val fits = next != null && next in 0..words.size && marks != null && marks.size == words.size &&
                marks.none { it == null }
            if (!fits) return start(words, config)
            val kept = marks.orEmpty().filterNotNull()
            return ReadAlong(words, kept, next ?: 0, Settled(kept, next ?: 0), WordAligner(config), config)
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
