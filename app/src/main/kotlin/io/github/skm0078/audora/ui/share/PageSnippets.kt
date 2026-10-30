// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/PageSnippets.kt
// Purpose: Hands the part of a page the listener boxed, and the whole page it came from, from the reader to the card
//          screen, in memory (ADRs 2026-10-28-page-snippet, 2026-10-30-whole-page-cards).

package io.github.skm0078.audora.ui.share

import android.graphics.Bitmap
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.share.SnippetBox
import kotlin.math.roundToInt

/** The boxed [part] of a drawn [page], and the page itself for the card's "Whole page". */
class HeldSnippet(val part: Bitmap, val page: Bitmap)

/**
 * The reader cuts the page as it is drawn on screen and the card screen opens on it: in memory, so a locked PDF is
 * never rendered again without its password and nothing is written until Share. One snippet at a time; a new one
 * replaces it, and the card screen lets it go when it closes. After the app is stopped by Android the card screen
 * says the snippet is gone, and the listener picks it again.
 */
object PageSnippets {
    private const val TAG = "ShareCard"

    @Volatile
    private var held: HeldSnippet? = null

    /** [page] cut to [box], the page kept beside it; false if it could not be cut (logged). */
    fun hold(page: Bitmap, box: SnippetBox): Boolean {
        val x = (box.left * page.width).roundToInt().coerceIn(0, page.width - 1)
        val y = (box.top * page.height).roundToInt().coerceIn(0, page.height - 1)
        val width = (box.width * page.width).roundToInt().coerceIn(1, page.width - x)
        val height = (box.height * page.height).roundToInt().coerceIn(1, page.height - y)
        return try {
            // A hardware bitmap cannot be cut on every phone; it is copied to memory first.
            val source = if (page.config == Bitmap.Config.HARDWARE) page.copy(Bitmap.Config.ARGB_8888, false) else page
            val readable = requireNotNull(source) { "page could not be copied" }
            held = HeldSnippet(Bitmap.createBitmap(readable, x, y, width, height), readable)
            AudoraLog.i(TAG, "Snippet of ${width}x$height px from a ${page.width}x${page.height} page")
            true
        } catch (e: IllegalArgumentException) {
            AudoraLog.e(TAG, "Snippet could not be cut", e)
            false
        }
    }

    /** The snippet held, if any. */
    fun current(): HeldSnippet? = held

    fun release() {
        held = null
    }
}
