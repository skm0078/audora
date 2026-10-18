// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/StudyLoop.kt
// Purpose: Study modes (plan item 31): repeat each sentence, pause after it, A–B loops and reading a selection once.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.model.AfterSentence
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.book.spokenEnd
import io.github.skm0078.audora.playback.playlist.PlaybackItem

/**
 * A stretch of the book to study: from the sentence that ends after [start] through the one that starts at or before
 * [end] (a selection's last character, inclusive), as PLAY_FROM picks its first sentence. [loop] reads it again from
 * [start] at its end; otherwise reading pauses there and the range is done ("read a selection only").
 */
data class StudyRange(val start: Spot, val end: Spot, val loop: Boolean) {
    init {
        require(!start.isAfter(end)) { "the range must not end before it starts: $start..$end" }
    }

    operator fun contains(unit: ReadingUnit): Boolean =
        Spot(unit.page, unit.spokenEnd).isAfter(start) && !Spot(unit.page, unit.charStart).isAfter(end)

    /** [unit] holds the range's start: the sentence a loop goes back to. */
    fun startsAt(unit: ReadingUnit): Boolean =
        unit.page == start.page && start.charStart in unit.charStart until unit.spokenEnd

    /** [unit] is the range's last sentence: inside, with nothing after it inside. A page not loaded yet is unknown. */
    fun endsAt(unit: ReadingUnit, next: PlaybackItem?): Boolean = unit in this && when (next) {
        null -> true
        is PlaybackItem.Sentence -> next.unit !in this
        is PlaybackItem.More -> false
    }

    /** Playback went from [previous], inside, straight on to [now], outside. */
    fun leftBy(previous: ReadingUnit?, now: ReadingUnit?): Boolean =
        previous?.let { it in this } == true && now?.let { it !in this } == true
}

/** What playback does when a sentence ends while a study mode is on. */
sealed interface StudyStep {
    /** Read the same sentence again. */
    data object Repeat : StudyStep

    /** Go on to the next sentence. */
    data object Continue : StudyStep

    /** Stay paused ("Pause after each sentence", or a selection read once): play goes on. */
    data object Stop : StudyStep

    /** Back to the range's start. */
    data class LoopTo(val start: Spot) : StudyStep
}

/** Which end of an A–B range a mark sets. */
enum class StudyMark { START, END }

/** What the reader shows of the A–B marks: A, B once set, and whether the range loops. */
data class StudyMarks(val start: Spot, val end: Spot?, val loop: Boolean)

/**
 * The study modes' state for the open book. Repeats and "after each sentence" come from the reading modes, so they
 * are saved like any reading setting; the A–B range is the session's only (a new book clears it). Playback asks
 * [pausesAfter] when it arms pause-at-end-of-item and [onSentenceEnd] when the player paused there. Main thread only.
 */
class StudyLoop {
    /** The A mark, set before B; with no B yet, nothing loops. */
    var start: Spot? = null
        private set

    var range: StudyRange? = null
        private set

    private var heard = 0

    /** Whether a range loops: the last choice, kept for the next B. */
    private var loop = true

    /** For the session extras; null with no A. */
    fun marks(): StudyMarks? = start?.let { StudyMarks(it, range?.end, range?.loop ?: loop) }

    /** The range starts on [unit] (the sentence a loop goes back to). */
    fun startsRange(unit: ReadingUnit): Boolean = range?.startsAt(unit) == true

    /**
     * Marks A or B at [unit], the sentence playing. B before A swaps them, so the range always runs forward; B with no
     * A starts the range at the sentence playing too (a one-sentence loop). False with no sentence playing.
     */
    fun mark(mark: StudyMark, unit: ReadingUnit?, loop: Boolean): Boolean {
        val here = unit?.let { Spot(it.page, it.charStart) } ?: return false
        this.loop = loop
        when (mark) {
            StudyMark.START -> {
                start = here
                // A new A after the old B drops the range: B must be set again.
                range = range?.takeIf { !here.isAfter(it.end) }?.copy(start = here)
            }

            StudyMark.END -> {
                val from = start ?: here
                range = if (here.isAfter(from)) StudyRange(from, here, loop) else StudyRange(here, from, loop)
                start = range?.start
            }
        }
        return true
    }

    /** An exact stretch, such as a selection in the reader. */
    fun set(newRange: StudyRange) {
        range = newRange
        loop = newRange.loop
        start = newRange.start
        heard = 0
    }

    /** Loop on or off for the range set, if any. */
    fun setLoop(loop: Boolean) {
        this.loop = loop
        range = range?.copy(loop = loop)
    }

    fun clear() {
        start = null
        range = null
        heard = 0
    }

    /** A new sentence is playing (not a repeat of the last one): its repeats start from one. */
    fun onNewSentence() {
        heard = 0
    }

    /** Whether the player must pause at the end of [current] so [onSentenceEnd] can decide what comes next. */
    fun pausesAfter(current: PlaybackItem?, next: PlaybackItem?, modes: ReadingModes): Boolean {
        val unit = (current as? PlaybackItem.Sentence)?.unit ?: return false
        return modes.studying || range?.endsAt(unit, next) == true
    }

    /** [unit] has just been read to its end and the player paused there; [next] is the item after it. */
    fun onSentenceEnd(unit: ReadingUnit, next: PlaybackItem?, modes: ReadingModes): StudyStep {
        heard++
        if (heard < modes.sentenceRepeats) return StudyStep.Repeat
        heard = 0
        val ending = range?.takeIf { it.endsAt(unit, next) }
        return when {
            ending != null && ending.loop -> StudyStep.LoopTo(ending.start)
            ending != null -> StudyStep.Stop.also { clear() }
            modes.afterSentence == AfterSentence.STOP -> StudyStep.Stop
            else -> StudyStep.Continue
        }
    }

    /**
     * Playback moved on by itself from [previous] to [now] without stopping at the range's end, because the next page
     * was not in the playlist yet when [previous] was armed: the step that should have run, or null.
     */
    fun onOvershoot(previous: ReadingUnit?, now: ReadingUnit?): StudyStep? {
        val left = range?.takeIf { it.leftBy(previous, now) } ?: return null
        return if (left.loop) StudyStep.LoopTo(left.start) else StudyStep.Stop.also { clear() }
    }
}

/** Whether [current] is inside the range and not its end, so reading goes on past it (a chapter's end included). */
fun StudyLoop.runsOnAfter(current: PlaybackItem?, next: PlaybackItem?): Boolean {
    val unit = (current as? PlaybackItem.Sentence)?.unit
    val inside = range
    return unit != null && inside != null && unit in inside && !inside.endsAt(unit, next)
}

/** True when this spot comes after [other] in the book. */
fun Spot.isAfter(other: Spot): Boolean = page > other.page || (page == other.page && charStart > other.charStart)
