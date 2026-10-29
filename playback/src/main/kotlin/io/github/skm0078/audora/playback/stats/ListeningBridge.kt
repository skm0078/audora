// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/stats/ListeningBridge.kt
// Purpose: Feeds the service's reading, sentences and stops into the listening stats, saved on the phone (CF-8).

package io.github.skm0078.audora.playback.stats

import android.os.SystemClock
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.stats.Heard
import io.github.skm0078.audora.core.stats.ListeningClock
import io.github.skm0078.audora.core.stats.ListeningLog
import io.github.skm0078.audora.core.stats.ListeningSlot
import io.github.skm0078.audora.core.stats.ListeningTally
import io.github.skm0078.audora.core.stats.WordCount
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main thread, like the session events that call it: "reading" comes on play and on every page change, so time is
 * saved as it goes and a killed process loses a page's worth at most. The log does blocking file I/O, so saves run in
 * order on one background thread. Sentences only count words and languages in memory; they are saved with the next
 * page or stop. Kept: the day, the hour, the book's id, the language, how long and how many words; never a page, a
 * title or anything read.
 */
class ListeningBridge(
    private val log: ListeningLog,
    private val scope: CoroutineScope,
    private val tally: ListeningTally = ListeningTally(AndroidListeningClock),
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    fun reading(bookId: String) {
        tally.reading(bookId)
        save()
    }

    fun sentence(bookId: String, lang: Lang, text: String) = tally.sentence(bookId, lang, WordCount.of(text))

    fun notReading() {
        tally.stopped()
        save()
    }

    private fun save() {
        val listened: Map<ListeningSlot, Heard> = tally.drain()
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
