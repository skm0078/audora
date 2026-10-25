// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/PlaylistItems.kt
// Purpose: Maps PlaybackItems to Media3 MediaItems (URI, notification text, position extras) and back.

package io.github.skm0078.audora.playback.session

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.playlist.PlaybackItem
import io.github.skm0078.audora.playback.playlist.Playlist
import io.github.skm0078.audora.playback.queue.UnitKey

/**
 * Items of one render epoch of one book. The [PlaybackItem] rides along as the item's tag, so the session reads
 * its sentences straight from the player's playlist (same process; tags never cross to controllers, which read
 * the `ITEM_*` extras instead).
 */
class PlaylistItems(
    private val bookId: String,
    private val epoch: Int,
    private val look: ItemLook,
    private val pageCount: Int,
    private val texts: ItemTexts,
) {
    fun toMediaItem(item: PlaybackItem): MediaItem {
        val uri = when (item) {
            is PlaybackItem.Sentence -> TtsUri.Sentence(bookId, epoch, UnitKey.of(item.unit))
            is PlaybackItem.More -> TtsUri.More(bookId, epoch, item.serial)
        }.toString()
        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri)
            .setMimeType(MimeTypes.AUDIO_WAV)
            .setTag(item)
            .setMediaMetadata(metadata(item))
            .build()
    }

    private fun metadata(item: PlaybackItem): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(look.title)
            .setAlbumTitle(look.title)
            .setAlbumArtist(look.author)
            .setArtworkUri(look.artwork)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
        if (item is PlaybackItem.Sentence) {
            val unit = item.unit
            builder.setArtist(look.whereLine(unit.page, pageCount, texts))
            builder.setExtras(
                Bundle().apply {
                    putString(PlaybackContract.ITEM_BOOK_ID, bookId)
                    putInt(PlaybackContract.ITEM_PAGE, unit.page)
                    putInt(PlaybackContract.ITEM_PAGE_COUNT, pageCount)
                    putInt(PlaybackContract.ITEM_UNIT_INDEX, unit.index)
                    putInt(PlaybackContract.ITEM_CHAR_START, unit.charStart)
                    putInt(PlaybackContract.ITEM_CHAR_END, unit.charEnd)
                },
            )
        } else {
            builder.setArtist(texts.loadingNextPage())
        }
        return builder.build()
    }

    companion object {
        fun itemOf(mediaItem: MediaItem?): PlaybackItem? = mediaItem?.localConfiguration?.tag as? PlaybackItem
    }
}

/**
 * What the notification, lock screen, car screen and watch show of the open book besides the sentence's place: its
 * [title], [author] and cover ([artwork], a file the library already drew; null shows no picture), and the first
 * pages of its chapters ([chapterStarts], ascending) for "Chapter 2 of 9".
 */
data class ItemLook(
    val title: String,
    val author: String? = null,
    val artwork: Uri? = null,
    val chapterStarts: List<Int> = emptyList(),
) {
    /** "Jane Austen · Chapter 2 of 9 · Page 14 of 300", leaving out what the book does not have. [page] is 0-based. */
    fun whereLine(page: Int, pageCount: Int, texts: ItemTexts): String {
        val chapter = chapterStarts.count { it <= page }.takeIf { it > 0 && chapterStarts.size > 1 }
        return listOfNotNull(
            author,
            chapter?.let {
                texts.chapterOf(it, chapterStarts.size)
            },
            texts.pageOf(page + 1, pageCount),
        )
            .filter { it.isNotBlank() }
            .joinToString(SEPARATOR)
    }

    private companion object {
        const val SEPARATOR = " · "
    }
}

/** Notification and lock-screen strings, from resources in the service. */
interface ItemTexts {
    fun pageOf(page: Int, pageCount: Int): String

    /** "Chapter [chapter] of [chapterCount]", both 1-based; blank leaves the chapter out. */
    fun chapterOf(chapter: Int, chapterCount: Int): String = ""

    fun loadingNextPage(): String
}

/** [Playlist] over the player's own playlist. Main thread only. */
class PlayerPlaylist(private val player: Player, private val factory: PlaylistItems) : Playlist {
    override val size: Int get() = player.mediaItemCount

    override val currentIndex: Int get() = if (player.mediaItemCount == 0) -1 else player.currentMediaItemIndex

    override fun item(index: Int): PlaybackItem =
        checkNotNull(PlaylistItems.itemOf(player.getMediaItemAt(index))) { "Playlist item $index has no tag" }

    override fun add(index: Int, items: List<PlaybackItem>) {
        player.addMediaItems(index, items.map(factory::toMediaItem))
    }

    override fun remove(fromIndex: Int, toIndex: Int) {
        player.removeMediaItems(fromIndex, toIndex)
    }
}
