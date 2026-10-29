// File: app/src/main/kotlin/io/github/skm0078/audora/FirstFrameHold.kt
// Purpose: Draws the first screen once, in the listener's saved look, instead of in the default look and again in
//          theirs a moment later (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.ui.theme.Appearance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Holds the window's first frame back, so the system's splash screen stays up, until [release] or [maxHoldMs],
 * whichever comes first. A held frame is skipped and tried again on the next one, so the hold must always end.
 *
 * The cap runs on the main thread's own clock, not on Compose's: under a UI test, Compose's clock only moves once
 * the main thread goes idle, which a held frame never lets it do, so a cap measured by Compose alone (the wait in
 * [rememberSavedAppearance]) would never fire. Main thread only.
 */
internal class FirstFrameHold(private val maxHoldMs: Long) {
    private var released = false

    fun hold(activity: ComponentActivity) {
        Handler(Looper.getMainLooper()).postDelayed(::release, maxHoldMs)
        val content = activity.findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (released) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return released
                }
            },
        )
    }

    fun release() {
        released = true
    }
}

/**
 * The saved look once it is read; the default look if reading takes longer than [waitMs]; null until one of the two.
 * The first screen is composed only once this is not null, so it is never built twice in two looks.
 */
@Composable
internal fun rememberSavedAppearance(appearances: Flow<Appearance>, waitMs: Long): Appearance? {
    val saved: Appearance? by remember(appearances) { appearances }.collectAsStateWithLifecycle(initialValue = null)
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(waitMs)
        waited = true
    }
    return saved ?: Appearance().takeIf { waited }
}
