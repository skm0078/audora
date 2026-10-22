// File: app/src/main/kotlin/io/github/skm0078/audora/widget/ResumeWidgetBook.kt
// Purpose: What the home-screen widget shows (plan item 35): the book to continue and how far it is, kept for the
//          widget in a small preferences file the app writes and the widget reads.

package io.github.skm0078.audora.widget

import android.content.Context
import androidx.core.content.edit
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.ui.library.LibraryListState

/** The book read most recently, as the library's Continue reading card counts it. */
data class ResumeWidgetBook(val bookId: String, val title: String?, val page: Int, val pageCount: Int) {
    /** 0..1, the share of pages started: the library's progress measure. */
    val fraction: Float get() = ((page + 1).toFloat() / pageCount).coerceIn(0f, 1f)
}

/**
 * The widget's book for [list]: the most recently read book whose file is still there, whatever the library is
 * searched or filtered by. Null when no book was read yet; [Unchanged] while the library loads.
 */
fun resumeWidgetBook(list: LibraryListState): ResumeWidgetUpdate {
    val loaded = list as? LibraryListState.Loaded ?: return ResumeWidgetUpdate.Unchanged
    val latest = loaded.all.filterNot { it.needsLocating }
        .mapNotNull { item -> item.progress?.let { item to it } }
        .maxByOrNull { (_, progress) -> progress.savedAtMillis }
    return ResumeWidgetUpdate.Show(
        latest?.let { (item, progress) ->
            ResumeWidgetBook(
                bookId = item.book.id.value,
                title = item.book.title.takeUnless { it == BookTitles.UNTITLED },
                page = progress.page,
                pageCount = progress.pageCount,
            )
        },
    )
}

sealed interface ResumeWidgetUpdate {
    /** Nothing known yet: the widget keeps what it shows. */
    data object Unchanged : ResumeWidgetUpdate

    /** [book] null: nothing read yet, so the widget invites opening the app. */
    data class Show(val book: ResumeWidgetBook?) : ResumeWidgetUpdate
}

/**
 * The widget's copy of [ResumeWidgetBook]. SharedPreferences, not DataStore: the widget reads it synchronously
 * inside a broadcast, where a coroutine would need goAsync for four values.
 */
class ResumeWidgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): ResumeWidgetBook? {
        val id = prefs.getString(KEY_ID, null)?.takeIf { it.isNotBlank() }
        val count = prefs.getInt(KEY_COUNT, 0)
        return if (id == null || count <= 0) {
            null
        } else {
            ResumeWidgetBook(id, prefs.getString(KEY_TITLE, null), prefs.getInt(KEY_PAGE, 0), count)
        }
    }

    /** Saves [book] (null clears it); true when it differs from what was saved, so the widget needs redrawing. */
    fun write(book: ResumeWidgetBook?): Boolean {
        if (read() == book) return false
        prefs.edit {
            if (book == null) {
                clear()
            } else {
                putString(KEY_ID, book.bookId)
                putString(KEY_TITLE, book.title)
                putInt(KEY_PAGE, book.page)
                putInt(KEY_COUNT, book.pageCount)
            }
        }
        return true
    }

    private companion object {
        const val FILE = "resume_widget"
        const val KEY_ID = "book_id"
        const val KEY_TITLE = "title"
        const val KEY_PAGE = "page"
        const val KEY_COUNT = "page_count"
    }
}
