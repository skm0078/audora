// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/OpdsCatalogues.kt
// Purpose: The catalogues on the Book catalogues screen: the curated ones, then any a listener added before the
//          list was curated (ADR 2026-10-28-curated-catalogues).

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.files.TextFiles
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/** A catalogue's name and first page; a [curated] one ([CuratedCatalogue]) cannot be removed. */
data class OpdsCatalogue(val title: String, val address: String, val curated: CuratedCatalogue? = null)

/**
 * Catalogues can no longer be added by address (typed OPDS links mostly failed, and a curated list is checked every
 * week), but those added by earlier versions stay listed until the listener removes them. They are kept in one small
 * text file, a line each (`title<TAB>address`), written whole and durably ([TextFiles.writeDurably]); a line this
 * build cannot read is skipped, as is one now curated. Callers keep calls off the main thread.
 */
class OpdsCatalogues(private val file: File) {
    /** The curated catalogues, then the listener's in the order added. */
    fun all(): List<OpdsCatalogue> = CURATED + added().filter { own -> CURATED.none { it.address == own.address } }

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

        val CURATED: List<OpdsCatalogue> = CuratedCatalogue.entries.map { it.catalogue }
    }
}
