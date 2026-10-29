// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/ShelfLooks.kt
// Purpose: The colour and emoji a listener gives a collection ("Make it yours", touch 9), kept in one small file on
//          the phone beside the collections themselves.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.files.TextFiles
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Six fixed colours, so every one is checked for contrast in both themes; a free picker is Premium later. */
enum class ShelfColour { CLAY, SAFFRON, FOREST, TEAL, INDIGO, PLUM }

/** A fixed set, so a shelf never shows an emoji the phone's font cannot draw. */
enum class ShelfEmoji(val glyph: String) {
    BOOKS("📚"),
    MOON("🌙"),
    LAMP("🪔"),
    HEART("❤️"),
    SCHOOL("🎓"),
}

/** How a collection looks; null fields mean the default (a colour from its id, no emoji). */
data class ShelfLook(val colour: ShelfColour? = null, val emoji: ShelfEmoji? = null) {
    val isDefault: Boolean get() = colour == null && emoji == null

    companion object {
        val DEFAULT = ShelfLook()
    }
}

/**
 * One line per collection id with its colour and emoji by name, so a renamed enum entry never loads as another. An
 * unknown name reads as the default for that field. Written to a synced temp file and renamed; a file in another
 * format (a newer app's backup) reads as no looks and is set aside first, never overwritten. Logs counts only.
 */
class ShelfLookStore(private val file: File, private val moveAside: (File) -> Boolean = { TextFiles.setAside(it) }) {
    fun load(): Map<String, ShelfLook> {
        val lines = readLines()
        val foreign = lines != null && lines.isNotEmpty() && lines.first() != HEADER
        if (foreign) {
            AudoraLog.w(TAG, "Shelf looks from another format set aside")
            moveAside(file)
        }
        return if (lines == null || foreign) emptyMap() else lines.drop(1).mapNotNull(::lookOf).toMap()
    }

    /** The file's lines (none when there is no file yet); null, set aside, when it cannot be read. */
    private fun readLines(): List<String>? = try {
        if (file.isFile) file.readLines() else emptyList()
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Shelf looks unreadable, set aside: ${e.javaClass.simpleName}")
        moveAside(file)
        null
    }

    /**
     * Replaces the file with [looks], leaving out default looks; an empty map deletes it. False (logged) if it could
     * not be written, or if a file [load] could not read is in the way and would not move.
     */
    fun save(looks: Map<String, ShelfLook>): Boolean {
        val kept = looks.filterValues { !it.isDefault }.toSortedMap()
        return try {
            if (isForeign() && !moveAside(file)) throw IOException("unreadable shelf looks in the way")
            if (kept.isEmpty()) {
                if (file.exists() && !file.delete()) throw IOException("not deleted")
            } else {
                val lines = kept.map { (id, look) ->
                    listOf(id, look.colour?.name.orEmpty(), look.emoji?.name.orEmpty()).joinToString(SEPARATOR)
                }
                TextFiles.writeDurably(file, (listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
            }
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Shelf looks not saved, ${kept.size} looks: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun isForeign(): Boolean = file.isFile &&
        file.bufferedReader().use { it.readLine() }.let { it != null && it != HEADER }

    private fun lookOf(line: String): Pair<String, ShelfLook>? {
        val parts = line.split(SEPARATOR)
        if (parts.size != FIELDS || parts[0].isEmpty()) return null
        val colour = ShelfColour.entries.firstOrNull { it.name == parts[1] }
        val emoji = ShelfEmoji.entries.firstOrNull { it.name == parts[2] }
        return parts[0] to ShelfLook(colour, emoji)
    }

    companion object {
        /** The file under `filesDir`; the backup lists it by this name. */
        const val FILE_NAME = "shelf-looks.txt"
        private const val TAG = "Library.ShelfLooks"
        private const val HEADER = "audora-shelf-looks 1"
        private const val SEPARATOR = "\t"
        private const val FIELDS = 3
    }
}

/**
 * Every collection's look, loaded once and kept in [looks] for the screens; each change is written through [store]
 * at once (a few dozen bytes). Call from a background thread: [load] and [set] touch the file.
 */
class ShelfLooks(private val store: ShelfLookStore) {
    private val state = MutableStateFlow<Map<String, ShelfLook>>(emptyMap())
    val looks: StateFlow<Map<String, ShelfLook>> = state.asStateFlow()

    @Volatile
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        state.value = store.load()
        loaded = true
    }

    /** Gives collection [id] [look] (the default clears it); false (logged by the store) if it was not saved. */
    @Synchronized
    fun set(id: String, look: ShelfLook): Boolean {
        load()
        val next = if (look.isDefault) state.value - id else state.value + (id to look)
        return store.save(next).also { saved -> if (saved) state.value = next }
    }
}
