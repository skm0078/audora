// File: app/src/main/kotlin/io/github/skm0078/audora/TabsRoute.kt
// Purpose: The four places' frame: the app shell with its tabs or sidebar and the mini player (ADR-036, ADR
//          2026-10-28-four-places), and the tour over the library (ADR 2026-10-28-home-and-tour); moved out of
//          MainActivity to keep it small.

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
import io.github.skm0078.audora.ui.library.LibraryController
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.library.MiniPlayer
import io.github.skm0078.audora.ui.library.nowPlaying
import io.github.skm0078.audora.ui.library.sidebarShelves
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.rememberPlayWithNotificationPermission
import io.github.skm0078.audora.ui.shell.AppShell

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
    content: @Composable () -> Unit,
) {
    val targets = remember { TourTargets() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTourTargets provides targets) {
            Shell(tab, library, port, covers, navigate, content)
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
    content: @Composable () -> Unit,
) {
    val playback by port.state.collectAsStateWithLifecycle()
    val playWithPermission = rememberPlayWithNotificationPermission()
    val playing = nowPlaying(playback, library.listState)
    AppShell(
        selected = tab,
        onSelect = { navigate(it.destination) },
        tabs = if (LocalKidsMode.current) listOf(AppTab.LIBRARY) else AppTab.entries,
        shelves = sidebarShelves(library) { navigate(AppDestination.Library) },
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
