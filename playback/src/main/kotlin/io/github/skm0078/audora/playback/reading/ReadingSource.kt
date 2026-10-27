// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/ReadingSource.kt
// Purpose: Where the session gets its reading modes and the open book's chapter pages, for playlist and renderer alike.

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.brain.StoryBrainReader
import io.github.skm0078.audora.playback.voice.RenderBook
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import io.github.skm0078.audora.speech.route.CharacterVoices
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/**
 * One per playback service. [modes] is read when a render epoch starts (a mode change restarts the sentence playing,
 * like any style change); [chapterPages] and [scanned] are set when a book opens. All are read on the render thread
 * too, so [chapterPages] and [scanned] are volatile and [modes] must be thread-safe. [brain]: the opt-in story brain
 * (ADR story-brain); its calls on who speaks ([speakerOf]) and how fill in where the rules' are unsure.
 */
class ReadingSource(
    val modes: () -> ReadingModes = { ReadingModes() },
    val speakability: SpeakabilityConfig = SpeakabilityConfig(),
    val brain: StoryBrainReader? = null,
) {
    /** The open book's chapter start pages (PDF outline); empty with no book or no outline. */
    @Volatile
    var chapterPages: Set<Int> = emptySet()

    /** Whether a page of the open book was read by OCR (RI-11); set when a book opens. */
    @Volatile
    var scanned: (Int) -> Boolean = { false }

    /**
     * The open book's language tag as the library holds it (ADR 2026-10-26); set when a book opens and when the
     * listener changes it in Edit details. Null for none.
     */
    @Volatile
    var language: String? = null

    /** Which sentences start inside a quote opened earlier in their paragraph (plan item 7); fed a page at a time. */
    val quotes: QuoteCarry = QuoteCarry()

    /** What the open book is (a story, a poem, the news), told from its pages as they are read (ADR-056). */
    val intent: BookIntent = BookIntent(readableFrom = speakability.skipBelow)

    /**
     * One extracted page, before any of its sentences renders: to the quote tracking and the book's intent, and to
     * the story brain, which reads it (and the pages after it) on its own thread.
     */
    fun page(units: List<ReadingUnit>, continuesPreviousPage: Boolean) {
        quotes.page(units, continuesPreviousPage)
        intent.page(units)
        units.firstOrNull()?.let { brain?.page(units, scanned(it.page)) }
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
        speakerOf = ::speakerOf,
        moodOf = quotes::moodOf,
        judgedFeeling = { unit -> brain?.feelingOf(unit) },
        characters = characters,
        intent = { intent.current },
        language = { language },
        placeOf = quotes::placeOf,
    )

    /**
     * Who says [unit]: the rules' speaker when they also know the gender (a tag like "said Mira": they are rarely
     * wrong then), else the story brain's, else the rules' (ADR story-brain).
     */
    fun speakerOf(unit: ReadingUnit): Speaker? {
        val ruled = quotes.speakerOf(unit)
        return ruled?.takeIf { it.gender != SpeakerGender.UNKNOWN } ?: brain?.speakerOf(unit) ?: ruled
    }

    /** The modes as they are now, for one render epoch's playlist. */
    fun current(): UnitReading = UnitReading(modes(), speakability, chapterPages, language = language)
}
