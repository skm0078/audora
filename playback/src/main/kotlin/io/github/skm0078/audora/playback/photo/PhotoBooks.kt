// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/photo/PhotoBooks.kt
// Purpose: "Point and read" and pictures: a camera photo, pictures from the gallery, Files or a share, or a comic
//          archive become a scanned book in the library (plan item 62, ADR-043, ADR-066,
//          ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.photo

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.backup.writeDurably
import io.github.skm0078.audora.playback.comic.ComicBookFile
import io.github.skm0078.audora.playback.comic.ComicTooLargeException
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why a photo did not become a book; the UI says so, the log has the detail. */
sealed interface PhotoError {
    val detail: String

    /** The camera app returned without a photo, or wrote an empty file. */
    data object NoPhoto : PhotoError {
        override val detail = "no photo"
    }

    data class Unreadable(override val detail: String) : PhotoError

    /** The book was made but the library refused it. */
    data class NotAdded(override val detail: String) : PhotoError

    /** More pictures than one book takes; the first ones were not used either, so the user picks fewer. */
    data class TooMany(val max: Int) : PhotoError {
        override val detail = "over $max pictures"
    }

    /** A comic archive with more pages than [max], or larger than the copy limit; nothing was made. */
    data class ComicTooLong(val max: Int) : PhotoError {
        override val detail = "comic over $max pages or the size limit"
    }

    /** A comic archive that is damaged or has no picture Android can decode. */
    data class ComicUnreadable(override val detail: String) : PhotoError
}

/**
 * The camera app (ACTION_IMAGE_CAPTURE, no camera permission needed) writes into [captureUri]; [startBook] turns the
 * photo upright (its EXIF turn), scales it to `PhotoConfig.maxLongSidePx`, stores it as JPEG inside a one-page PDF
 * with no text layer ([ImagePdf]), and adds it to the library. The reader's scanned-page path (OCR, layout, junk
 * gate) reads it, with no `:pdf` change (ADR-043).
 *
 * Everything runs in this object's own scope, so turning the phone or leaving the screen keeps it, and the screen
 * only opens what [made] reports. The PDF is written beside the capture and moves into the shared copies only to be
 * added at once: an unreferenced copy there is pruned at the next start, so a half-made book never sits in it.
 * A marker beside the capture holds the title until the book is added; if the process dies first,
 * [resumePending] makes the book at the next start.
 *
 * Pictures picked or shared ([startBookFromImages]) become one book with a page per picture, in the order given
 * (ADR-066). Their URIs are lent for a moment, so they are read at once; a book cut short by the process dying is
 * not made again (the pictures are still in the gallery to pick again).
 */
@Suppress("TooManyFunctions") // Pictures, a camera capture and a comic archive each have their own way in.
class PhotoBooks(
    context: Context,
    private val copies: SharedPdfCopies,
    private val library: Library,
    private val config: PhotoConfig = PhotoConfig(),
) {
    private val appContext = context.applicationContext
    private val folder = File(appContext.cacheDir, config.captureFolder)
    private val captureFile = File(folder, CAPTURE_NAME)
    private val pendingFile = File(folder, PENDING_NAME)
    private val madeFile = File(folder, MADE_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = AtomicBoolean(false)
    private val mutableMade = MutableStateFlow<Outcome<BookId, PhotoError>?>(null)
    private val bookFile = PhotoBookFile(config)
    private val comicFile = ComicBookFile(appContext.contentResolver, File(appContext.cacheDir, COMIC_FOLDER), config)

    /** The last book's result until [consume]: the book to open, or why there is none. */
    val made: StateFlow<Outcome<BookId, PhotoError>?> = mutableMade.asStateFlow()

    /** Where the camera app writes the next photo; an older capture is deleted first. */
    fun captureUri(): Uri {
        folder.mkdirs()
        if (captureFile.exists() && !captureFile.delete()) AudoraLog.w(TAG, "An old capture could not be deleted")
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}$AUTHORITY_SUFFIX", captureFile)
    }

    /**
     * A photo book is being made from the one capture file: another photo now would overwrite it, so the caller
     * says so instead of opening the camera.
     */
    val making: Boolean get() = busy.get()

    /** Records [title] beside the photo just taken and makes the book; the result arrives in [made]. */
    fun startBook(title: String) {
        if (!busy.compareAndSet(false, true)) {
            AudoraLog.w(TAG, "A photo book is already being made")
            return
        }
        scope.launch {
            try {
                writeDurably(pendingFile, title.toByteArray())
                mutableMade.value = makeAndAdd(title)
            } catch (e: IOException) {
                mutableMade.value = failed(e)
            } finally {
                busy.set(false)
            }
        }
    }

    /**
     * [images] (content URIs from the photo picker, Files or a share) as one book named [title], a page each in
     * order; the result arrives in [made]. More than `PhotoConfig.maxImagesPerBook` is refused, not cut.
     */
    fun startBookFromImages(images: List<Uri>, title: String) {
        when {
            images.isEmpty() -> Unit

            images.size > config.maxImagesPerBook -> {
                AudoraLog.w(TAG, "Too many pictures for one book: ${images.size}")
                mutableMade.value = Outcome.Failure(PhotoError.TooMany(config.maxImagesPerBook))
            }

            !busy.compareAndSet(false, true) -> AudoraLog.w(TAG, "A photo book is already being made")

            else -> scope.launch {
                try {
                    // Picked or shared pictures are not Audora's: nothing to clean up once the book is in.
                    val sources = images.map { ImageDecoder.createSource(appContext.contentResolver, it) }
                    mutableMade.value = addMade(title, { bookFile.write(sources, madeFile) }) {}
                } finally {
                    busy.set(false)
                }
            }
        }
    }

    /**
     * A comic archive (.cbz) as one book named [title], a page per picture in name order
     * (ADR 2026-10-26-more-ebook-formats); the result arrives in [made]. The archive is copied in first (its URI may
     * be lent for a moment, and pages are read out of order), and the copy is deleted once the book is made.
     */
    fun startBookFromComic(archive: Uri, title: String) {
        if (!busy.compareAndSet(false, true)) {
            AudoraLog.w(TAG, "A photo book is already being made")
            return
        }
        scope.launch {
            try {
                mutableMade.value = makeComic(archive, title)
            } catch (e: ComicTooLargeException) {
                AudoraLog.w(TAG, "Comic archive too large: ${e.javaClass.simpleName}")
                mutableMade.value = Outcome.Failure(PhotoError.ComicTooLong(config.maxComicPages))
            } catch (e: IOException) {
                mutableMade.value = comicFailed(e)
            } catch (e: SecurityException) {
                mutableMade.value = comicFailed(e)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun comicFailed(e: Exception): Outcome<BookId, PhotoError> {
        AudoraLog.e(TAG, "Comic book not made: ${e.javaClass.simpleName}")
        return Outcome.Failure(PhotoError.ComicUnreadable(e.javaClass.simpleName))
    }

    private suspend fun makeComic(archive: Uri, title: String): Outcome<BookId, PhotoError> {
        val copy = comicFile.copyIn(archive)
        try {
            val pages = comicFile.pages(copy)
            AudoraLog.i(TAG, "Comic archive read, pages=${pages.size}")
            return when {
                pages.isEmpty() -> Outcome.Failure(PhotoError.ComicUnreadable("no pictures"))
                pages.size > config.maxComicPages -> Outcome.Failure(PhotoError.ComicTooLong(config.maxComicPages))
                else -> addMade(title, { comicFile.write(copy, pages, madeFile) }) {}
            }
        } finally {
            if (!copy.delete()) AudoraLog.w(TAG, "A comic archive's copy could not be deleted")
        }
    }

    /**
     * At start: a photo whose book was not added before the process died is made and added now. Without the photo
     * the marker is dropped (logged; nothing is shown, as there is nothing to retry).
     */
    fun resumePending() {
        scope.launch {
            if (!pendingFile.isFile || busy.get()) return@launch
            val title = try {
                pendingFile.readText().trim()
            } catch (e: IOException) {
                AudoraLog.w(TAG, "A photo book marker could not be read: ${e.javaClass.simpleName}")
                ""
            }
            if (title.isEmpty() || !captureFile.isFile) {
                AudoraLog.w(TAG, "An unfinished photo book was dropped: photo=${captureFile.isFile}")
                dropPending()
            } else {
                AudoraLog.i(TAG, "Finishing a photo book cut short")
                startBook(title)
            }
        }
    }

    /** Clears [made] once the screen has acted on it. */
    fun consume() {
        mutableMade.value = null
    }

    /** The camera app came back without a photo: drops whatever it may have half-written. */
    fun discardCapture() {
        if (captureFile.exists() && !captureFile.delete()) AudoraLog.w(TAG, "A cancelled capture could not be deleted")
    }

    /**
     * The photo as a one-page book named [title], added to the library. The capture and marker are deleted once the
     * book is added; after a failure the capture stays until the next photo, so nothing is lost to a bug in the
     * conversion, but the marker goes, so a failing photo is not retried at every start.
     */
    private suspend fun makeAndAdd(title: String): Outcome<BookId, PhotoError> {
        if (!captureFile.isFile || captureFile.length() == 0L) {
            dropPending()
            return Outcome.Failure(PhotoError.NoPhoto)
        }
        return addMade(title, { bookFile.write(listOf(ImageDecoder.createSource(captureFile)), madeFile) }) {
            if (!captureFile.delete()) AudoraLog.w(TAG, "The capture could not be deleted")
        }
    }

    /**
     * [write]s the book into [madeFile], moves it into the shared copies as "[title].pdf" and adds it to the library;
     * [onAdded] runs once it is in. Any failure is reported, and the half-made book deleted.
     */
    @Suppress("TooGenericExceptionCaught") // Any failure is reported to the user; a crash would lose the photo's book.
    private suspend fun addMade(title: String, write: () -> Unit, onAdded: () -> Unit): Outcome<BookId, PhotoError> {
        var copy: File? = null
        return try {
            // Only the camera path made the folder before; pictures and comics on a fresh install found none.
            folder.mkdirs()
            write()
            val target = copies.newCopy("$title.pdf").also { copy = it }
            moveInto(madeFile, target)
            val uri = Uri.fromFile(target).toString()
            when (val added = library.add(uri)) {
                is Outcome.Success -> {
                    if (added.value.book.documentUri != uri) target.parentFile?.deleteRecursively()
                    onAdded()
                    dropPending()
                    AudoraLog.i(TAG, "Photo book added, book=${added.value.book.id.logLabel}")
                    Outcome.Success(added.value.book.id)
                }

                is Outcome.Failure -> {
                    AudoraLog.e(TAG, "Photo book not added: ${added.error.javaClass.simpleName}")
                    target.parentFile?.deleteRecursively()
                    dropPending()
                    Outcome.Failure(PhotoError.NotAdded(added.error.javaClass.simpleName))
                }
            }
        } catch (e: Exception) {
            copy?.parentFile?.deleteRecursively()
            failed(e)
        }
    }

    private fun dropPending() {
        if (pendingFile.exists() && !pendingFile.delete()) AudoraLog.w(TAG, "A photo book marker could not be deleted")
        if (madeFile.exists() && !madeFile.delete()) AudoraLog.w(TAG, "A made photo book could not be deleted")
    }

    private fun failed(e: Exception): Outcome<BookId, PhotoError> {
        AudoraLog.e(TAG, "Photo book not made: ${e.javaClass.simpleName}")
        dropPending()
        return Outcome.Failure(PhotoError.Unreadable(e.javaClass.simpleName))
    }

    private companion object {
        const val TAG = "Audora.Photo"
        const val CAPTURE_NAME = "photo.jpg"
        const val PENDING_NAME = "photo.pending"
        const val MADE_NAME = "photo-book.pdf"
        const val AUTHORITY_SUFFIX = ".photos"
        const val COMIC_FOLDER = "comic-copies"
    }
}

/** A rename (cache and files share the data volume); a copy and delete if the rename is refused. */
private fun moveInto(from: File, to: File) {
    if (from.renameTo(to)) return
    from.copyTo(to, overwrite = true)
    if (!from.delete()) AudoraLog.w("Audora.Photo", "A made photo book could not be deleted after its copy")
}

/** Its own class, so its manifest entry does not collide with the other FileProviders (logs, clips). */
class PhotoFileProvider : FileProvider()
