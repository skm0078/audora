// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/JumpHistory.kt
// Purpose: Remembers where reading was before each jump, so a wrong tap or a pocket skip is one tap to undo (item 78).

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.config.JumpConfig

/** A place in the open book: the sentence starting at [charStart] on [page]. */
data class Spot(val page: Int, val charStart: Int)

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
        if (from == to) return
        val previous = jumps.lastOrNull()
        if (previous != null && atMillis - previous.atMillis <= config.mergeWindowMs) {
            jumps[jumps.lastIndex] = previous.copy(to = to, atMillis = atMillis)
            if (previous.from == to) jumps.removeLast() // Skipped back to where the burst began: nothing to undo.
            return
        }
        jumps.addLast(Jump(from, to, origin, atMillis))
        while (jumps.size > config.keep) jumps.removeFirst()
    }

    /** Where to go to undo the last jump, forgetting it; null when there is nothing to undo. */
    fun undo(): Spot? = jumps.removeLastOrNull()?.from

    fun clear() = jumps.clear()
}
