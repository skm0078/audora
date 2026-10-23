// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioStore.kt
// Purpose: Studio audio on disk: one folder per book and signature, one file per page, kept within a size budget.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.util.SortedMap

/**
 * Layout: `<root>/<book hash>/<signature hash>/p<page>.studio`, and `<root>/<book hash>/book` holding the library id
 * so books removed from the library can be found and deleted. Pages are written whole (temp file + rename), so a
 * reader on another thread sees the old file or the new one, never half of one.
 *
 * Thread-safe for one writer (the studio run) and any number of readers (the playback render thread): readers only
 * read files, and a file that vanishes under them reads as missing.
 */
@Suppress("TooManyFunctions") // Reads, writes and the four ways old audio is dropped, all on one folder tree.
class StudioStore(private val root: File) {
    /** The page's studio audio for [key]; null when there is none or it is unreadable (logged, then deleted). */
    fun page(key: StudioKey, page: Int): StudioPage? {
        val file = pageFile(key, page)
        val bytes = try {
            file.takeIf { it.isFile }?.readBytes()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Studio page unreadable, book=${key.book.logLabel} page=$page", e)
            null
        } ?: return null
        return try {
            StudioPage.fromBytes(bytes).takeIf { it.page == page } ?: throw CorruptStudioData("page number differs")
        } catch (e: CorruptStudioData) {
            AudoraLog.w(TAG, "Studio page damaged, deleted, book=${key.book.logLabel} page=$page: ${e.message}")
            file.delete()
            null
        }
    }

    /** A version of the page's file that changes whenever the file is replaced; null when there is none. */
    fun pageStamp(key: StudioKey, page: Int): Long? = pageFile(key, page).takeIf { it.isFile }?.lastModified()

    /** Writes the page whole; returns the bytes written. */
    fun write(key: StudioKey, studioPage: StudioPage): Outcome<Long, StudioError> {
        val folder = signatureFolder(key)
        val target = pageFile(key, studioPage.page)
        val temp = File(folder, "${target.name}.tmp")
        return try {
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("cannot create the folder")
            File(bookFolder(key.book), BOOK_ID_FILE).takeUnless { it.isFile }?.writeText(key.book.value)
            val bytes = studioPage.toBytes()
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) throw IOException("cannot replace ${target.name}")
            Outcome.Success(bytes.size.toLong())
        } catch (e: IOException) {
            temp.delete()
            AudoraLog.e(TAG, "Studio page not saved, book=${key.book.logLabel} page=${studioPage.page}", e)
            Outcome.Failure(StudioError.Storage(e.message ?: "write failed"))
        }
    }

    /**
     * Drops what [key]'s book no longer needs: pages before [keepFromPage] (already listened to) and every folder of
     * another signature (rendered with a voice or style no longer in use).
     */
    fun prune(key: StudioKey, keepFromPage: Int) {
        val folders = bookFolder(key.book).listFiles { file -> file.isDirectory }.orEmpty()
        for (folder in folders) {
            if (folder.name != key.signature.hash) {
                folder.deleteRecursively()
                continue
            }
            folder.listFiles().orEmpty()
                .filter { pageNumber(it)?.let { page -> page < keepFromPage } ?: false }
                .forEach { it.delete() }
        }
    }

    /** Deletes the studio audio of every book not in [library]. */
    fun dropBooksNotIn(library: Set<BookId>) {
        val keep = library.mapTo(HashSet()) { StudioKeys.bookFolder(it) }
        root.listFiles { file -> file.isDirectory && file.name !in keep }.orEmpty().forEach { folder ->
            AudoraLog.i(TAG, "Studio audio of a removed book deleted (${folder.name})")
            folder.deleteRecursively()
        }
    }

    /**
     * Deletes every signature folder last written before [stamp], the app's install or update time: each signature
     * carries that time ([StudioSignature.appStamp]), so audio written before an update can never play again.
     */
    fun dropWrittenBefore(stamp: Long) {
        root.listFiles { file -> file.isDirectory }.orEmpty().forEach { book ->
            book.listFiles { file -> file.isDirectory }.orEmpty()
                .filter { newestWrite(it) < stamp }
                .forEach { folder ->
                    AudoraLog.i(TAG, "Studio audio of an older build deleted (${book.name}/${folder.name})")
                    folder.deleteRecursively()
                }
        }
    }

    /**
     * Deletes other books' studio audio, the least recently written first, until everything fits in [maxBytes].
     * True when it fits; false when [keep]'s own audio alone is over the budget.
     */
    fun fitWithin(maxBytes: Long, keep: BookId): Boolean {
        val keepFolder = StudioKeys.bookFolder(keep)
        var total = totalBytes()
        val others = root.listFiles { file -> file.isDirectory && file.name != keepFolder }.orEmpty()
            .sortedBy { newestWrite(it) }
        for (folder in others) {
            if (total <= maxBytes) break
            val size = sizeOf(folder)
            if (folder.deleteRecursively()) {
                total -= size
                AudoraLog.i(TAG, "Studio audio of another book deleted to stay within the budget (${folder.name})")
            }
        }
        return total <= maxBytes
    }

    fun totalBytes(): Long = sizeOf(root)

    /**
     * What [book] has on the phone (ADR-062): the pages of its newest signature, the one the last run wrote. Older
     * signatures are dropped when a run starts. Reads only the clip headers. Null when it has none.
     */
    fun bookAudio(book: BookId): StudioBookAudio? {
        val pages = newestPages(book)
        if (pages.isEmpty()) return null
        val durationMs = pages.values.sumOf { file -> pageDuration(book, file) }
        return StudioBookAudio(pages.firstKey(), pages.lastKey(), pages.size, durationMs, sizeOf(bookFolder(book)))
    }

    /** [book]'s newest pages in reading order, each read when asked for; damaged ones are skipped (and logged). */
    fun readyPages(book: BookId): Sequence<StudioPage> = newestPages(book).asSequence().mapNotNull { (page, file) ->
        try {
            StudioPage.fromBytes(file.readBytes()).takeIf { it.page == page }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Studio page unreadable, book=${book.logLabel} page=$page", e)
            null
        } catch (e: CorruptStudioData) {
            AudoraLog.w(TAG, "Studio page damaged, book=${book.logLabel} page=$page: ${e.message}")
            null
        }
    }

    /** Deletes all of [book]'s studio audio; playback reads it live until studio mode runs again. */
    fun deleteBook(book: BookId): Boolean = bookFolder(book).let { !it.exists() || it.deleteRecursively() }

    private fun newestPages(book: BookId): SortedMap<Int, File> {
        val newest = bookFolder(book).listFiles { file -> file.isDirectory }.orEmpty().maxByOrNull { newestWrite(it) }
        return newest?.listFiles().orEmpty()
            .mapNotNull { file -> pageNumber(file)?.let { it to file } }
            .toMap().toSortedMap()
    }

    private fun pageDuration(book: BookId, file: File): Long = try {
        DataInputStream(BufferedInputStream(file.inputStream())).use { StudioPage.durationOf(it) }
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Studio page unreadable, book=${book.logLabel} (${file.name})", e)
        0L
    } catch (e: CorruptStudioData) {
        AudoraLog.w(TAG, "Studio page damaged, book=${book.logLabel} (${file.name}): ${e.message}")
        0L
    }

    private fun bookFolder(book: BookId) = File(root, StudioKeys.bookFolder(book))

    private fun signatureFolder(key: StudioKey) = File(bookFolder(key.book), key.signature.hash)

    private fun pageFile(key: StudioKey, page: Int) = File(signatureFolder(key), "$PAGE_PREFIX$page$PAGE_SUFFIX")

    private companion object {
        const val TAG = "Studio.Store"
        const val BOOK_ID_FILE = "book"
        const val PAGE_PREFIX = "p"
        const val PAGE_SUFFIX = ".studio"

        fun pageNumber(file: File): Int? = file.name
            .takeIf { it.startsWith(PAGE_PREFIX) && it.endsWith(PAGE_SUFFIX) }
            ?.removePrefix(PAGE_PREFIX)?.removeSuffix(PAGE_SUFFIX)?.toIntOrNull()

        fun sizeOf(file: File): Long = file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

        fun newestWrite(folder: File): Long = folder.walkBottomUp().maxOfOrNull { it.lastModified() } ?: 0L
    }
}

/**
 * One book's studio audio on the phone (ADR-062). Pages are zero-based; [pages] can be fewer than the range when a
 * page had nothing to read.
 */
data class StudioBookAudio(val firstPage: Int, val lastPage: Int, val pages: Int, val durationMs: Long, val bytes: Long)

/** Why studio mode stopped early. */
sealed interface StudioError {
    val message: String

    data class Storage(val detail: String) : StudioError {
        override val message: String get() = "Could not save studio audio: $detail"
    }

    /** The phone is nearly full, or all studio audio is at its budget. */
    data object NoSpace : StudioError {
        override val message: String get() = "Not enough free storage for studio audio"
    }

    data class VoiceFailed(val detail: String) : StudioError {
        override val message: String get() = detail
    }

    data class BookUnreadable(val page: Int) : StudioError {
        override val message: String get() = "The PDF could not be read from page ${page + 1}"
    }
}
