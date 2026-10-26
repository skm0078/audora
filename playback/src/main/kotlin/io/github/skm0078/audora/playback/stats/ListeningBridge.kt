// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/stats/ListeningBridge.kt
// Purpose: Feeds the service's reading / not reading into the day-by-day listening stats, saved on the phone (CF-8).

package io.github.skm0078.audora.playback.stats

import android.os.SystemClock
import io.github.skm0078.audora.core.stats.ListeningClock
import io.github.skm0078.audora.core.stats.ListeningLog
import io.github.skm0078.audora.core.stats.ListeningTally
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main thread, like the session events that call it: "reading" comes on play and on every page change, so time is
 * saved as it goes and a killed process loses a page's worth at most. The log does blocking file I/O, so saves run in
 * order on one background thread. Only how long and which day is kept: never a book, page or time of day.
 */
class ListeningBridge(
    private val log: ListeningLog,
    private val scope: CoroutineScope,
    private val tally: ListeningTally = ListeningTally(AndroidListeningClock),
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    fun reading() = save(tally.reading())

    fun notReading() = save(tally.stopped())

    private fun save(listened: Map<LocalDate, Long>) {
        if (listened.isEmpty()) return
        // The log logs its own failed saves; the time stays in memory and goes out with the next one.
        scope.launch(io) { log.add(listened) }
    }
}

/** Android's clocks: elapsed real time counts deep sleep too, so screen-off listening is all counted. */
object AndroidListeningClock : ListeningClock {
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    override fun wallMillis(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
