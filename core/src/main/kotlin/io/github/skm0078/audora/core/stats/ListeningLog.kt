// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningLog.kt
// Purpose: The process's one record of listening (CF-8): the playback service adds to it, the stats screen reads it.

package io.github.skm0078.audora.core.stats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Blocking file I/O: call [load], [add] and [clear] off the main thread. Loads once, then keeps the slots in memory and
 * saves the whole small file on each change. [slots] is empty until loaded.
 */
class ListeningLog(private val store: ListeningDaysStore) {
    private val lock = Any()
    private var loaded = false
    private val state = MutableStateFlow<Map<ListeningSlot, Heard>>(emptyMap())

    val slots: StateFlow<Map<ListeningSlot, Heard>> = state.asStateFlow()

    fun load() = synchronized(lock) { ensureLoaded() }

    /** Adds [heard] and saves; nothing to add writes nothing. */
    fun add(heard: Map<ListeningSlot, Heard>) = synchronized(lock) {
        val real = heard.filterValues { !it.isEmpty }
        if (real.isEmpty()) return@synchronized
        ensureLoaded()
        val next = state.value.plusHeard(real)
        state.value = next
        store.save(next)
    }

    /** Forgets everything listened (the stats screen's Clear). */
    fun clear() = synchronized(lock) {
        loaded = true
        state.value = emptyMap()
        store.save(emptyMap())
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        state.value = store.load()
    }
}
