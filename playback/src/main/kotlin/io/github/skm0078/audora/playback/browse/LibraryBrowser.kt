// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/LibraryBrowser.kt
// Purpose: Media3's library calls (Android Auto, plan item 34) over the browse tree: folders, items, search, play.

package io.github.skm0078.audora.playback.browse

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.async.launchedFuture
import io.github.skm0078.audora.playback.async.launchedValue
import kotlinx.coroutines.CoroutineScope

/**
 * Main thread, like Media3's callbacks; each answer is a future completed on [scope] once the library has been read.
 * Playing a row (or a spoken "play … on Audora") opens the book as the app does, from the user's own tap or voice
 * (C7), then hands Media3 the playlist the book session set ([playlist]), as playback resumption does. Logs context
 * only: never a title or a search's words.
 */
@OptIn(UnstableApi::class)
@Suppress("TooManyFunctions") // One per Media3 library call, plus play's two halves and the future helper.
class LibraryBrowser(
    private val lookup: BrowseLookup,
    private val scope: CoroutineScope,
    private val playlist: () -> MediaItemsWithStartPosition,
) {
    fun root(params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(BrowseItems.root, BrowseItems.listStyle(params)))

    fun children(
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = later {
        val entries = BrowseNode.parse(parentId)?.let { lookup.children(it) }
        if (entries == null) {
            AudoraLog.w(TAG, "Browse of something that is not a folder")
            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        } else {
            LibraryResult.ofItemList(BrowsePages.of(entries, page, pageSize).map(BrowseItems::of), params)
        }
    }

    fun item(mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = later {
        when (val entry = BrowseNode.parse(mediaId)?.let { lookup.entry(it) }) {
            null -> LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            else -> LibraryResult.ofItem(BrowseItems.of(entry), null)
        }
    }

    /** Counts the matches and tells [browser], which then asks for [searchResult]. */
    fun search(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = later {
        session.notifySearchResultChanged(browser, query, lookup.search(query).size, params)
        LibraryResult.ofVoid()
    }

    fun searchResult(
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = later {
        LibraryResult.ofItemList(BrowsePages.of(lookup.search(query), page, pageSize).map(BrowseItems::of), params)
    }

    /**
     * Plays the rows a browser asked for, or returns null when they are not Audora's (Media3's own handling then
     * applies). A search plays its best match; a row plays its book.
     */
    fun play(items: List<MediaItem>): ListenableFuture<MediaItemsWithStartPosition>? {
        val first = items.firstOrNull()
        val query = first?.requestMetadata?.searchQuery
        val node = first?.let { BrowseNode.parse(it.mediaId) }
        return if (query == null && node == null) null else play(node, query)
    }

    private fun play(node: BrowseNode?, query: String?): ListenableFuture<MediaItemsWithStartPosition> =
        scope.launchedFuture(TAG, "Browse play") { result ->
            val target = node ?: lookup.search(query.orEmpty()).firstOrNull()?.node
            when (val played = target?.let { lookup.play(it) }) {
                is Outcome.Success -> result.set(playlist())

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Browse play failed: ${played.error.code}")
                    result.setException(UnsupportedOperationException(played.error.code.name))
                }

                null -> {
                    AudoraLog.i(TAG, "Browse play found nothing to play, search=${query != null}")
                    result.setException(UnsupportedOperationException("Nothing to play"))
                }
            }
        }

    private fun <T : Any> later(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> =
        scope.launchedValue(TAG, "Library call", block)

    private companion object {
        const val TAG = "Playback.Browse"
    }
}
