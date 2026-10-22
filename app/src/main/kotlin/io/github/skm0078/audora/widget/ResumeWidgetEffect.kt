// File: app/src/main/kotlin/io/github/skm0078/audora/widget/ResumeWidgetEffect.kt
// Purpose: The app's side of the home-screen widget (plan item 35): keeps it current, and continues the book its
//          Resume button asked for.

package io.github.skm0078.audora.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.core.library.BookId
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
    val context = LocalContext.current.applicationContext
    val update = resumeWidgetBook(list)
    LaunchedEffect(update) {
        if (update !is ResumeWidgetUpdate.Show) return@LaunchedEffect
        // The first read of the widget's preferences file loads it from disk.
        withContext(Dispatchers.IO) { ResumeWidgetProvider.publish(context, update.book) }
    }
    LaunchedEffect(resume) {
        if (resume == null) return@LaunchedEffect
        onTaken()
        continueReading(BookId(resume))
    }
}
