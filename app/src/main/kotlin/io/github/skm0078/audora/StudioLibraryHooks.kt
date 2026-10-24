// File: app/src/main/kotlin/io/github/skm0078/audora/StudioLibraryHooks.kt
// Purpose: MainActivity's library-side studio features: notes export, point and read, shared text.

package io.github.skm0078.audora

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.config.TextBookConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.epub.BookCopies
import io.github.skm0078.audora.playback.epub.CopyError
import io.github.skm0078.audora.playback.export.AudiobookExport
import io.github.skm0078.audora.playback.export.MarkStores
import io.github.skm0078.audora.playback.export.NotesExport
import io.github.skm0078.audora.playback.photo.PhotoBooks
import io.github.skm0078.audora.playback.photo.PhotoError
import io.github.skm0078.audora.ui.AppDestination
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Jobs a notification's Continue reopens the app for: a talking book or copy, or an audiobook export (ADR-034). */
internal fun Context.continueStudioJobs(intent: Intent) {
    BookCopies.continueFrom(this, intent)
    AudiobookExport.continueFrom(this, intent)
}

/** Plan item 30: the book's highlights, bookmarks and voice notes to Documents/ListenLock; a toast says where. */
internal fun Context.exportNotes(scope: CoroutineScope, stores: MarkStores, book: Book) {
    scope.launch {
        val message = when (val saved = NotesExport.export(applicationContext, book.id, book.title, stores)) {
            is Outcome.Success -> getString(R.string.notes_export_done, saved.value.joinToString(", "))

            is Outcome.Failure -> {
                val empty = saved.error == CopyError.NothingToCopy
                getString(if (empty) R.string.notes_export_empty else R.string.notes_export_failed)
            }
        }
        Toast.makeText(this@exportNotes, message, Toast.LENGTH_LONG).show()
    }
}

/** Plan item 62: the camera app takes the photo (no camera permission); [makePhotoBook] reads what it saved. */
internal fun Context.pointAndRead(photoBooks: PhotoBooks, takePhoto: ActivityResultLauncher<Uri>) {
    if (photoBooks.making) {
        Toast.makeText(this, R.string.photo_busy, Toast.LENGTH_LONG).show()
        return
    }
    try {
        takePhoto.launch(photoBooks.captureUri())
    } catch (e: ActivityNotFoundException) {
        AudoraLog.w(STUDIO_TAG, "No camera app: ${e.javaClass.simpleName}")
        Toast.makeText(this, R.string.photo_no_camera, Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        // Thrown if a CAMERA permission is ever declared but not granted; the manifest removes any merged one.
        AudoraLog.w(STUDIO_TAG, "Camera app refused: ${e.javaClass.simpleName}")
        Toast.makeText(this, R.string.photo_no_camera, Toast.LENGTH_LONG).show()
    }
}

/** The photo becomes a book, added in the app's scope (rotation keeps it); [PhotoBookEffect] opens it. */
internal fun Context.makePhotoBook(photoBooks: PhotoBooks) {
    val takenAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern(PHOTO_TITLE_DATE))
    photoBooks.startBook(getString(R.string.photo_book_title, takenAt))
}

/** A photo book already in the library is opened, whichever activity instance is showing. */
@Composable
internal fun PhotoBookEffect(photoBooks: PhotoBooks, navigate: (AppDestination) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val made by photoBooks.made.collectAsStateWithLifecycle()
    LaunchedEffect(made) {
        val result = made ?: return@LaunchedEffect
        photoBooks.consume()
        when (result) {
            is Outcome.Success -> navigate(AppDestination.Reader(result.value))

            is Outcome.Failure -> {
                val error = result.error
                val message = if (error is PhotoError.TooMany) {
                    resources.getString(R.string.pictures_too_many, error.max)
                } else {
                    resources.getString(R.string.photo_failed)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * Text shared from another app (plan item 42): saved as a .txt beside the shared copies, then handed to [onSaved]
 * to be added and opened like a shared PDF. [onTaken] clears the request first.
 */
@Composable
internal fun SharedTextEffect(
    shared: SharedText?,
    copies: SharedPdfCopies,
    onTaken: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(shared) {
        val text = shared ?: return@LaunchedEffect
        onTaken()
        val saved = if (text.isOnlyLink) {
            null
        } else {
            copies.saveText(
                text.text,
                text.title,
                TextBookConfig().maxSharedChars,
            )
        }
        if (saved != null) {
            onSaved(saved)
        } else {
            val message = if (text.isOnlyLink) R.string.shared_text_link else R.string.shared_text_failed
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

private const val STUDIO_TAG = "Audora.Main"

/** A photo book's title date: no colon or slash, since the title is also its file name (plan item 62). */
private const val PHOTO_TITLE_DATE = "d MMM yyyy, HH.mm"
