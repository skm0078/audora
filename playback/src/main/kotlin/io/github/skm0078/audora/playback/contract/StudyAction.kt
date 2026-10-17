// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/StudyAction.kt
// Purpose: What a STUDY command does to the A–B study range (plan item 31).

package io.github.skm0078.audora.playback.contract

enum class StudyAction {
    /** A at the sentence playing. */
    MARK_START,

    /** B at the sentence playing; the range starts looping or reading once. */
    MARK_END,

    /** Loop on or off for the range, from ARG_STUDY_LOOP. */
    SET_LOOP,

    CLEAR,

    /** Plays an exact range of a book, such as a selection. */
    RANGE,
}
