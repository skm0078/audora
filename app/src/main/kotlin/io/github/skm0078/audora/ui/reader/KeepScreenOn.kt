// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/KeepScreenOn.kt
// Purpose: Holds the screen on while [active] and this screen is shown ("Keep screen on while reading aloud").

package io.github.skm0078.audora.ui.reader

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Uses the view's flag, so leaving the reader or pausing lets the screen time out as usual; no wake lock. Two book
 * screens can be composed at once while one animates out, so the flag is counted per view: the leaving screen's
 * dispose never clears it while the arriving one still holds it.
 */
@Composable
fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        if (!active) return@DisposableEffect onDispose {}
        ScreenOnHolders.hold(view)
        onDispose { ScreenOnHolders.release(view) }
    }
}

/** Main thread only, like the views it counts for. */
private object ScreenOnHolders {
    private val holders = WeakHashMap<View, Int>()

    fun hold(view: View) {
        holders[view] = (holders[view] ?: 0) + 1
        view.keepScreenOn = true
    }

    fun release(view: View) {
        val left = ((holders[view] ?: 1) - 1).coerceAtLeast(0)
        if (left == 0) holders.remove(view) else holders[view] = left
        view.keepScreenOn = left > 0
    }
}
