// File: app/src/main/kotlin/io/github/skm0078/audora/KeepOnPhoneAction.kt
// Purpose: The book menu's "Keep on this phone" (ADR 2026-10-28-book-source): copies a book read through Drive or
//          another app in the app's scope, one at a time, with a toast when it starts and when it ends.

package io.github.skm0078.audora

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.KeepOnPhone
import io.github.skm0078.audora.core.library.KeepResult
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/** One copy at a time: a cloud app fetching two big books at once only makes both slower. */
private val copying = AtomicBoolean(false)

/**
 * A Drive download of a big book takes a while, so the copy runs in the app's scope: closing the sheet, turning the
 * phone or leaving the library neither stops it nor loses its message. The library list updates by itself once the
 * book points at its copy.
 */
internal fun ComponentActivity.keepOnPhone(book: Book) {
    val app = applicationContext
    val title = book.title.takeUnless { it == BookTitles.UNTITLED } ?: app.getString(R.string.library_untitled)
    if (!copying.compareAndSet(false, true)) {
        Toast.makeText(app, R.string.keep_on_phone_busy, Toast.LENGTH_LONG).show()
        return
    }
    Toast.makeText(app, app.getString(R.string.keep_on_phone_started, title), Toast.LENGTH_SHORT).show()
    (application as AudoraApplication).appScope.launch {
        try {
            val copies = SharedPdfCopies(app)
            val keeper = KeepOnPhone(LibraryEntryPoint.of(app).library(), copies::copy) { copies.delete(Uri.parse(it)) }
            val result = keeper.keep(book)
            Toast.makeText(app, app.getString(messageOf(result), title), Toast.LENGTH_LONG).show()
        } finally {
            copying.set(false)
        }
    }
}

@StringRes
private fun messageOf(result: KeepResult): Int = when (result) {
    KeepResult.KEPT -> R.string.keep_on_phone_done
    KeepResult.ALREADY_ON_PHONE -> R.string.keep_on_phone_already
    KeepResult.NOT_COPIED -> R.string.keep_on_phone_not_copied
    KeepResult.NOT_MOVED -> R.string.keep_on_phone_not_moved
}
