// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/sync/SyncLocal.kt
// Purpose: This phone's side of a sync (CF-16): reads every book's place, bookmarks and highlights by fingerprint,
//          and applies what the merge decided.

package io.github.skm0078.audora.playback.sync

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.sync.SyncAnchor
import io.github.skm0078.audora.core.sync.SyncBook
import io.github.skm0078.audora.core.sync.SyncBookmark
import io.github.skm0078.audora.core.sync.SyncHighlight
import io.github.skm0078.audora.core.sync.SyncPlan
import kotlinx.coroutines.flow.first

/** The stores a sync reads and changes; the app hands over its singletons. */
data class SyncStores(
    val library: Library,
    val anchors: ResumeAnchorStore,
    val bookmarks: BookmarkStore,
    val highlights: HighlightStore,
    val documents: DocumentAccess,
)

/** A stored bookmark or highlight, to remove by its id. */
internal data class MarkRef(val highlight: Boolean, val id: Long)

/**
 * This phone's books as the sync folder sees them, with what is needed to change them: each fingerprint's book id
 * and each mark's stored id. A book is listed once per fingerprint (the library never holds two of the same book).
 */
class LocalBooks internal constructor(
    val books: List<SyncBook>,
    internal val ids: Map<String, BookId>,
    private val marks: Map<String, MarkRef>,
    internal val documentUris: Map<String, String>,
    internal val sizes: Map<String, Long>,
) {
    internal fun markRef(markKey: String): MarkRef? = marks[markKey]
}

/** How many changes landed and how many could not be stored (logged, tried again next sync). */
data class Applied(val changes: Int, val failed: Int)

/** Reads and changes this phone's books for a sync. Notes and quotes are never logged. */
class SyncLocal(private val stores: SyncStores) {
    suspend fun read(): LocalBooks {
        val books = stores.library.books().first().distinctBy { it.fingerprint }
        val anchors = stores.anchors.observeAll().first().associateBy { it.bookId }
        val highlights = stores.highlights.observeAll().first().groupBy { it.bookId }
        val marks = HashMap<String, MarkRef>()
        val synced = books.map { book ->
            val bookmarks = stores.bookmarks.observe(book.id).first()
            val own = highlights[book.id].orEmpty()
            val sync = SyncBook(
                fingerprint = book.fingerprint,
                title = book.title,
                progress = anchors[book.id]?.let(SyncAnchor::of),
                bookmarks = bookmarks.map(SyncBookmark::of),
                highlights = own.map(SyncHighlight::of),
            )
            bookmarks.forEach { marks[sync.markKey(SyncBookmark.of(it).key)] = MarkRef(false, it.id) }
            own.forEach { marks[sync.markKey(SyncHighlight.of(it).key)] = MarkRef(true, it.id) }
            sync
        }
        return LocalBooks(
            books = synced,
            ids = books.associate { it.fingerprint to it.id },
            marks = marks,
            documentUris = books.associate { it.fingerprint to it.documentUri },
            sizes = books.associate { it.fingerprint to it.sizeBytes },
        )
    }

    /** Applies [plan] to the books in [local]; a book that left the library meanwhile is skipped. */
    suspend fun apply(plan: SyncPlan, local: LocalBooks): Applied {
        val tally = Tally()
        plan.progress.forEach { (fingerprint, anchor) ->
            local.ids[fingerprint]?.let { id -> tally.store({ anchor.toAnchor(id) }) { stores.anchors.put(it) } }
        }
        plan.bookmarks.forEach { (fingerprint, marks) ->
            val id = local.ids[fingerprint] ?: return@forEach
            marks.forEach { mark -> tally.store({ mark.toBookmark(id) }) { stores.bookmarks.add(it) } }
        }
        plan.highlights.forEach { (fingerprint, marks) ->
            val id = local.ids[fingerprint] ?: return@forEach
            marks.forEach { mark -> tally.store({ mark.toHighlight(id) }) { stores.highlights.add(it) } }
        }
        plan.removeMarks.forEach { key ->
            val mark = local.markRef(key) ?: return@forEach
            tally.count(if (mark.highlight) stores.highlights.delete(mark.id) else stores.bookmarks.delete(mark.id))
        }
        if (tally.failed >
            0
        ) {
            AudoraLog.w(TAG, "Sync changes not stored: ${tally.failed} of ${tally.done + tally.failed}")
        }
        return Applied(tally.done, tally.failed)
    }

    /** Counts what was stored and what was not. */
    private class Tally {
        var done = 0
        var failed = 0

        fun count(outcome: Outcome<*, *>) = if (outcome is Outcome.Success) done++ else failed++

        /**
         * Another device's file may hold a place this build rejects (a damaged line); it counts as not stored.
         */
        suspend fun <T> store(make: () -> T?, put: suspend (T) -> Outcome<*, *>) {
            val made = try {
                make()
            } catch (ignored: IllegalArgumentException) {
                null
            }
            if (made == null) failed++ else count(put(made))
        }
    }

    private companion object {
        const val TAG = "Audora.Sync"
    }
}
