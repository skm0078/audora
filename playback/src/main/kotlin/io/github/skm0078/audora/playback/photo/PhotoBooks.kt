// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/photo/PhotoBooks.kt
// Purpose: "Point and read": the camera app's photo becomes a one-page scanned book in the library (plan item 62).

package io.github.skm0078.audora.playback.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.backup.writeDurably
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
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
 */
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
    @Suppress("TooGenericExceptionCaught") // Any failure is reported to the user; a crash would lose the photo's book.
    private suspend fun makeAndAdd(title: String): Outcome<BookId, PhotoError> {
        if (!captureFile.isFile || captureFile.length() == 0L) {
            dropPending()
            return Outcome.Failure(PhotoError.NoPhoto)
        }
        var copy: File? = null
        return try {
            writeBook()
            val target = copies.newCopy("$title.pdf").also { copy = it }
            moveInto(madeFile, target)
            val uri = Uri.fromFile(target).toString()
            when (val added = library.add(uri)) {
                is Outcome.Success -> {
                    if (added.value.book.documentUri != uri) target.parentFile?.deleteRecursively()
                    if (!captureFile.delete()) AudoraLog.w(TAG, "The capture could not be deleted")
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

    /** The photo, upright and scaled, as a one-page PDF in [madeFile]. */
    private fun writeBook() {
        val bitmap = decodeUpright()
        try {
            val jpeg = ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality, out)) { "JPEG encoder failed" }
                out.toByteArray()
            }
            writeDurably(madeFile, ImagePdf.write(jpeg, bitmap.width, bitmap.height, config.pageLongSidePt))
            AudoraLog.i(TAG, "Photo book made, ${bitmap.width}x${bitmap.height} px, bytes=${madeFile.length()}")
        } finally {
            bitmap.recycle()
        }
    }

    /** ImageDecoder applies the EXIF turn and caps the long side while decoding: a 50 MP photo never loads whole. */
    private fun decodeUpright(): Bitmap {
        val source = ImageDecoder.createSource(captureFile)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > config.maxLongSidePx) {
                val scale = config.maxLongSidePx.toDouble() / longSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
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
