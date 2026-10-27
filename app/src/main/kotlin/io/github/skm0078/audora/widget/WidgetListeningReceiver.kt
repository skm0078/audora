// File: app/src/main/kotlin/io/github/skm0078/audora/widget/WidgetListeningReceiver.kt
// Purpose: Counts the widget's time left down as the playback service reports time listened (CF-7), so it stays
//          right with the screen off and the app closed.

package io.github.skm0078.audora.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.contract.ListeningBroadcast

/**
 * Not exported: only Audora's own service reaches it. Two small preference reads and a redraw, well inside a
 * receiver's time, so no goAsync.
 */
class WidgetListeningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val (bookId, span) = ListeningBroadcast.read(intent) ?: run {
            AudoraLog.w(TAG, "Ignored a listening report without a book or span")
            return
        }
        val store = WidgetTimeLeftStore(context)
        val before = store.read() ?: return
        val after = before.afterListening(bookId, span, SystemClock.elapsedRealtime())
        if (after != before) ResumeWidgetProvider.publishTimeLeft(context, after)
    }

    private companion object {
        const val TAG = "Audora.Widget"
    }
}
