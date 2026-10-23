// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/BrowseItems.kt
// Purpose: Turns browse rows into Media3 media items, laid out as lists for a car's screen (plan item 34).

package io.github.skm0078.audora.playback.browse

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams

@OptIn(UnstableApi::class)
object BrowseItems {
    val root: MediaItem get() = of(BrowseEntry(BrowseNode.Root, "", browsable = true))

    fun of(entry: BrowseEntry): MediaItem = MediaItem.Builder()
        .setMediaId(entry.node.mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(entry.title)
                .setSubtitle(entry.subtitle)
                .setIsBrowsable(entry.browsable)
                .setIsPlayable(entry.playable)
                .setMediaType(mediaTypeOf(entry.node))
                .build(),
        )
        .build()

    /** Rows as a list, not a grid of tiles: there is no cover art, and titles are long. */
    fun listStyle(params: LibraryParams?): LibraryParams {
        val list = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        val extras = Bundle(params?.extras ?: Bundle.EMPTY).apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, list)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, list)
        }
        return LibraryParams.Builder()
            .setOffline(params?.isOffline ?: false)
            .setRecent(params?.isRecent ?: false)
            .setSuggested(params?.isSuggested ?: false)
            .setExtras(extras)
            .build()
    }

    private fun mediaTypeOf(node: BrowseNode): Int = when (node) {
        BrowseNode.Root -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        BrowseNode.Recent, BrowseNode.UpNext, BrowseNode.Shelf -> MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS
        is BrowseNode.Book, is BrowseNode.Resume -> MediaMetadata.MEDIA_TYPE_AUDIO_BOOK
        is BrowseNode.Chapter -> MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER
    }
}
