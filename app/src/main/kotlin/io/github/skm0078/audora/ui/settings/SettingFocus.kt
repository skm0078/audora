// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingFocus.kt
// Purpose: Brings the setting a search picked into view and tints it for a moment, so the listener sees where it
//          lives (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay

/** The setting to show, once: [onShown] runs when it has been, so going back to You later shows nothing again. */
class SettingFocus(val key: SettingKey?, val onShown: () -> Unit) {
    companion object {
        val None = SettingFocus(null) {}
    }
}

internal val LocalSettingFocus = compositionLocalOf { SettingFocus.None }

/** Which of You's pages shows; [fromSearch] while a found setting opened Privacy or Advanced. */
internal class SettingsPages(pageState: MutableState<SettingsPage>, fromSearchState: MutableState<Boolean>) {
    var page by pageState
        private set
    var fromSearch by fromSearchState
        private set

    /** The listener's own move to [next]: Back returns to You's main page from it again. */
    fun open(next: SettingsPage) {
        page = next
        fromSearch = false
    }

    fun show(key: SettingKey) {
        page = key.page
        fromSearch = key.page != SettingsPage.MAIN
    }
}

/**
 * You's page, saved across rotation. A setting found by search opens its page, and Back then returns to the search
 * rather than to You's own page.
 */
@Composable
internal fun rememberSettingsPages(focus: SettingFocus): SettingsPages {
    val page = rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    val fromSearch = rememberSaveable { mutableStateOf(false) }
    val pages = remember { SettingsPages(page, fromSearch) }
    BackHandler(enabled = pages.page != SettingsPage.MAIN && !pages.fromSearch) { pages.open(SettingsPage.MAIN) }
    LaunchedEffect(focus.key) { focus.key?.let(pages::show) }
    return pages
}

/**
 * Wraps the row of [key]; while it is the one to show, scrolls it into view, moves focus to it (TalkBack reads it)
 * and tints it, fading over [FLASH_MS]. Every other row is drawn as it was.
 */
@Composable
internal fun SettingAnchor(key: SettingKey, row: @Composable () -> Unit) {
    val focus = LocalSettingFocus.current
    val shown = focus.key == key
    val view = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val tint = remember { Animatable(0f) }
    val colour = MaterialTheme.colorScheme.primaryContainer
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        withFrameNanos { } // laid out first, so there is somewhere to scroll to
        view.bringIntoView()
        runCatching { focusRequester.requestFocus() }
        tint.snapTo(1f)
        delay(HOLD_MS)
        tint.animateTo(0f, tween(FLASH_MS))
        focus.onShown()
    }
    val marked = if (shown) Modifier.focusRequester(focusRequester).focusable().testTag(FOCUSED_TAG) else Modifier
    // A Column, not a Box: some anchors hold several rows (voice commands' model, switch and screen-off rows), and a
    // Box drew them on top of each other.
    Column(
        Modifier.bringIntoViewRequester(view).then(marked).drawBehind { drawRect(colour.copy(alpha = tint.value)) },
    ) {
        row()
    }
}

internal const val FOCUSED_TAG = "setting_focused"
private const val HOLD_MS = 600L
private const val FLASH_MS = 900
