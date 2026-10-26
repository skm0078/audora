// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/HeadsetBookmarks.kt
// Purpose: Bookmarks the sentence playing, eyes-free: a headset double press (item 20) or the lock screen's bookmark
//          button (CF-1), anchored like a resume point.

package io.github.skm0078.audora.playback.service

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.SentenceFingerprint
import io.github.skm0078.audora.playback.session.BookSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The same anchor the reader's long-press bookmark stores (offsets and hashes, never the text), with no note: one can
 * be added from the bookmark list later. [onSaved] confirms it (a short buzz, the lock screen's filled bookmark),
 * since the screen may be off; [source] names who asked, in the log.
 */
class HeadsetBookmarks(
    private val store: BookmarkStore,
    private val scope: CoroutineScope,
    private val pipelineVersion: Int,
    private val clock: () -> Long,
    private val onSaved: (ReadingUnit) -> Unit,
    private val source: String = "Headset",
) {
    private val fingerprint = SentenceFingerprint()

    /** Bookmarks the sentence [session] plays (or last played, while paused); false, logged, when there is none. */
    fun addCurrent(session: BookSession): Boolean {
        val bookId = session.bookId
        val unit = session.currentSentence()
        if (bookId == null || unit == null) {
            AudoraLog.w(TAG, "$source bookmark ignored: no sentence playing")
            return false
        }
        add(bookId, unit)
        return true
    }

    fun add(bookId: BookId, unit: ReadingUnit) {
        val anchor = ResumeAnchor(
            bookId = bookId,
            page = unit.page,
            charStart = unit.charStart,
            fingerprint = fingerprint.head(unit),
            tailFingerprint = fingerprint.tail(unit),
            pipelineVersion = pipelineVersion,
            savedAtMillis = clock(),
        )
        scope.launch {
            when (val added = store.add(Bookmark(0, anchor))) {
                is Outcome.Success -> {
                    AudoraLog.i(
                        TAG,
                        "$source bookmark added, book=${bookId.logLabel} page=${unit.page} id=${added.value}",
                    )
                    onSaved(unit)
                }

                is Outcome.Failure ->
                    AudoraLog.e(
                        TAG,
                        "$source bookmark failed, book=${bookId.logLabel} page=${unit.page}: ${added.error.detail}",
                    )
            }
        }
    }

    private companion object {
        const val TAG = "Playback.HeadsetMark"
    }
}
