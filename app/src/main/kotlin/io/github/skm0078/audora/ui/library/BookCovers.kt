// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookCovers.kt
// Purpose: Library covers (plan item 21): each book's first page (or a page, a picture, its own cover, ADR-068) made
//          once, cached as WEBP in filesDir/covers; chosen pictures are kept in filesDir/covers-chosen.

package io.github.skm0078.audora.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.skm0078.audora.core.config.CoverConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.CoverChoice
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the library screen asks for; [None] (previews) has no covers, so every row shows its placeholder. */
fun interface CoverSource {
    /** [book]'s cover, or null when it has none (password protected, unreadable, never drawn while missing). */
    suspend fun cover(book: Book): ImageBitmap?

    /** Changes when [id]'s kept picture is replaced, so a screen showing it asks again; snapshot state. */
    fun revision(id: BookId): Int = 0

    companion object {
        val None = CoverSource { null }
    }
}

/**
 * Draws a cover once per book and keeps it: on disk as `covers/<book id>.webp` (another page: `<id>@p<n>.webp`),
 * decoded in a small memory cache. One cover is made at a time, so opening a long library never opens many PDFs at
 * once. A page that cannot be drawn (a password, damaged, missing) is not retried until the app restarts or the book
 * is found at a new URI. A chosen picture or the book's own cover ([keep]) is kept in `covers-chosen/<id>.webp`,
 * already cover-sized; a text book without one shows its placeholder. Covers of removed books are deleted by [prune].
 */
class BookCovers(
    context: Context,
    private val io: CoroutineDispatcher,
    private val config: CoverConfig = CoverConfig(),
) : CoverSource {
    private val appContext = context.applicationContext
    private val folders = CoverFolders(appContext.filesDir)
    private val bitmaps = CoverBitmaps(config)
    private val drawing = Mutex()

    /** Pages (document URI and page) that could not be drawn; a book found again at a new URI gets another try. */
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val revisions = mutableStateMapOf<BookId, Int>()
    private val memory = object : LruCache<String, Bitmap>(config.memoryCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    override fun revision(id: BookId): Int = revisions[id] ?: 0

    override suspend fun cover(book: Book): ImageBitmap? {
        val key = "${book.id.value}|${CoverChoice.encode(book.cover)}|${revision(book.id)}"
        val known = memory.get(key)
        return when {
            known != null -> known.asImageBitmap()

            else -> withContext(io) {
                // Into memory under the same lock as clear(), so a cover drawn just before it is not kept after.
                // A tile scrolled away while waiting for the lock is cancelled here, before its page is drawn.
                drawing.withLock {
                    ensureActive()
                    make(book)?.also { memory.put(key, it) }
                }?.asImageBitmap()
            }
        }
    }

    /**
     * Keeps [image] (a picture from the gallery, or the book's own cover from its file) as [id]'s cover, cut down to
     * the cover's size first, so a 12 MP photo is never decoded whole. False (logged) if it cannot be read or saved.
     */
    suspend fun keep(id: BookId, image: CoverImage): Boolean = withContext(io) {
        val bitmap = bitmaps.decodeSmall(image)
        if (bitmap == null) {
            AudoraLog.w(TAG, "Chosen cover unreadable, book=${id.logLabel}")
            return@withContext false
        }
        drawing.withLock {
            val saved = bitmaps.writeWebp(folders.chosenFile(id), bitmap)
            memory.snapshot().keys.filter { it.startsWith(id.value) }.forEach(memory::remove)
            if (saved) revisions[id] = revision(id) + 1
            saved
        }
    }

    /** Deletes the covers of books no longer in the library, drawn and chosen. */
    suspend fun prune(keep: Set<BookId>) = withContext(io) {
        val ids = keep.mapTo(HashSet()) { it.value }
        listOf(folders.drawn, folders.chosen).forEach { dir ->
            dir.listFiles()?.filter { it.isFile && folders.bookIdOf(it.name) !in ids }?.forEach { stale ->
                if (!stale.delete()) AudoraLog.w(TAG, "Could not delete an old cover")
            }
        }
    }

    /** What the covers take on disk, for the Storage screen. */
    suspend fun sizeBytes(): Long = withContext(io) {
        listOf(folders.drawn, folders.chosen).sumOf { dir -> dir.listFiles().orEmpty().sumOf { it.length() } }
    }

    /**
     * Deletes every drawn cover, on disk and in memory; each is drawn again the next time the library shows it.
     * Chosen pictures are kept: they cannot be drawn again. Fails, logged, with the files that could not be removed.
     */
    suspend fun clear(): Outcome<Unit, String> = withContext(io) {
        drawing.withLock {
            memory.evictAll()
            failed.clear()
            val files = folders.drawn.listFiles().orEmpty()
            val kept = files.count { !it.delete() }
            if (kept == 0) {
                AudoraLog.i(TAG, "Covers cleared, files=${files.size}")
                Outcome.Success(Unit)
            } else {
                AudoraLog.w(TAG, "Covers: $kept of ${files.size} files could not be removed")
                Outcome.Failure("$kept files could not be removed")
            }
        }
    }

    /** A kept picture when the book has one; for a PDF the page drawn (cached), else its first page. */
    private fun make(book: Book): Bitmap? {
        val kept = if (book.cover.isKeptPicture) bitmaps.decode(folders.chosenFile(book.id)) else null
        val page = (book.cover as? CoverChoice.Page)?.index ?: FIRST_PAGE
        return when {
            kept != null -> kept

            book.format?.isText == true -> null

            else -> bitmaps.decodeCached(folders.drawnFile(book.id, page)) ?: draw(book, page)
                ?: firstPageInstead(book, page)
        }
    }

    /** A chosen page that cannot be drawn (the book changed) shows the first page. */
    private fun firstPageInstead(book: Book, page: Int): Bitmap? = if (page ==
        FIRST_PAGE
    ) {
        null
    } else {
        bitmaps.decodeCached(folders.drawnFile(book.id, FIRST_PAGE)) ?: draw(book, FIRST_PAGE)
    }

    /** Null without trying again for a page that could not be drawn before (see [failed]). */
    private fun draw(book: Book, page: Int): Bitmap? {
        val failedKey = "${book.documentUri}#$page"
        val drawn = if (failedKey in failed) null else pageOf(book, page)
        if (drawn == null) {
            failed += failedKey
            return null
        }
        return onWhite(drawn, bitmaps.colours).also { bitmaps.writeWebp(folders.drawnFile(book.id, page), it) }
    }

    private fun pageOf(book: Book, page: Int): Bitmap? {
        val logContext = "book=${book.id.logLabel}"
        val engine = PdfiumEngine.forUri(appContext, SharedPdfCopies.readableUri(appContext, book.documentUri))
        val renderer = when (val opened = engine.openRenderer(password = null, logContext)) {
            is Outcome.Success -> opened.value

            is Outcome.Failure -> {
                AudoraLog.i(TAG, "No cover, $logContext: ${opened.error::class.simpleName}")
                return null
            }
        }
        return when (val rendered = renderer.use { it.render(page, config.widthPx, config.maxPixels) }) {
            is Outcome.Success -> rendered.value.bitmap

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Cover not drawn, page=$page $logContext: ${rendered.error::class.simpleName}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "Audora.Covers"
        const val FIRST_PAGE = CoverFolders.FIRST_PAGE
    }
}
