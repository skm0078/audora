// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/covers/CoverFolders.kt
// Purpose: Where each book's cover is kept (plan item 21, ADR-068), so the library draws it and the notification,
//          lock screen, car screen and widget show the same picture without drawing it again.

package io.github.skm0078.audora.playback.covers

import android.net.Uri
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.CoverChoice
import java.io.File

/**
 * `covers/<id>.webp` for a drawn first page, `covers/<id>@p<n>.webp` for another page, `covers-chosen/<id>.webp` for
 * a kept picture. A file's book id is its name up to the page mark.
 */
class CoverFolders(filesDir: File) {
    val drawn = File(filesDir, DRAWN)
    val chosen = File(filesDir, CHOSEN)

    fun drawnFile(id: BookId, page: Int) =
        File(drawn, if (page == FIRST_PAGE) "${id.value}$EXTENSION" else "${id.value}$PAGE_MARK$page$EXTENSION")

    fun chosenFile(id: BookId) = File(chosen, "${id.value}$EXTENSION")

    fun bookIdOf(fileName: String) =
        fileName.substringBefore(PAGE_MARK).removeSuffix(PARTIAL_SUFFIX).removeSuffix(EXTENSION)

    /**
     * The cover the library shows for [book], when it has been made already: its kept picture, else its chosen page,
     * else its first page. Null for a text book without a picture, or a cover the library has not drawn yet. Never
     * draws one: callers outside the library (the notification, the widget) must stay cheap.
     */
    fun shownFile(book: Book): File? = when {
        book.cover.isKeptPicture -> chosenFile(book.id).takeIf(File::isFile)

        book.format?.isText == true -> null

        else -> {
            val page = (book.cover as? CoverChoice.Page)?.index ?: FIRST_PAGE
            listOf(drawnFile(book.id, page), drawnFile(book.id, FIRST_PAGE)).firstOrNull(File::isFile)
        }
    }

    companion object {
        const val FIRST_PAGE = 0
        const val PARTIAL_SUFFIX = ".part"
        private const val DRAWN = "covers"
        private const val CHOSEN = "covers-chosen"
        private const val PAGE_MARK = "@p"
        private const val EXTENSION = ".webp"
        private const val VERSION_PARAMETER = "v"

        /**
         * [file] as artwork for the media session. The file's time rides along, so a replaced cover is a new address
         * and the session's bitmap cache loads it again instead of showing the old picture.
         */
        fun artworkUri(file: File): Uri =
            Uri.fromFile(file).buildUpon().appendQueryParameter(VERSION_PARAMETER, file.lastModified().toString())
                .build()
    }
}
