// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/SentenceTimings.kt
// Purpose: The renderer's record of where the voice speaks in each sentence rendered, for the word highlight (item 14).

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.contract.SentenceTiming
import io.github.skm0078.audora.playback.queue.UnitKey

/**
 * Written on the render thread as each sentence is rendered, read on the main thread when it becomes the one
 * playing. Keeps the last [capacity] sentences (the render-ahead window is far smaller); a re-render (a style change)
 * replaces its entry. [onRecorded] is told of each new entry, on the render thread.
 */
class SentenceTimings(private val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity >= 1) { "capacity must be >= 1, was $capacity" }
    }

    private val timings = object : LinkedHashMap<UnitKey, SentenceTiming>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UnitKey, SentenceTiming>): Boolean =
            size > capacity
    }

    @Volatile
    var onRecorded: (UnitKey) -> Unit = {}

    fun record(key: UnitKey, timing: SentenceTiming) {
        synchronized(timings) { timings[key] = timing }
        onRecorded(key)
    }

    fun of(key: UnitKey): SentenceTiming? = synchronized(timings) { timings[key] }

    private companion object {
        const val DEFAULT_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}
