// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookDetails.kt
// Purpose: A book's title, author, language and cover: what the file says about itself, what the listener set, and
//          how the two are merged so an edit is never overwritten (ADR-068).

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.model.Lang
import java.util.Locale

/** The fields a listener can edit. */
enum class BookField { TITLE, AUTHOR, LANGUAGE, COVER }

/** Which picture stands for a book in the library. */
sealed interface CoverChoice {
    /** The first page drawn (a PDF); a text book shows its placeholder. */
    data object FirstPage : CoverChoice

    /** Another page of a PDF, 0-based. */
    data class Page(val index: Int) : CoverChoice {
        init {
            require(index >= 0) { "page index must be >= 0, was $index" }
        }
    }

    /** A picture the listener chose from the gallery, kept by the app. */
    data object Picture : CoverChoice

    /** The cover image inside the book (an EPUB's), kept by the app. */
    data object FromBook : CoverChoice

    /** Whether the app keeps a picture file for it (gallery or the book's own), instead of drawing a page. */
    val isKeptPicture: Boolean get() = this == Picture || this == FromBook

    companion object {
        private const val PAGE_PREFIX = "page:"
        private const val PICTURE = "picture"
        private const val FROM_BOOK = "book"

        /** The stored form; [decode] reads it back. [FirstPage] is null, so books from before ADR-068 need nothing. */
        fun encode(choice: CoverChoice): String? = when (choice) {
            FirstPage -> null
            is Page -> PAGE_PREFIX + choice.index
            Picture -> PICTURE
            FromBook -> FROM_BOOK
        }

        /** Anything unknown or damaged is [FirstPage]. */
        fun decode(stored: String?): CoverChoice = when {
            stored == null -> FirstPage

            stored == PICTURE -> Picture

            stored == FROM_BOOK -> FromBook

            stored.startsWith(PAGE_PREFIX) ->
                stored.removePrefix(PAGE_PREFIX).toIntOrNull()?.takeIf { it >= 0 }?.let(::Page) ?: FirstPage

            else -> FirstPage
        }
    }
}

/**
 * What a book's file says about itself: a PDF's document information, an EPUB's package, a Word or OpenDocument
 * file's properties, an HTML `<title>`. Each field is null when the file does not say; [hasCoverImage] when it holds
 * a cover picture of its own (an EPUB).
 */
data class FileDetails(
    val format: DocumentFormat?,
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val hasCoverImage: Boolean = false,
)

/**
 * What the listener set in the book's Edit; a null field is left as it is. [fromFile] are fields handed back to the
 * file ("From the book"): no longer edited, and filled in again from the file.
 */
data class BookEdit(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val cover: CoverChoice? = null,
    val fromFile: Set<BookField> = emptySet(),
)

object BookDetails {
    /** Raise it when the readers learn more, so every book is filled in once more (its edits kept). */
    const val VERSION = 1

    /** Longer than this is not a title or an author but a sentence, or junk. */
    private const val MAX_FIELD_LENGTH = 200

    /** Titles that tools write when nobody set one; the file name is better than these. */
    private val PLACEHOLDER_TITLES = setOf("untitled", "title", "document", "no title", "microsoft word", "slide 1")
    private val TOOL_PREFIXES = listOf("microsoft word - ", "microsoft powerpoint - ", "untitled-")
    private val FILE_LIKE = Regex(".*\\.(pdf|docx?|odt|rtf|txt|html?|indd|qxd|tex|epub)$", RegexOption.IGNORE_CASE)

    /** Account names that tools write as the author. */
    private val PLACEHOLDER_AUTHORS = setOf("administrator", "admin", "user", "owner", "unknown", "author", "pc")

    /** [book] filled in from [file]: only what the listener did not edit, and only what looks real. */
    fun filled(book: Book, file: FileDetails): Book {
        val title = book.title.takeIf { BookField.TITLE in book.edited } ?: plausibleTitle(file.title) ?: book.title
        val author = if (BookField.AUTHOR in book.edited) book.author else plausibleAuthor(file.author) ?: book.author
        val language = if (BookField.LANGUAGE in book.edited) {
            book.language
        } else {
            languageTag(file.language) ?: book.language ?: scriptLanguage(title)
        }
        // A cover the listener did not choose is always the file's: its own picture, else its first page.
        val cover = when {
            BookField.COVER in book.edited -> book.cover
            file.hasCoverImage -> CoverChoice.FromBook
            else -> CoverChoice.FirstPage
        }
        return book.copy(
            title = title,
            author = author,
            language = language,
            format = file.format ?: book.format,
            cover = cover,
            detailsVersion = VERSION,
        )
    }

    /**
     * [book] as the listener edited it: each field given is set and marked edited, so a later fill keeps it. A blank
     * title is refused (the old one stays); a blank author or language clears the field, still marked edited.
     */
    fun edited(book: Book, edit: BookEdit): Book {
        val title = edit.title?.trim()?.take(MAX_FIELD_LENGTH)?.takeIf { it.isNotEmpty() }
        val fields = buildSet {
            addAll(book.edited)
            if (title != null) add(BookField.TITLE)
            if (edit.author != null) add(BookField.AUTHOR)
            if (edit.language != null) add(BookField.LANGUAGE)
            if (edit.cover != null) add(BookField.COVER)
            removeAll(edit.fromFile)
        }
        return book.copy(
            title = title ?: book.title,
            author = if (edit.author ==
                null
            ) {
                book.author
            } else {
                edit.author.trim().take(MAX_FIELD_LENGTH).ifEmpty { null }
            },
            language = if (edit.language == null) book.language else languageTag(edit.language),
            cover = edit.cover ?: book.cover,
            edited = fields,
            detailsVersion = if (edit.fromFile.isEmpty()) book.detailsVersion else 0,
        )
    }

    /** Back to what the file says: the listener's edits are dropped and the book is filled in again. */
    fun reset(book: Book): Book = book.copy(edited = emptySet(), cover = CoverChoice.FirstPage, detailsVersion = 0)

    /** "en-GB" → "en", "BN" → "bn"; null for blank or non-letters ("und", "zxx" mean no language). */
    fun languageTag(raw: String?): String? {
        val primary = raw?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT) ?: return null
        val known = primary.length in LANGUAGE_CODE_LENGTHS && primary.all { it in 'a'..'z' } && primary !in NO_LANGUAGE
        return primary.takeIf { known }
    }

    /** The language the [text]'s letters are written in, for the three the app reads; null for anything else. */
    fun scriptLanguage(text: String): String? {
        var latin = 0
        var devanagari = 0
        var bengali = 0
        text.forEach { char ->
            when (Character.UnicodeScript.of(char.code)) {
                Character.UnicodeScript.LATIN -> latin++
                Character.UnicodeScript.DEVANAGARI -> devanagari++
                Character.UnicodeScript.BENGALI -> bengali++
                else -> Unit
            }
        }
        val most = maxOf(latin, devanagari, bengali)
        return when {
            most == 0 -> null
            most == devanagari -> Lang.HI.bcp47Tag
            most == bengali -> Lang.BN.bcp47Tag
            else -> Lang.EN.bcp47Tag
        }
    }

    internal fun plausibleTitle(raw: String?): String? {
        val title = raw?.collapse() ?: return null
        val lower = title.lowercase(Locale.ROOT)
        val junk = title.length !in 2..MAX_FIELD_LENGTH ||
            lower in PLACEHOLDER_TITLES ||
            TOOL_PREFIXES.any { lower.startsWith(it) } ||
            FILE_LIKE.matches(title) ||
            title.none { it.isLetter() }
        return title.takeUnless { junk }
    }

    internal fun plausibleAuthor(raw: String?): String? {
        val author = raw?.collapse() ?: return null
        val junk = author.length !in 2..MAX_FIELD_LENGTH ||
            author.lowercase(Locale.ROOT) in PLACEHOLDER_AUTHORS ||
            author.none { it.isLetter() }
        return author.takeUnless { junk }
    }

    private fun String.collapse(): String = trim().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
    private val NO_LANGUAGE = setOf("und", "zxx", "mul", "mis")

    /** ISO 639-1 and 639-2/3 codes. */
    private const val SHORTEST_LANGUAGE_CODE = 2
    private const val LONGEST_LANGUAGE_CODE = 3
    private val LANGUAGE_CODE_LENGTHS = SHORTEST_LANGUAGE_CODE..LONGEST_LANGUAGE_CODE
}
