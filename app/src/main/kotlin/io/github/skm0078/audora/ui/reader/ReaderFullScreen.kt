// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderFullScreen.kt
// Purpose: The reader's full screen: its on/off button beside the page number, the phone's status and navigation bars
//          hidden while it is on, and Back leaving it before it leaves the book.

package io.github.skm0078.audora.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog

/**
 * Whether the reader fills the screen: the book's page alone, with the page row (page turns, the page number and this
 * button) as the way out that is always in sight. Kept across rotation, not across opening the book again: a book that
 * opened with no bars and no controls would look broken.
 */
@Stable
internal class ReaderFullScreen(private val state: MutableState<Boolean>) {
    val on: Boolean get() = state.value

    fun toggle() {
        state.value = !state.value
        AudoraLog.i(TAG, if (state.value) "Full screen on" else "Full screen off")
    }

    fun leave() {
        if (on) toggle()
    }

    private companion object {
        const val TAG = "ReaderFullScreen"
    }
}

/** Full screen applies only while a book is open ([bookOpen]); a password prompt or an error keeps the bars. */
@Composable
internal fun rememberReaderFullScreen(bookOpen: Boolean): ReaderFullScreen {
    val state = rememberSaveable { mutableStateOf(false) }
    val screen = remember(state) { ReaderFullScreen(state) }
    FullScreenEffects(screen, on = screen.on && bookOpen)
    return screen
}

/** Full screen hides the bars, so only a camera cutout is kept clear of the page. */
@Composable
internal fun readerInsets(full: Boolean): WindowInsets =
    if (full) WindowInsets.displayCutout else ScaffoldDefaults.contentWindowInsets

/**
 * While [on]: hides the status and navigation bars (a swipe in from an edge shows them for a moment, as in a video
 * player), and Back leaves full screen instead of the book. Both come back when it turns off or the reader closes.
 */
@Composable
private fun FullScreenEffects(screen: ReaderFullScreen, on: Boolean) {
    LeaveOnBack(screen, on)
    val view = LocalView.current
    DisposableEffect(on, view) {
        val window = view.context.findActivity()?.window
        if (!on || window == null) return@DisposableEffect onDispose {}
        val bars = WindowCompat.getInsetsController(window, view)
        // On the main thread, in order: effects can run elsewhere in UI tests, and a window call made there did not
        // bring the bars back (release journey step 68).
        val main = ContextCompat.getMainExecutor(view.context)
        main.execute {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { main.execute { bars.show(WindowInsetsCompat.Type.systemBars()) } }
    }
}

/**
 * Back leaves full screen, registered only while it is on. Not Compose's BackHandler: that ties its callback to the
 * lifecycle, and removing a lifecycle observer must happen on the main thread, which UI tests do not guarantee when a
 * screen leaves composition (the release journey crashed with "removeObserver must be called on the main thread"
 * once the reader always held one). Added last, so it runs before the screen-level back.
 */
@Composable
private fun LeaveOnBack(screen: ReaderFullScreen, on: Boolean) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(dispatcher, on) {
        if (!on || dispatcher == null) return@DisposableEffect onDispose {}
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = screen.leave()
        }
        dispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}

/** Full screen's on/off, named for what a tap does, so TalkBack says "Full screen" or "Exit full screen". */
@Composable
internal fun FullScreenButton(on: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        if (on) {
            Icon(painterResource(R.drawable.ic_fullscreen_exit), stringResource(R.string.reader_full_screen_exit))
        } else {
            Icon(painterResource(R.drawable.ic_fullscreen), stringResource(R.string.reader_full_screen))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Play and pause while the bottom controls are hidden; the skip buttons and the rest stay with those controls. */
@Composable
internal fun FullScreenPlayPause(state: ReaderState, actions: ReaderActions) {
    IconButton(onClick = actions::onPlayPause, enabled = state.playerReady) {
        if (state.isReading) {
            Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.reader_pause))
        } else {
            Icon(painterResource(R.drawable.ic_play), stringResource(R.string.reader_play))
        }
    }
}
