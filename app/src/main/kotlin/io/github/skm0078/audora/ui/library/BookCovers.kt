// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookCovers.kt
// Purpose: Library covers (plan item 21): each book's first page drawn once, cached as WEBP in filesDir/covers.

package io.github.skm0078.audora.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.skm0078.audora.core.config.CoverConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the library screen asks for; [None] (previews) has no covers, so every row shows its placeholder. */
fun interface CoverSource {
    /** [book]'s cover, or null when it has none (password protected, unreadable, never drawn while missing). */
    suspend fun cover(book: Book): ImageBitmap?

    companion object {
        val None = CoverSource { null }
    }
}

/**
 * Draws a cover once per book and keeps it: on disk as `covers/<book id>.webp`, decoded in a small memory cache.
 * One cover is drawn at a time, so opening a long library never opens many PDFs at once. A file that cannot be
 * drawn (a password, damaged, missing) is not retried until the app restarts or the book is found at a new URI.
 * Covers of removed books are deleted by [prune].
 */
class BookCovers(
    context: Context,
    private val io: CoroutineDispatcher,
    private val config: CoverConfig = CoverConfig(),
) : CoverSource {
    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, FOLDER)
    private val drawing = Mutex()

    /** Document URIs that could not be drawn; a book found again at a new URI gets another try. */
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val memory = object : LruCache<BookId, Bitmap>(config.memoryCacheBytes) {
        override fun sizeOf(key: BookId, value: Bitmap): Int = value.allocationByteCount
    }

    override suspend fun cover(book: Book): ImageBitmap? {
        val known = memory.get(book.id)
        return when {
            known != null -> known.asImageBitmap()

            book.documentUri in failed -> null

            else -> withContext(io) {
                val bitmap = drawing.withLock { cached(book.id) ?: draw(book) }
                bitmap?.also { memory.put(book.id, it) }?.asImageBitmap()
            }
        }
    }

    /** Deletes the covers of books no longer in the library. */
    suspend fun prune(keep: Set<BookId>) = withContext(io) {
        val names = keep.mapTo(HashSet()) { fileName(it) }
        folder.listFiles()?.filter { it.name !in names }?.forEach { stale ->
            if (!stale.delete()) AudoraLog.w(TAG, "Could not delete an old cover")
        }
    }

    /** What the covers take on disk, for the Storage screen. */
    suspend fun sizeBytes(): Long = withContext(io) { folder.listFiles().orEmpty().sumOf { it.length() } }

    /**
     * Deletes every cover, on disk and in memory; each is drawn again the next time the library shows it. Fails,
     * logged, with the files that could not be removed.
     */
    suspend fun clear(): Outcome<Unit, String> = withContext(io) {
        drawing.withLock {
            memory.evictAll()
            failed.clear()
            val files = folder.listFiles().orEmpty()
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

    private fun cached(id: BookId): Bitmap? {
        val file = fileOf(id)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path) ?: run {
            AudoraLog.w(TAG, "Cached cover unreadable, drawing it again, book=${id.logLabel}")
            file.delete()
            null
        }
    }

    private fun draw(book: Book): Bitmap? {
        val page = firstPage(book)
        if (page == null) {
            failed += book.documentUri
            return null
        }
        return onWhite(page).also { save(book.id, it) }
    }

    private fun firstPage(book: Book): Bitmap? {
        val logContext = "book=${book.id.logLabel}"
        val engine = PdfiumEngine.forUri(appContext, Uri.parse(book.documentUri))
        val renderer = when (val opened = engine.openRenderer(password = null, logContext)) {
            is Outcome.Success -> opened.value

            is Outcome.Failure -> {
                AudoraLog.i(TAG, "No cover, $logContext: ${opened.error::class.simpleName}")
                return null
            }
        }
        return when (val rendered = renderer.use { it.render(FIRST_PAGE, config.widthPx, config.maxPixels) }) {
            is Outcome.Success -> rendered.value.bitmap

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Cover not drawn, $logContext: ${rendered.error::class.simpleName}")
                null
            }
        }
    }

    /** Written beside the final name, then renamed, so a cover is never half written; a failed save costs a redraw. */
    private fun save(id: BookId, cover: Bitmap) {
        val file = fileOf(id)
        val partial = File(folder, "${file.name}$PARTIAL_SUFFIX")
        try {
            folder.mkdirs()
            partial.outputStream().use { cover.compress(Bitmap.CompressFormat.WEBP_LOSSY, config.webpQuality, it) }
            if (!partial.renameTo(file)) throw IOException("rename failed")
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not cache the cover, book=${id.logLabel}", e)
            partial.delete()
        }
    }

    private fun fileOf(id: BookId) = File(folder, fileName(id))

    private fun fileName(id: BookId) = "${id.value}$EXTENSION"

    private companion object {
        const val TAG = "Audora.Covers"
        const val FOLDER = "covers"
        const val EXTENSION = ".webp"
        const val PARTIAL_SUFFIX = ".part"
        const val FIRST_PAGE = 0
    }
}

/** PDFium leaves blank paper transparent; a cover is paper, so it gets white behind it. */
private fun onWhite(page: Bitmap): Bitmap {
    val cover = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
    Canvas(cover).apply {
        drawColor(Color.WHITE)
        drawBitmap(page, 0f, 0f, null)
    }
    page.recycle()
    return cover
}
