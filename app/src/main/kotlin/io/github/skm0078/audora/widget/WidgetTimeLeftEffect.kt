// File: app/src/main/kotlin/io/github/skm0078/audora/widget/WidgetTimeLeftEffect.kt
// Purpose: Hands the reader's time-left estimate to the home-screen widget (CF-7): on a pause or any change while
//          paused, and every few minutes while reading.

package io.github.skm0078.audora.widget

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.core.config.PlayerControlsConfig
import io.github.skm0078.audora.ui.reader.ReaderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

/** One estimate as the widget needs it: which book, how long, and whether reading goes on. */
private data class Estimate(val bookId: String, val ms: Long, val reading: Boolean)

/** Runs for as long as the reader or player of a book is on screen; [state] is its model's. */
@Composable
fun WidgetTimeLeftEffect(state: StateFlow<ReaderState>, config: PlayerControlsConfig = PlayerControlsConfig()) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(state) {
        val schedule = WidgetEstimateSchedule(config.widgetRefreshMs)
        state.mapNotNull { now -> now.timeLeft?.let { Estimate(now.bookId.value, it.bookMs, now.isReading) } }
            .distinctUntilChanged()
            .collect { estimate ->
                val at = SystemClock.elapsedRealtime()
                if (!schedule.due(estimate.bookId, estimate.reading, at)) return@collect
                val timeLeft = WidgetTimeLeft(estimate.bookId, estimate.ms, at)
                // The first read of the widget's preferences file loads it from disk.
                withContext(Dispatchers.IO) { ResumeWidgetProvider.publishTimeLeft(context, timeLeft) }
            }
    }
}
