// File: core/src/main/kotlin/io/github/skm0078/audora/core/sync/SyncMerge.kt
// Purpose: Merges what other phones wrote into this phone's books (CF-16): the newest place wins, bookmarks and
//          highlights are joined, a removal anywhere removes everywhere, and books come from the folder.

package io.github.skm0078.audora.core.sync

import io.github.skm0078.audora.core.config.FolderSyncConfig

/**
 * What to change on this phone, by book fingerprint, and what to remember for next time.
 *
 * @property progress places newer than this phone's, to save.
 * @property bookmarks and [highlights] marks made on another phone, to add.
 * @property removeMarks keys ([SyncBook.markKey]) of marks removed on another phone, to remove here.
 * @property booksToAdd books another phone copied into the folder that this phone does not have.
 */
data class SyncPlan(
    val progress: Map<String, SyncAnchor>,
    val bookmarks: Map<String, List<SyncBookmark>>,
    val highlights: Map<String, List<SyncHighlight>>,
    val removeMarks: Set<String>,
    val booksToAdd: List<SyncBook>,
    val memory: SyncMemory,
) {
    val changes: Int get() = progress.size + bookmarks.values.sumOf { it.size } +
        highlights.values.sumOf { it.size } + removeMarks.size
}

/**
 * Pure: no files, no clock of its own. Marks are named by their place and the moment they were made
 * ([SyncBookmark.key]), so the same mark is the same on every phone and a note edited later is not a new mark (an
 * edited note stays as each phone has it). A mark this phone had at its last sync and no longer has was removed here;
 * that removal is written for the others, and a mark another phone removed is removed here. A removed book's marks are
 * just forgotten, never removed elsewhere: removing a book from one phone leaves it on the others, and it is not
 * added back here from the folder.
 */
class SyncMerge(private val config: FolderSyncConfig = FolderSyncConfig()) {
    fun merge(local: List<SyncBook>, memory: SyncMemory, others: List<SyncSnapshot>, nowMillis: Long): SyncPlan {
        val localFingerprints = local.mapTo(HashSet()) { it.fingerprint }
        val localKeys = local.flatMapTo(HashSet()) { it.markKeys }
        val removedHere = memory.knownMarks
            .filter { it !in localKeys && it.substringBefore(KEY_SEPARATOR) in localFingerprints }
            .map { SyncRemoval(it, nowMillis) }
        val removals = (memory.removals + removedHere + others.flatMap { it.removals })
            .filter { nowMillis - it.atMillis < config.removalKeepMs }
            .groupBy { it.markKey }
            .map { (_, same) -> same.minBy { it.atMillis } }
        val removedKeys = removals.mapTo(HashSet()) { it.markKey }
        val theirs = others.flatMap { it.books }
            .filter { it.fingerprint in localFingerprints }
            .groupBy { it.fingerprint }
        val bookmarks = theirs.mapValues { (_, books) ->
            books.flatMap { book -> book.bookmarks.map { book.markKey(it.key) to it } }
                .newMarks(localKeys, removedKeys)
        }.filterValues { it.isNotEmpty() }
        val highlights = theirs.mapValues { (_, books) ->
            books.flatMap { book -> book.highlights.map { book.markKey(it.key) to it } }
                .newMarks(localKeys, removedKeys)
        }.filterValues { it.isNotEmpty() }
        val removeMarks = localKeys.filterTo(HashSet()) { it in removedKeys }
        val dismissed = (memory.dismissedBooks + (memory.knownBooks - localFingerprints)) - localFingerprints
        val booksToAdd = others.flatMap { it.books }
            .filter { it.fileName != null && it.fingerprint !in localFingerprints && it.fingerprint !in dismissed }
            .distinctBy { it.fingerprint }
            .take(config.maxAddsPerSync)
        val added = bookmarks.flatMap { (fp, marks) -> marks.map { "$fp$KEY_SEPARATOR${it.key}" } } +
            highlights.flatMap { (fp, marks) -> marks.map { "$fp$KEY_SEPARATOR${it.key}" } }
        return SyncPlan(
            progress = newerProgress(local, theirs),
            bookmarks = bookmarks,
            highlights = highlights,
            removeMarks = removeMarks,
            booksToAdd = booksToAdd,
            memory = SyncMemory(
                knownMarks = (localKeys - removeMarks) + added,
                removals = removals,
                knownBooks = localFingerprints,
                dismissedBooks = dismissed,
            ),
        )
    }

    /** For each of this phone's books, another phone's place when it was saved later than this phone's. */
    private fun newerProgress(local: List<SyncBook>, theirs: Map<String, List<SyncBook>>): Map<String, SyncAnchor> =
        local.mapNotNull { book ->
            val newest = theirs[book.fingerprint].orEmpty().mapNotNull { it.progress }.maxByOrNull { it.savedAtMillis }
            val mine = book.progress?.savedAtMillis ?: Long.MIN_VALUE
            newest?.takeIf { it.savedAtMillis > mine && it != book.progress }?.let { book.fingerprint to it }
        }.toMap()

    /** Marks not on this phone and not removed anywhere, once each. */
    private fun <T> List<Pair<String, T>>.newMarks(localKeys: Set<String>, removedKeys: Set<String>): List<T> =
        filter { (key, _) -> key !in localKeys && key !in removedKeys }.distinctBy { it.first }.map { it.second }

    private companion object {
        const val KEY_SEPARATOR = '|'
    }
}
