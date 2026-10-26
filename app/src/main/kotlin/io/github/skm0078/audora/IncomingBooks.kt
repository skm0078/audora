// File: app/src/main/kotlin/io/github/skm0078/audora/IncomingBooks.kt
// Purpose: Books that arrive from outside the library: pictures, comic archives, shared files and text, copied text
//          (ADR-066, ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.skm0078.audora.core.config.LibraryConfig
import io.github.skm0078.audora.core.files.readUpTo
import io.github.skm0078.audora.core.library.DocumentFormats
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.documents.displayName
import io.github.skm0078.audora.playback.photo.PhotoBooks
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pictures picked with Add book, from the gallery, or opened or shared from another app become one book with a page
 * each (ADR-066), made in the app's scope; [PhotoBookEffect] opens it. One picture keeps its own name as the title.
 */
internal fun Context.makePicturesBook(photoBooks: PhotoBooks, images: List<Uri>) {
    if (photoBooks.making) {
        Toast.makeText(this, R.string.photo_busy, Toast.LENGTH_LONG).show()
        return
    }
    val single = images.singleOrNull()?.let { contentResolver.displayName(it) }?.substringBeforeLast('.')
    val takenAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern(PICTURES_TITLE_DATE))
    val title = single?.takeIf { it.isNotBlank() } ?: getString(R.string.pictures_book_title, takenAt)
    photoBooks.startBookFromImages(images, title)
}

/**
 * What another app opened or shared: pictures become one photo book (ADR-066), a book file goes to [onBook] to be
 * added, and text (shared, or selected and sent with "Read aloud") to [onText] to be saved as a text book.
 */
internal fun Context.receiveShared(
    intent: Intent,
    photoBooks: PhotoBooks,
    onBook: (String) -> Unit,
    onText: (SharedText) -> Unit,
) {
    val images = SharedPdfIntent.imagesOf(intent, contentResolver::getType)
    if (images.isNotEmpty()) {
        makePicturesBook(photoBooks, images)
    } else {
        SharedPdfIntent.uriOf(intent)?.let(onBook)
        SharedPdfIntent.textOf(intent)?.let(onText)
    }
}

/** A file picked with Add book: a picture becomes a photo book, anything else goes to [add] as a book. */
internal fun Context.addPicked(uri: Uri, photoBooks: PhotoBooks, add: (String) -> Unit) {
    val type = try {
        contentResolver.getType(uri)
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "No type for a picked file: ${e.javaClass.simpleName}")
        null
    }
    if (SharedPdfIntent.isImage(type)) makePicturesBook(photoBooks, listOf(uri)) else add(uri.toString())
}

/**
 * A comic archive (.cbz), picked, opened or shared, becomes a photo book read by OCR
 * (ADR 2026-10-26-more-ebook-formats): true when [documentUri] is one and was taken (the book opens itself once made);
 * false for any other file, which is added as it is. Reads the file's first bytes, off the main thread.
 */
internal suspend fun Context.takeComic(photoBooks: PhotoBooks, documentUri: String): Boolean {
    val uri = Uri.parse(documentUri)
    if (!withContext(Dispatchers.IO) { isComicArchive(uri) }) return false
    if (photoBooks.making) {
        Toast.makeText(this, R.string.photo_busy, Toast.LENGTH_LONG).show()
    } else {
        val name = withContext(Dispatchers.IO) { contentResolver.displayName(uri) }?.substringBeforeLast('.')
        val title = name?.takeIf { it.isNotBlank() } ?: getString(R.string.comic_book_title)
        photoBooks.startBookFromComic(uri, title)
        Toast.makeText(this, getString(R.string.comic_making, title), Toast.LENGTH_LONG).show()
    }
    return true
}

private fun Context.isComicArchive(uri: Uri): Boolean = try {
    val window = LibraryConfig().pdfHeaderWindowBytes
    val head = contentResolver.openInputStream(uri)?.use { it.readUpTo(window) }
    head != null && DocumentFormats.isComicArchive(head, window)
} catch (e: IOException) {
    AudoraLog.w(TAG, "A picked file could not be checked for a comic: ${e.javaClass.simpleName}")
    false
} catch (e: SecurityException) {
    AudoraLog.w(TAG, "A picked file could not be checked for a comic: ${e.javaClass.simpleName}")
    false
}

/** "Read copied text": the text copied last, handed to [onText] like text shared to ListenLock (ADR-066). */
internal fun Context.readClipboard(onText: (SharedText) -> Unit) {
    val text = clipboardText(this)
    if (text == null) {
        Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_LONG).show()
    } else {
        onText(SharedText(title = null, text = text))
    }
}

/** The clipboard's text, or null when nothing (or only a picture) was copied. */
private fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        ?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }

private const val TAG = "Audora.Incoming"

/** A picture book's title date: no colon or slash, since the title is also its file name. */
private const val PICTURES_TITLE_DATE = "d MMM yyyy, HH.mm"
