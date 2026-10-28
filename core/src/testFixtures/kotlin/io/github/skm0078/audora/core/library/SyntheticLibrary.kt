// File: core/src/testFixtures/kotlin/io/github/skm0078/audora/core/library/SyntheticLibrary.kt
// Purpose: A made-up reader's library of any size for the load tests (ADR 2026-10-28-performance-budget): the same
//          books on the JVM, on the emulator and in the release journey.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.resume.ResumeAnchor

/**
 * In :core's test fixtures, never in the app, so the JVM and emulator tests build the very same books. Three scripts,
 * authors (some missing), four formats, [COLLECTIONS] collections, and every [READING_EVERY]th book half read. Every
 * title ends in a 4-digit number, so no title is part of another and a search for one finds that book alone (up to
 * 9,999 books).
 */
object SyntheticLibrary {
    const val COLLECTIONS = 20
    const val READING_EVERY = 3
    private const val NOW = 1_800_000_000_000L
    private const val MINUTE_MS = 60_000L
    private const val BASE_PAGES = 100
    private const val PAGE_SPREAD = 400
    private const val BASE_BYTES = 1_000L
    private const val PIPELINE = 1
    private val NAMES = listOf("Gitanjali", "गोदान", "পথের পাঁচালী", "Pride and Prejudice", "निर्मला", "গোরা")
    private val AUTHORS = listOf("Rabindranath Tagore", "प्रेमचंद", "বিভূতিভূষণ", "Jane Austen", null)
    private val LANGUAGES = listOf("en", "hi", "bn")
    private val FORMATS = listOf(DocumentFormat.PDF, DocumentFormat.EPUB, DocumentFormat.TEXT, DocumentFormat.DOCX)

    /** Book [i]'s title, also the name of a seeded file. */
    fun titleOf(i: Int, name: String = NAMES[i % NAMES.size]): String = "$name ${"%04d".format(i + 1)}"

    fun books(count: Int): List<Book> = List(count) { i ->
        Book(
            id = BookId("load-$i"),
            documentUri = "content://load/$i",
            title = titleOf(i),
            sizeBytes = BASE_BYTES + i,
            fingerprint = "v1:load-$i",
            pageCount = BASE_PAGES + i % PAGE_SPREAD,
            addedAtMillis = NOW - i * MINUTE_MS,
            lastOpenedAtMillis = (NOW - i * MINUTE_MS).takeIf { i % READING_EVERY == 0 },
            author = AUTHORS[i % AUTHORS.size],
            language = LANGUAGES[i % LANGUAGES.size],
            format = FORMATS[i % FORMATS.size],
        )
    }

    /** The books being read: each has a reading position in [anchors]. */
    fun read(books: List<Book>): List<Book> = books.filterIndexed { i, _ -> i % READING_EVERY == 0 }

    /** A position in each read book's first half, so none counts as finished. */
    fun anchors(books: List<Book>): List<ResumeAnchor> = read(books).mapIndexed { i, book ->
        val page = i % ((book.pageCount ?: 2) / 2)
        ResumeAnchor(book.id, page, 0, 0L, 0L, PIPELINE, NOW - i * MINUTE_MS)
    }

    fun collections(): List<BookCollection> = List(COLLECTIONS) { BookCollection("c$it", "Shelf $it", NOW) }

    fun members(books: List<Book>): Map<BookId, Set<String>> =
        books.withIndex().associate { (i, book) -> book.id to setOf("c${i % COLLECTIONS}") }
}
