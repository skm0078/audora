// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/OpdsCatalogues.kt
// Purpose: The catalogues on the Book catalogues screen: the built-in public-domain one and those the listener added.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.files.TextFiles
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/** A catalogue's name and first page; [builtIn] ones cannot be removed. */
data class OpdsCatalogue(val title: String, val address: String, val builtIn: Boolean = false)

/** How adding a catalogue went; each has its own message. */
enum class CatalogueAdd { ADDED, ALREADY_THERE, TOO_MANY, NOT_SAVED }

/**
 * The listener's catalogues are kept in one small text file, a line each (`title<TAB>address`), written whole and
 * durably ([TextFiles.writeDurably]); a line this build cannot read is skipped. Not in the backup: re-adding an
 * address is quick, and a backup is the listener's books and notes. Callers keep calls off the main thread.
 */
class OpdsCatalogues(private val file: File, private val maxCatalogues: Int) {
    /** The built-in catalogues, then the listener's in the order added. */
    fun all(): List<OpdsCatalogue> = BUILT_IN + added()

    /** Adds [catalogue] unless its address is already listed or the list is full. */
    fun add(catalogue: OpdsCatalogue): CatalogueAdd {
        val added = added()
        return when {
            all().any { it.address == catalogue.address } -> CatalogueAdd.ALREADY_THERE
            added.size >= maxCatalogues -> CatalogueAdd.TOO_MANY
            save(added + catalogue.copy(builtIn = false)) -> CatalogueAdd.ADDED
            else -> CatalogueAdd.NOT_SAVED
        }
    }

    /** Removes the listener's catalogue at [address]; false when it could not be saved. */
    fun remove(address: String): Boolean = save(added().filter { it.address != address })

    private fun added(): List<OpdsCatalogue> = try {
        if (file.exists()) file.readLines().mapNotNull(::parse) else emptyList()
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Catalogue list unreadable: ${e.javaClass.simpleName}")
        emptyList()
    }

    private fun save(catalogues: List<OpdsCatalogue>): Boolean = try {
        TextFiles.writeDurably(file, catalogues.joinToString("") { "${clean(it.title)}$TAB${it.address}\n" })
        true
    } catch (e: IOException) {
        AudoraLog.e(TAG, "Catalogue list not saved: ${e.javaClass.simpleName}")
        false
    }

    private fun parse(line: String): OpdsCatalogue? {
        val title = line.substringBefore(TAB, missingDelimiterValue = "")
        val address = line.substringAfter(TAB, missingDelimiterValue = "").trim()
        return WebAddress.normalize(address)?.let { OpdsCatalogue(title.ifBlank { WebAddress.hostOf(it) }, it) }
    }

    /** A title is one line without tabs, so the file stays a line per catalogue. */
    private fun clean(title: String) = title.replace(LINE_BREAKS, " ").trim()

    companion object {
        private const val TAG = "Audora.Catalogues"
        private const val TAB = '\t'
        private val LINE_BREAKS = Regex("""[\t\r\n]+""")

        /** Project Gutenberg's own OPDS catalogue: over 70,000 free public-domain books, no account. */
        const val GUTENBERG = "https://www.gutenberg.org/ebooks.opds/"

        val BUILT_IN = listOf(OpdsCatalogue("Project Gutenberg", GUTENBERG, builtIn = true))
    }
}
