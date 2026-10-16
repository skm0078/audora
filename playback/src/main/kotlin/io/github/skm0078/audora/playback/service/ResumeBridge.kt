// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/ResumeBridge.kt
// Purpose: Saves the sentence being read as the book's resume anchor, and writes it at once when reading stops.

package io.github.skm0078.audora.playback.service

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.ResumeRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Main thread in, database writes on [recorder]'s own scope (P1-06, Build Spec §6 "State"). */
class ResumeBridge(private val recorder: ResumeRecorder, private val scope: CoroutineScope) {
    fun sentenceStarted(bookId: BookId, unit: ReadingUnit) = recorder.onUnitStarted(bookId, unit)

    /** Pause, stop, end of book, sleep timer, error or service shutdown. */
    fun flush() {
        scope.launch { recorder.flush() }
    }
}
