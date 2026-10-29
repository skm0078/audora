// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AudiobookLeftovers.kt
// Purpose: Deletes the files of audiobook exports that never finished and were not continued (ADR
//          2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverPolicy
import io.github.skm0078.audora.core.files.LeftoverTally
import io.github.skm0078.audora.core.logging.AudoraLog

/**
 * A stopped export keeps its finished chapters in Audiobooks/<title>/ so saving the book again goes on from them
 * (ADR-034), and a run cut off mid-chapter leaves one hidden half-written file. Neither is in Made: an audiobook is
 * recorded there only once it is complete ([io.github.skm0078.audora.playback.outputs.MadeFiles]). Left alone they
 * stay for good, so the clean-up deletes:
 * - an unfinished export's chapters and half-written files, once [LeftoverPolicy] lets them go (kept a while for
 *   Continue; at once when the book is gone from the library or the listener asks);
 * - the half-written files a finished export could not delete (the next export would retry them anyway).
 *
 * A finished audiobook's chapters are never touched, and nor is any file in [made] (what Made lists), whatever a
 * record says. Call with no export running ([JobSlots.EXPORT] held), off the main thread: each file is a MediaStore
 * call.
 */
class AudiobookLeftovers(private val records: ExportRecords, private val files: SavedFiles) {
    /** What is waiting, and how large. */
    fun measure(made: Set<String>): LeftoverTally = records.stored().fold(LeftoverTally.NONE) { tally, stored ->
        val locations = leftoversOf(stored.record, made)
        tally + LeftoverTally.of(LeftoverKind.AUDIOBOOK, if (locations.isEmpty()) 0 else 1, sizes(locations))
    }

    /** The leftovers of [bookId]'s last export, if it did not finish; 0 when there are none. */
    fun bytesFor(bookId: String, made: Set<String>): Long =
        sizes(leftoversOf(records.load(bookId)?.takeUnless { it.complete }, made))

    /**
     * Deletes what [policy] lets go. [library] holds the books still in the library, by [ExportRecords.hashOf];
     * an export of a book that is gone can never be continued. Returns what was deleted.
     */
    fun clean(policy: LeftoverPolicy, library: Set<String>, made: Set<String>, asked: Boolean): LeftoverTally {
        records.strayTemps().forEach { if (!it.delete()) AudoraLog.w(TAG, "Stray record temp file not deleted") }
        return records.stored().fold(LeftoverTally.NONE) { tally, stored ->
            val orphan = stored.hash !in library
            val record = stored.record
            when {
                // Unreadable: the next export starts over anyway; only the record itself is left to drop.
                record == null -> {
                    if (policy.mayDelete(stored.touchedAtMs, orphan, asked)) records.deleteStored(stored.hash)
                    tally
                }

                policy.mayDelete(stored.touchedAtMs, orphan, asked) -> tally + discard(stored.hash, record, made)

                else -> tally
            }
        }
    }

    /** The listener's "Delete unfinished files" on [bookId]'s stopped or failed export card. */
    fun discard(bookId: String, made: Set<String>): LeftoverTally {
        val record = records.load(bookId)?.takeUnless { it.complete } ?: return LeftoverTally.NONE
        return discard(ExportRecords.hashOf(bookId), record, made)
    }

    private fun discard(hash: String, record: ExportRecord, made: Set<String>): LeftoverTally {
        val locations = leftoversOf(record, made)
        if (locations.isEmpty()) return LeftoverTally.NONE
        val sizes = locations.associateWith(files::sizeOf)
        val failed = locations.filterNot(files::delete)
        when {
            // Saving again later will start over; a failed delete stays pending so that run tries it again.
            !record.complete && failed.isEmpty() -> records.deleteStored(hash)

            else -> records.saveStored(hash, record.copy(chapters = keptChapters(record), pending = failed))
        }
        val deleted = locations - failed.toSet()
        AudoraLog.i(
            TAG,
            "Unfinished audiobook files deleted=${deleted.size} kept=${failed.size} done=${record.complete}",
        )
        val jobs = if (deleted.isEmpty()) 0 else 1
        return LeftoverTally.of(LeftoverKind.AUDIOBOOK, jobs, deleted.sumOf(sizes::getValue))
    }

    /** A finished export keeps its chapters; an unfinished one gives them all up. */
    private fun keptChapters(record: ExportRecord) = if (record.complete) record.chapters else emptyList()

    private fun leftoversOf(record: ExportRecord?, made: Set<String>): List<String> {
        record ?: return emptyList()
        val unfinished = if (record.complete) record.pending else record.pending + record.chapters.map { it.location }
        return unfinished.distinct().filterNot { it in made }
    }

    private fun sizes(locations: List<String>): Long = locations.sumOf(files::sizeOf)

    private companion object {
        const val TAG = "Export.Leftovers"
    }
}
