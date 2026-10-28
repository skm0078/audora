// File: app/src/main/kotlin/io/github/skm0078/audora/ui/firstrun/GuidedTour.kt
// Purpose: The 1-minute tour (ADR 2026-10-28-home-and-tour): offered once when the library first has a book, five
//          stops that each point at one thing on screen, and replayed from You › Show me around.

package io.github.skm0078.audora.ui.firstrun

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.ui.library.LibraryListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** What a stop points at: a thing on the library screen, or one of the places. */
enum class TourTarget { PLAY, BOOK, NOTES, MADE, YOU }

/** The tour's stops, in order. [LISTEN] points at the Continue card's Play, or at the first book without one. */
enum class TourStop(val target: TourTarget, @StringRes val title: Int, @StringRes val body: Int) {
    LISTEN(TourTarget.PLAY, R.string.tour_listen_title, R.string.tour_listen_body),
    BOOK_MENU(TourTarget.BOOK, R.string.tour_menu_title, R.string.tour_menu_body),
    NOTES(TourTarget.NOTES, R.string.tour_notes_title, R.string.tour_notes_body),
    MADE(TourTarget.MADE, R.string.tour_made_title, R.string.tour_made_body),
    YOU(TourTarget.YOU, R.string.tour_you_title, R.string.tour_you_body),
}

/** Offered once, to a listener out of kids mode whose library has a book; never while it still loads. */
fun shouldOfferTour(offered: Boolean, kids: Boolean, library: LibraryListState): Boolean =
    !offered && !kids && library is LibraryListState.Loaded && library.all.isNotEmpty()

/**
 * Where the tour stands: hidden, offering itself, or at one stop. Saved as one number, so a rotation keeps the stop.
 * Answering the offer in any way, or starting a replay, marks it offered, so it is never offered again.
 */
@Stable
class GuidedTour internal constructor(state: MutableIntState, private val onOffered: () -> Unit) {
    private var step by state

    val offering: Boolean
        get() = step == OFFER

    val stop: TourStop?
        get() = TourStop.entries.getOrNull(step)

    internal fun offer() {
        if (step == HIDDEN) step = OFFER
    }

    /** Starts at the first stop; also You's Show me around. */
    fun start() {
        onOffered()
        step = 0
    }

    fun next() {
        step = (step + 1).takeIf { it < TourStop.entries.size } ?: HIDDEN
    }

    /** Back steps to the stop before; from the first one it closes the tour. */
    fun back() {
        step = (step - 1).takeIf { it >= 0 } ?: HIDDEN
    }

    fun close() {
        onOffered()
        step = HIDDEN
    }

    internal companion object {
        const val HIDDEN = -2
        const val OFFER = -1
    }
}

/**
 * The tour for this activity, placed after the first-run screens. The saved flag is read once off the main thread;
 * then the tour offers itself as soon as the library has a book (for a new listener, the one the first-run screens
 * just added) and nothing else is [waiting] to be read first, such as What's new.
 */
@Composable
fun rememberGuidedTour(library: () -> LibraryListState, kids: Boolean, waiting: Boolean): GuidedTour {
    val context = LocalContext.current.applicationContext
    val prefs = remember { OnboardingPreferences(context) }
    val state = rememberSaveable { mutableIntStateOf(GuidedTour.HIDDEN) }
    val tour = remember { GuidedTour(state) { prefs.tourOffered = true } }
    val held by rememberUpdatedState(waiting)
    LaunchedEffect(kids) {
        val offered = withContext(Dispatchers.IO) { prefs.tourOffered }
        if (offered || kids) return@LaunchedEffect
        snapshotFlow { !held && shouldOfferTour(offered, kids, library()) }.first { it }
        tour.offer()
    }
    return tour
}
