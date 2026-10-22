// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/KeepScreenOn.kt
// Purpose: Holds the screen on while [active] and this screen is shown ("Keep screen on while reading aloud").

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Uses the view's flag, so leaving the reader or pausing lets the screen time out as usual; no wake lock. */
@Composable
fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}
