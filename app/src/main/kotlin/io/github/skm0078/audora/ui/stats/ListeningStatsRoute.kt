// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/ListeningStatsRoute.kt
// Purpose: The library's "Listening stats" sheet wired to the listening log and the books' places (CF-8).

package io.github.skm0078.audora.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.stats.ListeningLog
import io.github.skm0078.audora.core.stats.ListeningSummary
import io.github.skm0078.audora.ui.loggingFailures
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * While [open]: the days listened (loaded off the main thread on first open) and the books finished, which are the
 * library's own Finished books, worked out from [books] (null while the library loads) and their places.
 */
@Composable
fun ListeningStatsRoute(
    log: ListeningLog,
    books: List<Book>?,
    anchors: ResumeAnchorStore,
    open: Boolean,
    onClose: () -> Unit,
) {
    if (!open) return
    val scope = rememberCoroutineScope()
    LaunchedEffect(log) { withContext(Dispatchers.IO) { log.load() } }
    val days by log.days.collectAsStateWithLifecycle()
    val places by remember(anchors) { anchors.observeAll().loggingFailures(TAG, "reading positions") }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now()
    val firstDay = WeekFields.of(LocalConfiguration.current.locales[0]).firstDayOfWeek
    val summary = remember(days, today, firstDay) { ListeningSummary.of(days, today, firstDay) }
    val finished = remember(books, places) { finishedCount(books.orEmpty(), places) }
    ListeningStatsSheet(
        summary = summary,
        booksFinished = finished,
        onClear = { scope.launch(Dispatchers.IO) { log.clear() } },
        onClose = onClose,
    )
}

/** Books whose place is on their last page: the library's Finished filter, counted. */
internal fun finishedCount(books: List<Book>, anchors: List<ResumeAnchor>): Int {
    val byBook = anchors.associateBy { it.bookId }
    return books.count { BookProgress.of(it, byBook[it.id])?.isFinished == true }
}

private const val TAG = "Audora.ListeningStats"
