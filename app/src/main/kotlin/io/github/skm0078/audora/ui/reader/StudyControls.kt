// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/StudyControls.kt
// Purpose: What the speech sheet's study section gets from the reader: the A–B range and the study action.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.playback.contract.StudyAction

/**
 * What the study section needs beyond the reading modes: the A–B range of this book, whether the sentence being read
 * is in this book (so A and B can be set), and where the buttons go.
 */
class StudyControls(
    val range: StudyRangeState? = null,
    val canMark: Boolean = false,
    val onStudy: (StudyAction, Boolean) -> Unit = { _, _ -> },
)
