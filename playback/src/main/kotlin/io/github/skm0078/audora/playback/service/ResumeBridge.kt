// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/ResumeBridge.kt
// Purpose: Saves the sentence being read as the book's resume anchor and as the Continue card's last line heard, and
//          writes both at once when reading stops.

package io.github.skm0078.audora.playback.service

import io.github.skm0078.audora.core.home.BookGlances
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.ResumeRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Main thread in, database and file writes on [scope] (P1-06, Build Spec §6 "State"; ADR 2026-10-28-make-it-yours). */
class ResumeBridge(
    private val recorder: ResumeRecorder,
    private val scope: CoroutineScope,
    private val glances: BookGlances,
) {
    /** One at a time, so a sentence's line never lands after the next one's. */
    private val inOrder = Dispatchers.IO.limitedParallelism(1)

    fun sentenceStarted(bookId: BookId, unit: ReadingUnit) {
        recorder.onUnitStarted(bookId, unit)
        if (unit.text.isNotBlank()) scope.launch(inOrder) { glances.heard(bookId, unit.text) }
    }

    /** Pause, stop, end of book, sleep timer, error or service shutdown. */
    fun flush() {
        scope.launch { recorder.flush() }
        scope.launch(inOrder) { glances.flush() }
    }
}
