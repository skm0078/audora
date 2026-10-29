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
 * Fed [ReaderState.voiceToWarm] as it changes. A language starts a wait of [delayMs], so that flicking through books
 * loads nothing; when the wait ends, [warm] is called and nothing more is ever asked. Null (something started
 * playing, the player went away) or another language (a page in another script) before then drops the wait, and the
 * next language starts it again.
 *
 * Not thread-safe: the reader model calls it from its own scope only.
 */
internal class ReaderVoiceWarmUp(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val warm: (Lang) -> Unit,
) {
    private var waiting: Job? = null
    private var done = false

    fun offer(lang: Lang?) {
        waiting?.cancel()
        if (lang == null || done) return
        waiting = scope.launch {
            delay(delayMs)
            done = true
            warm(lang)
        }
    }

    /** The book closed: nothing is loaded for it any more. */
    fun stop() {
        done = true
        waiting?.cancel()
    }
}
