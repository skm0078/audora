// File: app/src/main/kotlin/io/github/skm0078/audora/widget/ResumeWidgetEffect.kt
// Purpose: The app's side of the home-screen widget (plan item 35): keeps it and the app icon's Resume shortcut
//          current, and continues the book its Resume button asked for.

package io.github.skm0078.audora.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.playback.covers.CoverFolders
import io.github.skm0078.audora.shortcuts.publishAppShortcuts
import io.github.skm0078.audora.ui.library.LibraryListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @param resume the book id from a Resume tap, if one is waiting; [onTaken] clears it once handled.
 * @param continueReading opens the reader on a book and starts reading, as the library's Continue button does.
 */
@Composable
fun ResumeWidgetEffect(
    list: LibraryListState,
    resume: String?,
    onTaken: () -> Unit,
    continueReading: (BookId) -> Unit,
) {
    val activity = LocalContext.current
    val context = activity.applicationContext
    val covers = remember(context) { CoverFolders(context.filesDir) }
    // The cover file is checked on the main thread: one stat per library change, no decoding.
    val update = resumeWidgetBook(list) { book -> covers.shownFile(book)?.path }
    LaunchedEffect(update) {
        if (update !is ResumeWidgetUpdate.Show) return@LaunchedEffect
        // The first read of the widget's preferences file loads it from disk.
        withContext(Dispatchers.IO) { ResumeWidgetProvider.publish(context, update.book) }
    }
    // Only when the book or its title changes, not on every page; the activity's strings are in the app's language.
    val shown = (update as? ResumeWidgetUpdate.Show)?.let { it.book?.bookId to it.book?.title }
    LaunchedEffect(shown) {
        if (shown == null) return@LaunchedEffect
        val book = (update as ResumeWidgetUpdate.Show).book
        withContext(Dispatchers.IO) { publishAppShortcuts(activity, book) }
    }
    LaunchedEffect(resume) {
        if (resume == null) return@LaunchedEffect
        onTaken()
        continueReading(BookId(resume))
    }
}
