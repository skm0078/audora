// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderVoiceWarmUp.kt
// Purpose: Asks the player, once per open book, to load the voice for the shown page's language after the book has
//          stayed open a moment, so pressing Play does not wait for the model (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.Lang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Waits [delayMs] after the first [offer] so that flicking through books loads nothing, then asks once more whether
 * warming still makes sense ([mayWarm]: the player is connected and nothing plays, as a model loading beside one that
 * reads would slow it) and calls [warm]. Done once; a later offer is ignored. A wait that ends when warming does not
 * make sense does nothing, so a later offer (the next page, the player connecting) tries again.
 *
 * Not thread-safe: the reader model calls it from its own scope only.
 */
internal class ReaderVoiceWarmUp(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val mayWarm: () -> Boolean,
    private val warm: (Lang) -> Unit,
) {
    private var waiting: Job? = null
    private var done = false

    /** The shown page reads in [lang]; null when its sentences are not known yet. */
    fun offer(lang: Lang?) {
        if (lang == null || done || waiting?.isActive == true || !mayWarm()) return
        waiting = scope.launch {
            delay(delayMs)
            if (!mayWarm()) return@launch
            done = true
            warm(lang)
        }
    }

    fun cancel() {
        waiting?.cancel()
    }
}
