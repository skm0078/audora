// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VolumeKeySkips.kt
// Purpose: While on, the volume keys move by sentence in the reader as Audora reads (plan item 20).

package io.github.skm0078.audora.ui.reader

import android.view.KeyEvent
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.util.WeakHashMap

/**
 * Volume down skips to the next sentence and volume up goes back one, only while [active] (the reader is shown and
 * reading aloud); paused, or anywhere else, the keys change the volume as usual. The keys are taken before Android's
 * own volume handling by one wrapper around the window's callback, installed while any screen wants it, so nothing
 * else in the activity changes. Two book screens can be composed at once while one animates out: the newest active
 * one gets the keys, and the wrapper comes off when the last one lets go. A held key acts once.
 */
@Composable
fun VolumeKeySkips(active: Boolean, onNext: () -> Unit, onPrevious: () -> Unit) {
    val window = LocalActivity.current?.window ?: return
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    DisposableEffect(window, active) {
        if (!active) return@DisposableEffect onDispose {}
        val owner: (Boolean) -> Unit = { forward -> if (forward) next() else previous() }
        VolumeKeyOwners.add(window, owner)
        onDispose { VolumeKeyOwners.remove(window, owner) }
    }
}

/** One installed wrapper per window, dispatching to its newest owner. Main thread only. */
private object VolumeKeyOwners {
    private val installed = WeakHashMap<Window, SkippingCallback>()

    fun add(window: Window, owner: (Boolean) -> Unit) {
        val callback = installed.getOrPut(window) {
            SkippingCallback(window.callback).also { window.callback = it }
        }
        callback.owners.addLast(owner)
    }

    fun remove(window: Window, owner: (Boolean) -> Unit) {
        val callback = installed[window] ?: return
        callback.owners.remove(owner)
        if (callback.owners.isNotEmpty()) return
        installed.remove(window)
        // Only unwrap if nothing wrapped the window after us; otherwise the empty wrapper just passes keys on.
        if (window.callback === callback) window.callback = callback.original
    }
}

private class SkippingCallback(val original: Window.Callback) : Window.Callback by original {
    val owners = ArrayDeque<(forward: Boolean) -> Unit>()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val owner = owners.lastOrNull()
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP -> false
            else -> null
        }
        if (owner == null || forward == null) return original.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) owner(forward)
        return true
    }
}
