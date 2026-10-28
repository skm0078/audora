// File: app/src/main/kotlin/io/github/skm0078/audora/TabsRoute.kt
// Purpose: The four places' frame: the app shell with its tabs or sidebar, the phone's drawer and the mini player
//          (ADR-036, ADR 2026-10-28-four-places, ADR 2026-10-28-phone-drawer), and the tour over the library (ADR
//          2026-10-28-home-and-tour); moved out of MainActivity to keep it small.

package io.github.skm0078.audora

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.AppTab
import io.github.skm0078.audora.ui.firstrun.GuidedTour
import io.github.skm0078.audora.ui.firstrun.GuidedTourOverlay
import io.github.skm0078.audora.ui.firstrun.LocalTourTargets
import io.github.skm0078.audora.ui.firstrun.TourTargets
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.library.BookCovers
import io.github.skm0078.audora.ui.library.LibraryAction
import io.github.skm0078.audora.ui.library.LibraryController
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.library.MiniPlayer
import io.github.skm0078.audora.ui.library.drawerShelves
import io.github.skm0078.audora.ui.library.nowPlaying
import io.github.skm0078.audora.ui.library.sidebarShelves
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.rememberPlayWithNotificationPermission
import io.github.skm0078.audora.ui.shell.AppShell
import io.github.skm0078.audora.ui.shell.PhoneDrawer

/**
 * The shell around a place; the mini player shows while a book plays and opens the full player. Over the library,
 * the [tour] when it offers itself or stands at a stop, pointing at what the shell and the library mark for it.
 */
@Composable
internal fun TabsRoute(
    tab: AppTab,
    library: LibraryController,
    port: MediaControllerPlaybackPort,
    covers: BookCovers,
    navigate: (AppDestination) -> Unit,
    tour: GuidedTour,
    onLibraryAction: (LibraryAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val targets = remember { TourTargets() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTourTargets provides targets) {
            val drawer = phoneDrawer(tab, library, navigate, onLibraryAction) {
                tour.start()
                navigate(AppDestination.Library)
            }
            Shell(tab, library, port, covers, navigate, drawer, content)
        }
        if (tab == AppTab.LIBRARY && !LocalKidsMode.current) {
            val list = library.listState
            GuidedTourOverlay(tour, targets, firstBook = (list as? LibraryListState.Loaded)?.all?.size == 1)
        }
    }
}

@Composable
private fun Shell(
    tab: AppTab,
    library: LibraryController,
    port: MediaControllerPlaybackPort,
    covers: BookCovers,
    navigate: (AppDestination) -> Unit,
    drawer: PhoneDrawer,
    content: @Composable () -> Unit,
) {
    val playback by port.state.collectAsStateWithLifecycle()
    val playWithPermission = rememberPlayWithNotificationPermission()
    val playing = nowPlaying(playback, library.listState)
    AppShell(
        selected = tab,
        onSelect = { navigate(it.destination) },
        tabs = if (LocalKidsMode.current) listOf(AppTab.LIBRARY) else AppTab.entries,
        // The same collections the drawer lists, worked out once.
        shelves = drawer.collections,
        drawer = drawer,
        miniPlayer = {
            playing?.let { now ->
                MiniPlayer(
                    playing = now,
                    onOpen = { navigate(AppDestination.Player(now.bookId, tab)) },
                    onPlayPause = {
                        if (port.state.value.wantsToPlay) port.pause() else playWithPermission(port::play)
                    },
                    covers = covers,
                )
            }
        },
        content = content,
    )
}

/** The phone drawer's rows: the library's shelves and collections, Made, the listening stats, the tour and Settings. */
@Composable
private fun phoneDrawer(
    tab: AppTab,
    library: LibraryController,
    navigate: (AppDestination) -> Unit,
    onLibraryAction: (LibraryAction) -> Unit,
    onTour: () -> Unit,
): PhoneDrawer {
    val toLibrary = { navigate(AppDestination.Library) }
    return PhoneDrawer(
        shelves = drawerShelves(library, libraryShowing = tab == AppTab.LIBRARY, onOpened = toLibrary),
        collections = sidebarShelves(library, toLibrary),
        onMade = { navigate(AppDestination.Made) },
        onListeningStats = { onLibraryAction(LibraryAction.LISTENING_STATS) },
        onTour = onTour,
        onSettings = { navigate(AppDestination.Settings) },
    )
}
