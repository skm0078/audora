// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/controls/ListeningReports.kt
// Purpose: Sends the widget each span of reading (CF-7): at every pause, and every few minutes while reading goes on.

package io.github.skm0078.audora.playback.controls

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.contract.ListeningBroadcast

/**
 * Counts only the time the voice is heard (isPlaying), not a wait for the next sentence to render. Main thread;
 * [release] reports the span in progress when the service ends.
 */
class ListeningReports(
    context: Context,
    private val player: Player,
    private val spans: ListeningSpans,
    private val intervalMs: Long,
    private val bookId: () -> BookId?,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            spans.onTick()?.let(::send)
            main.postDelayed(this, intervalMs)
        }
    }
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            main.removeCallbacks(tick)
            if (isPlaying) {
                spans.onReading()
                main.postDelayed(tick, intervalMs)
            } else {
                spans.onStopped()?.let(::send)
            }
        }
    }

    fun start() = player.addListener(listener)

    fun release() {
        player.removeListener(listener)
        main.removeCallbacks(tick)
        spans.onStopped()?.let(::send)
    }

    private fun send(span: ListenedSpan) {
        val id = bookId() ?: return
        AudoraLog.d(TAG, "Listened ${span.toElapsedMs - span.fromElapsedMs} ms, book=${id.logLabel}")
        app.sendBroadcast(ListeningBroadcast.intent(app, id.value, span))
    }

    private companion object {
        const val TAG = "Playback.Listened"
    }
}
