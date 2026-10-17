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

/**
 * Volume down skips to the next sentence and volume up goes back one, only while [active] (the reader is shown and
 * reading aloud); paused, or anywhere else, the keys change the volume as usual. The keys are taken before Android's
 * own volume handling by wrapping the window's callback for as long as this is in the composition, so nothing else in
 * the activity changes. A held key acts once.
 */
@Composable
fun VolumeKeySkips(active: Boolean, onNext: () -> Unit, onPrevious: () -> Unit) {
    val window = LocalActivity.current?.window ?: return
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    DisposableEffect(window, active) {
        if (!active) return@DisposableEffect onDispose {}
        val original = window.callback
        val skipping = SkippingCallback(original) { forward -> if (forward) next() else previous() }
        window.callback = skipping
        onDispose { if (window.callback === skipping) window.callback = original }
    }
}

private class SkippingCallback(private val original: Window.Callback, private val onSkip: (forward: Boolean) -> Unit) :
    Window.Callback by original {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP -> false
            else -> return original.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onSkip(forward)
        return true
    }
}
