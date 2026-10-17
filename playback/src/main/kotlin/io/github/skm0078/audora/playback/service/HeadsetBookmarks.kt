// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/HeadsetBookmarks.kt
// Purpose: Bookmarks the sentence playing from a headset double press, anchored like a resume point (item 20).

package io.github.skm0078.audora.playback.service

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.SentenceFingerprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The same anchor the reader's long-press bookmark stores (offsets and hashes, never the text), with no note: one can
 * be added from the bookmark list later. [onSaved] confirms it (a short buzz), since the screen may be off.
 */
class HeadsetBookmarks(
    private val store: BookmarkStore,
    private val scope: CoroutineScope,
    private val pipelineVersion: Int,
    private val clock: () -> Long,
    private val onSaved: () -> Unit,
    private val fingerprint: SentenceFingerprint = SentenceFingerprint(),
) {
    fun add(bookId: BookId, unit: ReadingUnit) {
        val anchor = ResumeAnchor(
            bookId = bookId,
            page = unit.page,
            charStart = unit.charStart,
            fingerprint = fingerprint.head(unit.text),
            tailFingerprint = fingerprint.tail(unit.text),
            pipelineVersion = pipelineVersion,
            savedAtMillis = clock(),
        )
        scope.launch {
            when (val added = store.add(Bookmark(0, anchor))) {
                is Outcome.Success -> {
                    AudoraLog.i(
                        TAG,
                        "Headset bookmark added, book=${bookId.logLabel} page=${unit.page} id=${added.value}",
                    )
                    onSaved()
                }

                is Outcome.Failure ->
                    AudoraLog.e(
                        TAG,
                        "Headset bookmark failed, book=${bookId.logLabel} page=${unit.page}: ${added.error.detail}",
                    )
            }
        }
    }

    private companion object {
        const val TAG = "Playback.HeadsetMark"
    }
}
