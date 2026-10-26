// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/ReadingSource.kt
// Purpose: Where the session gets its reading modes and the open book's chapter pages, for playlist and renderer alike.

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.voice.RenderBook
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import io.github.skm0078.audora.speech.route.CharacterVoices

/**
 * One per playback service. [modes] is read when a render epoch starts (a mode change restarts the sentence playing,
 * like any style change); [chapterPages] and [scanned] are set when a book opens. All are read on the render thread
 * too, so [chapterPages] and [scanned] are volatile and [modes] must be thread-safe.
 */
class ReadingSource(
    val modes: () -> ReadingModes = { ReadingModes() },
    val speakability: SpeakabilityConfig = SpeakabilityConfig(),
) {
    /** The open book's chapter start pages (PDF outline); empty with no book or no outline. */
    @Volatile
    var chapterPages: Set<Int> = emptySet()

    /** Whether a page of the open book was read by OCR (RI-11); set when a book opens. */
    @Volatile
    var scanned: (Int) -> Boolean = { false }

    /** Which sentences start inside a quote opened earlier in their paragraph (plan item 7); fed a page at a time. */
    val quotes: QuoteCarry = QuoteCarry()

    /** What the open book is (a story, a poem, the news), told from its pages as they are read (ADR-056). */
    val intent: BookIntent = BookIntent(readableFrom = speakability.skipBelow)

    /** One extracted page, before any of its sentences renders: to the quote tracking and the book's intent. */
    fun page(units: List<ReadingUnit>, continuesPreviousPage: Boolean) {
        quotes.page(units, continuesPreviousPage)
        intent.page(units)
    }

    /** A new book opened: forget the last one's quotes and intent. */
    fun clearBook() {
        quotes.clear()
        intent.clear()
    }

    /** What the renderer reads about the open book, live: set again each time a book opens. */
    fun book(fixer: () -> PronunciationFixer, characters: CharacterVoices? = null): RenderBook = RenderBook(
        chapterPages = { chapterPages },
        scanned = { page -> scanned(page) },
        fixer = fixer,
        startsInQuote = quotes::startsInQuote,
        speakerOf = quotes::speakerOf,
        moodOf = quotes::moodOf,
        characters = characters,
        intent = { intent.current },
        placeOf = quotes::placeOf,
    )

    /** The modes as they are now, for one render epoch's playlist. */
    fun current(): UnitReading = UnitReading(modes(), speakability, chapterPages)
}
