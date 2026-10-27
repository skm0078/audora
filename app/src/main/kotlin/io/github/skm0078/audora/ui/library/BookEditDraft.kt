// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookEditDraft.kt
// Purpose: What the Edit details sheet holds while the listener types (ADR-068), and the BookEdit it saves: only the
//          fields that were changed, so a field left alone keeps following the file.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookEdit
import io.github.skm0078.audora.core.library.BookField
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.CoverChoice
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.PhoneLanguages

/** The cover options the sheet offers. */
enum class CoverOption { FIRST_PAGE, PAGE, GALLERY, FROM_BOOK }

/**
 * @property language a language tag, or "" for "As the book says".
 * @property page the typed page number, 1-based, as typed.
 * @property picture a picked gallery picture's URI, kept only once Save is pressed.
 */
data class BookEditDraft(
    val title: String,
    val author: String,
    val language: String,
    val cover: CoverOption,
    val page: String = "",
    val picture: String? = null,
) {
    /** The page typed, 0-based, when it is a page of the book. */
    fun pageIndex(pageCount: Int?): Int? =
        page.trim().toIntOrNull()?.takeIf { it >= 1 && (pageCount == null || it <= pageCount) }?.minus(1)

    /** Whether Save can be pressed: a title, and a page or a picture when that cover was chosen. */
    fun savable(book: Book): Boolean = title.isNotBlank() &&
        (cover != CoverOption.PAGE || pageIndex(book.pageCount) != null) &&
        (cover != CoverOption.GALLERY || picture != null || book.cover == CoverChoice.Picture)

    /** Only what differs from [book]; "As the book says" hands the language back to the file. */
    fun toEdit(book: Book): BookEdit {
        val cover = when (cover) {
            CoverOption.FIRST_PAGE -> CoverChoice.FirstPage
            CoverOption.PAGE -> pageIndex(book.pageCount)?.let(CoverChoice::Page)
            CoverOption.GALLERY -> CoverChoice.Picture
            CoverOption.FROM_BOOK -> CoverChoice.FromBook
        }
        val auto = language.isEmpty()
        val languageEdited = BookField.LANGUAGE in book.edited
        return BookEdit(
            title = title.trim().takeIf { it != shownTitle(book) },
            author = author.trim().takeIf { it != book.author.orEmpty() },
            language = language.takeIf { !auto && (it != book.language || !languageEdited) },
            cover = cover.takeIf { it != book.cover || picture != null },
            fromFile = if (auto && languageEdited) setOf(BookField.LANGUAGE) else emptySet(),
        )
    }

    companion object {
        /** The languages offered first, besides "As the book says": the three with ListenLock's own voices. */
        val LANGUAGES = listOf(Lang.EN, Lang.HI, Lang.BN).map { it.bcp47Tag }

        fun of(book: Book) = BookEditDraft(
            title = shownTitle(book),
            author = book.author.orEmpty(),
            language = book.language?.takeIf { BookField.LANGUAGE in book.edited }.orEmpty(),
            cover = when (val cover = book.cover) {
                CoverChoice.FirstPage -> CoverOption.FIRST_PAGE
                is CoverChoice.Page -> CoverOption.PAGE
                CoverChoice.Picture -> CoverOption.GALLERY
                CoverChoice.FromBook -> CoverOption.FROM_BOOK
            },
            page = (book.cover as? CoverChoice.Page)?.let { (it.index + 1).toString() }.orEmpty(),
        )

        /** The cover options that fit [book]: a page only of a PDF, the book's own only of an EPUB. */
        fun coverOptions(book: Book): List<CoverOption> = buildList {
            add(CoverOption.FIRST_PAGE)
            if (book.format == DocumentFormat.PDF || (book.format == null && book.pageCount != null)) {
                add(CoverOption.PAGE)
            }
            add(CoverOption.GALLERY)
            if (book.format == DocumentFormat.EPUB) add(CoverOption.FROM_BOOK)
        }

        /**
         * The languages offered after [LANGUAGES] (ADR 2026-10-26): those the phone reads offline ([offline]), and the
         * one [selected] if it is none of them, so an edit made before a voice pack went is still shown. Each once,
         * ordered by [nameOf].
         */
        fun phoneChoices(offline: List<String>, selected: String, nameOf: (String) -> String): List<String> =
            (offline + selected).mapNotNull(PhoneLanguages::phoneLanguage).distinct().sortedBy(nameOf)

        /**
         * The phone language [book] is read in with [selected] ("" for "As the book says") when the phone has no
         * offline voice for it, so the sheet can say so; null when it has one, the language is one of the three,
         * or [offline] is not known yet.
         */
        fun missingVoice(book: Book, selected: String, offline: List<String>?): String? {
            val language = PhoneLanguages.phoneLanguage(selected.ifEmpty { book.language.orEmpty() })
            return language?.takeIf { offline != null && it !in offline.mapNotNull(PhoneLanguages::canonical) }
        }

        private fun shownTitle(book: Book) = book.title.takeUnless { it == BookTitles.UNTITLED }.orEmpty()
    }
}
