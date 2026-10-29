// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderGlance.kt
// Purpose: Hands the reader's time-left estimate to the book glances, for the library's "min left at your speed"
//          (personal touch 13, ADR 2026-10-28-make-it-yours).

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.core.home.BookGlances
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.di.HomeEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

/** While the reader is open, each new estimate of the whole book is noted; the glances save it with the next line. */
@Composable
fun RecordTimeLeft(model: ReaderModel) {
    val context = LocalContext.current
    val glances: BookGlances = remember(context) { HomeEntryPoint.of(context).bookGlances() }
    LaunchedEffect(model, glances) {
        model.state
            .mapNotNull(::estimateOf)
            .distinctUntilChanged()
            .collect { estimate ->
                withContext(Dispatchers.IO) { glances.timeLeft(estimate.bookId, estimate.ms, estimate.pagesLeft) }
            }
    }
}

private data class Estimate(val bookId: BookId, val ms: Long, val pagesLeft: Int)

/** The estimate counts from the sentence being read, so the pages left count from its page too. */
private fun estimateOf(state: ReaderState): Estimate? {
    val ms = state.timeLeft?.bookMs
    val pages = state.pageCount
    if (ms == null || pages == null) return null
    return Estimate(state.bookId, ms, pages - (state.readingPosition?.page ?: state.page))
}
