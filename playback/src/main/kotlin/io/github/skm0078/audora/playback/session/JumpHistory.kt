// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/JumpHistory.kt
// Purpose: Remembers where reading was before each jump, so a wrong tap or a pocket skip is one tap to undo (item 78).

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.config.JumpConfig
import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * A place in the open book: the sentence starting at [charStart] on [page]. [index] is its unit index when the spot
 * came from a unit: a zero-length table summary and the first row share an offset, and only the index tells them
 * apart when reading rebuilds there ([io.github.skm0078.audora.playback.book.PageCursor]).
 */
data class Spot(val page: Int, val charStart: Int, val index: Int? = null)

/** Where this unit is, its index included. */
fun ReadingUnit.spot(): Spot = Spot(page, charStart, index)

/** The same sentence: the same offset, and the same unit when both spots know theirs. */
fun Spot.sameSentence(other: Spot): Boolean = page == other.page && charStart == other.charStart &&
    (index == null || other.index == null || index == other.index)

/** What moved reading: a tap or chapter in the app (a new start), or a skip (headset, notification, car, buttons). */
enum class JumpOrigin { START, SKIP }

/** One jump, [from] where reading was to where it went. */
data class Jump(val from: Spot, val to: Spot, val origin: JumpOrigin, val atMillis: Long)

/**
 * Jumps within [JumpConfig.mergeWindowMs] of the last one extend it: it keeps its first [Jump.from], so undo goes back
 * past the whole burst. Undo pops: undoing twice goes back two jumps. Only the open book's jumps count; a new book
 * clears the history ([clear]). Main thread.
 */
class JumpHistory(private val config: JumpConfig = JumpConfig()) {
    private val jumps = ArrayDeque<Jump>()

    /** The jump an undo would reverse, if any. */
    val last: Jump? get() = jumps.lastOrNull()

    /** Records a jump; a "jump" to where reading already was is no jump. */
    fun record(from: Spot, to: Spot, origin: JumpOrigin, atMillis: Long) {
        if (from.sameSentence(to)) return
        val previous = jumps.lastOrNull()
        if (previous != null && atMillis - previous.atMillis <= config.mergeWindowMs) {
            jumps[jumps.lastIndex] = previous.copy(to = to, atMillis = atMillis)
            // Skipped back to where the burst began: nothing to undo.
            if (previous.from.sameSentence(to)) jumps.removeLast()
            return
        }
        jumps.addLast(Jump(from, to, origin, atMillis))
        while (jumps.size > config.keep) jumps.removeFirst()
    }

    /** Where to go to undo the last jump, forgetting it; null when there is nothing to undo. */
    fun undo(): Spot? = jumps.removeLastOrNull()?.from

    fun clear() = jumps.clear()
}
