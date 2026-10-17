// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/photo/PhotoBooks.kt
// Purpose: "Point and read": the camera app's photo becomes a one-page scanned book in the library (plan item 62).

package io.github.skm0078.audora.playback.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why a photo did not become a book; the UI says so, the log has the detail. */
sealed interface PhotoError {
    val detail: String

    /** The camera app returned without a photo, or wrote an empty file. */
    data object NoPhoto : PhotoError {
        override val detail = "no photo"
    }

    data class Unreadable(override val detail: String) : PhotoError
}

/**
 * The camera app (ACTION_IMAGE_CAPTURE, no camera permission needed) writes into [captureUri]; [toBook] turns the
 * photo upright (its EXIF turn), scales it to `PhotoConfig.maxLongSidePx`, stores it as JPEG inside a one-page PDF
 * with no text layer ([ImagePdf]) beside the shared copies and returns that file's URI. The library adds it like a
 * shared PDF, and the reader's scanned-page path (OCR, layout, junk gate) reads it, with no `:pdf` change (ADR-043).
 */
class PhotoBooks(
    context: Context,
    private val copies: SharedPdfCopies,
    private val config: PhotoConfig = PhotoConfig(),
) {
    private val appContext = context.applicationContext
    private val captureFile = File(File(appContext.cacheDir, config.captureFolder), CAPTURE_NAME)

    /** Where the camera app writes the next photo; an older capture is deleted first. */
    fun captureUri(): Uri {
        captureFile.parentFile?.mkdirs()
        if (captureFile.exists() && !captureFile.delete()) AudoraLog.w(TAG, "An old capture could not be deleted")
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}$AUTHORITY_SUFFIX", captureFile)
    }

    /** The photo as a one-page book named [title], as a file URI the library can add; the capture is then deleted. */
    suspend fun toBook(title: String): Outcome<String, PhotoError> = withContext(Dispatchers.IO) {
        if (!captureFile.isFile || captureFile.length() == 0L) return@withContext Outcome.Failure(PhotoError.NoPhoto)
        val target = copies.newCopy("$title.pdf")
        try {
            val bitmap = decodeUpright()
            val jpeg = ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality, out)) { "JPEG encoder failed" }
                out.toByteArray()
            }
            target.writeBytes(ImagePdf.write(jpeg, bitmap.width, bitmap.height, config.pageLongSidePt))
            AudoraLog.i(TAG, "Photo book made, ${bitmap.width}x${bitmap.height} px, bytes=${target.length()}")
            bitmap.recycle()
            Outcome.Success(Uri.fromFile(target).toString())
        } catch (e: IOException) {
            failed(target, e)
        } catch (e: IllegalStateException) {
            failed(target, e)
        } finally {
            if (!captureFile.delete()) AudoraLog.w(TAG, "The capture could not be deleted")
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

    private fun failed(target: File, e: Exception): Outcome<String, PhotoError> {
        AudoraLog.e(TAG, "Photo book not made: ${e.javaClass.simpleName}")
        target.parentFile?.deleteRecursively()
        return Outcome.Failure(PhotoError.Unreadable(e.javaClass.simpleName))
    }

    private companion object {
        const val TAG = "Audora.Photo"
        const val CAPTURE_NAME = "photo.jpg"
        const val AUTHORITY_SUFFIX = ".photos"
    }
}

/** Its own class, so its manifest entry does not collide with the other FileProviders (logs, clips). */
class PhotoFileProvider : FileProvider()
