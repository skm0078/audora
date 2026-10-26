// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningLog.kt
// Purpose: The process's one record of days listened (CF-8): the playback service adds to it, the stats sheet reads it.

package io.github.skm0078.audora.core.stats

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Blocking file I/O: call [load], [add] and [clear] off the main thread. Loads once, then keeps the days in memory and
 * saves the whole small file on each change. [days] is empty until loaded.
 */
class ListeningLog(private val store: ListeningDaysStore) {
    private val lock = Any()
    private var loaded = false
    private val state = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())

    val days: StateFlow<Map<LocalDate, Long>> = state.asStateFlow()

    fun load() = synchronized(lock) { ensureLoaded() }

    /** Adds [listened] (milliseconds by day) and saves; nothing to add writes nothing. */
    fun add(listened: Map<LocalDate, Long>) = synchronized(lock) {
        val positive = listened.filterValues { it > 0 }
        if (positive.isEmpty()) return@synchronized
        ensureLoaded()
        val next = state.value.toMutableMap()
        positive.forEach { (day, ms) -> next[day] = (next[day] ?: 0) + ms }
        state.value = next
        store.save(next)
    }

    /** Forgets every day listened (the sheet's Clear). */
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
