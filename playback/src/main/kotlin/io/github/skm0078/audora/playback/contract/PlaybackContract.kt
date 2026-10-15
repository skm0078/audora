// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/PlaybackContract.kt
// Purpose: Bundle keys the reader and the playback service agree on (media item and session extras).

package io.github.skm0078.audora.playback.contract

/**
 * The reader talks to the playback service only through the standard Media3 `Player`/`MediaController` API,
 * plus these keys and [ReaderSessionCommands]. It never touches the service's classes, so either side can
 * change its internals freely.
 */
object PlaybackContract {
    /** `MediaItem.requestMetadata.extras` of a start request: the PDF's password (String), held in memory only. */
    const val EXTRA_PASSWORD = "io.github.skm0078.audora.PASSWORD"

    /** `MediaMetadata.extras` of a playlist item: the unit's exclusive end offset into its page's text (Int). */
    const val EXTRA_CHAR_END = "io.github.skm0078.audora.CHAR_END"

    /** `MediaMetadata.extras` of a playlist item: the book's page count (Int). */
    const val EXTRA_PAGE_COUNT = "io.github.skm0078.audora.PAGE_COUNT"
}
