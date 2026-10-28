// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideos.kt
// Purpose: The one call the card screen makes to turn a quote into a video and share it (ADR 2026-10-28-quote-video).

package io.github.skm0078.audora.playback.export.video

import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.export.JobSlots
import java.io.File

/**
 * A quote to make into a video: [quote] as the card shows it, from zero-based [page] of [book], its words in [ink]
 * with the one being said on [accent] (ARGB), at 1080 x 1920 when [sharp], else 720 x 1280.
 */
data class QuoteVideoRequest(
    val book: BookId,
    val quote: String,
    val page: Int,
    val ink: Int,
    val accent: Int,
    val sharp: Boolean,
)

object QuoteVideos {
    internal const val ACTION_MAKE = "io.github.skm0078.audora.video.MAKE"
    internal const val EXTRA_BOOK = "book"
    internal const val EXTRA_QUOTE = "quote"
    internal const val EXTRA_PAGE = "page"
    internal const val EXTRA_INK = "ink"
    internal const val EXTRA_ACCENT = "accent"
    internal const val EXTRA_SHARP = "sharp"

    /** Videos and their backdrop live here only; the clip FileProvider hands them out (xml/clip_share_paths.xml). */
    const val FOLDER = "shared-videos"
    private const val BACKDROP = "backdrop.png"
    private const val TAG = "Video.Start"

    /** A video is being made (possibly by a service that has already stopped): a new one would be dropped. */
    val busy: Boolean get() = JobSlots.VIDEO.busy

    /**
     * Where the card screen writes the card without its quote (look, picture, credit, mark) before [make], at the
     * video's size; the video is drawn over it. Its folder is made if missing.
     */
    fun backdropFile(context: Context): File = File(File(context.cacheDir, FOLDER).apply { mkdirs() }, BACKDROP)

    /**
     * Reads [request]'s quote in the book's voice, writes the video in a short foreground service (its notification
     * shows the progress) and opens the share sheet when it is done. Call it from a tap while ListenLock is on screen,
     * after writing [backdropFile]: Android lets a foreground service start only then.
     */
    fun make(context: Context, request: QuoteVideoRequest) {
        AudoraLog.i(TAG, "Video requested, book=${request.book.logLabel} page=${request.page} sharp=${request.sharp}")
        val intent = Intent(context, QuoteVideoService::class.java)
            .setAction(ACTION_MAKE)
            .putExtra(EXTRA_BOOK, request.book.value)
            .putExtra(EXTRA_QUOTE, request.quote)
            .putExtra(EXTRA_PAGE, request.page)
            .putExtra(EXTRA_INK, request.ink)
            .putExtra(EXTRA_ACCENT, request.accent)
            .putExtra(EXTRA_SHARP, request.sharp)
        context.startForegroundService(intent)
    }

    internal fun requestOf(intent: Intent?): QuoteVideoRequest? {
        val book = intent?.takeIf { it.action == ACTION_MAKE }?.getStringExtra(EXTRA_BOOK)?.takeIf(String::isNotBlank)
        val quote = intent?.getStringExtra(EXTRA_QUOTE)?.takeIf(String::isNotBlank)
        return if (book == null || quote == null) {
            null
        } else {
            QuoteVideoRequest(
                book = BookId(book),
                quote = quote,
                page = intent.getIntExtra(EXTRA_PAGE, 0).coerceAtLeast(0),
                ink = intent.getIntExtra(EXTRA_INK, DEFAULT_INK),
                accent = intent.getIntExtra(EXTRA_ACCENT, DEFAULT_ACCENT),
                sharp = intent.getBooleanExtra(EXTRA_SHARP, false),
            )
        }
    }

    private const val DEFAULT_INK = 0xFFFFFFFF.toInt()
    private const val DEFAULT_ACCENT = 0xFFFFD66B.toInt()
}
