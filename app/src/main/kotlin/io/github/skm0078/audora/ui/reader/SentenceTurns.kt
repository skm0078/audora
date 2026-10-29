// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SentenceTurns.kt
// Purpose: The text view turned a screenful at a time (ADR 2026-10-28-text-pages): turns by the arrows, a swipe or
//          reading on, across the book's pages, each played in the look's style (ADR 2026-10-29-page-curl).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import io.github.skm0078.audora.ui.reader.flip.TurnStyle
import kotlinx.coroutines.launch

/**
 * As each page of the book shows: open it at its end after a turn back, then play the turn begun on the page before.
 * A screen kept for reading's turn onto the next page is let go once reading stops.
 */
@Composable
internal fun PageStartTurns(state: ReaderState, listState: LazyListState, turning: TextTurning) {
    val turnBack = turning.turnBack
    LaunchedEffect(listState) {
        if (turnBack.openAtEnd) {
            turnBack.openAtEnd = false
            withFrameNanos { } // laid out first, so the list knows its last item
            turning.screens.toEnd()
        }
        val pending = turnBack.pending
        turnBack.pending = null
        if (pending != null && pending.page == state.page) {
            turning.turner.play(pending.forward, pending.shot, pending.reading)
        }
    }
    LaunchedEffect(state.isReading) {
        if (!state.isReading && turnBack.pending?.reading == true) turnBack.pending = null
    }
}

/**
 * Reading moved to [item]: a screen it is not wholly on turns to it, in the look's style. On the page's [last] item,
 * the screen is kept, so the turn onto the next page can curl it away once that page shows.
 */
internal suspend fun followInScreens(turning: TextTurning, item: Int, last: Boolean, page: Int) {
    val way = turning.screens.turnFor(item)
    if (way != 0) {
        val shot = turning.turner.before()
        turning.screens.keepInView(item)
        turning.turner.play(forward = way > 0, shot = shot, reading = true)
    }
    if (last && turning.turner.turn.style != TurnStyle.NONE) {
        turning.turnBack.pending = PendingTurn(page + 1, forward = true, shot = turning.turner.before(), reading = true)
    }
}

/** What turning the text view needs, held together. */
internal class TextTurning(
    val screens: TextScreens,
    val turner: TextTurner,
    val turnBack: TextTurnBack,
    val look: ReadingLook,
)

/**
 * The sentences a screenful at a time, with the arrows under them. At a page's last screen a turn on shows the book's
 * next page; at its first, a turn back shows the page before at its last screen. Each turn plays in the look's style.
 */
@Composable
internal fun TurnedSentences(
    state: ReaderState,
    actions: ReaderActions,
    turning: TextTurning,
    list: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pageCount = state.pageCount ?: 1
    val next = {
        scope.launch { turnOn(state, pageCount, actions, turning) }
        Unit
    }
    val previous = {
        scope.launch { turnBackOne(state, actions, turning) }
        Unit
    }
    Column(Modifier.fillMaxSize()) {
        TurningText(turning.turner, turning.look, Modifier.weight(1f).screenSwipes(next, previous), list)
        TextPagesBar(state.page, pageCount, turning.screens.turned, previous, next)
    }
}

private suspend fun turnOn(state: ReaderState, pageCount: Int, actions: ReaderActions, turning: TextTurning) {
    val lastPage = state.page + 1 >= pageCount
    if (turning.screens.atEnd && lastPage) return
    val shot = turning.turner.before()
    when {
        turning.screens.next() -> turning.turner.play(forward = true, shot = shot, reading = false)

        !lastPage -> {
            turning.turnBack.pending = PendingTurn(state.page + 1, forward = true, shot = shot, reading = false)
            actions.onNextPage()
        }
    }
}

private suspend fun turnBackOne(state: ReaderState, actions: ReaderActions, turning: TextTurning) {
    val shot = turning.turner.before()
    when {
        turning.screens.previous() -> turning.turner.play(forward = false, shot = shot, reading = false)

        state.page > 0 -> {
            turning.turnBack.openAtEnd = true
            turning.turnBack.pending = PendingTurn(state.page - 1, forward = false, shot = shot, reading = false)
            actions.onPreviousPage()
        }
    }
}
