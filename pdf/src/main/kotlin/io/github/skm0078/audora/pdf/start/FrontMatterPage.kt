// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/start/FrontMatterPage.kt
// Purpose: Tells what one of a book's first pages is from its sentences: contents, copyright, a front-matter or
//          chapter heading, a short title page, or running text (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.pdf.start

import io.github.skm0078.audora.core.config.ContentStartConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind

/** What a page looks like to the start search. Titles are the book's text: shown, never logged. */
sealed interface PageLook {
    /** No words, or only a few: a cover, title, half-title, dedication or epigraph page. */
    data object Short : PageLook

    /** Mostly lines that end in page numbers. */
    data object Contents : PageLook

    /** ISBN, "All rights reserved", the publisher and printer. */
    data object Copyright : PageLook

    /** Opens with a front-matter heading ("Preface", "भूमिका"); its section may run on over the next pages. */
    data class FrontHeading(val kind: FrontMatterKind) : PageLook

    /** Opens with a chapter heading ("Chapter 1", "অধ্যায় ১", "IV"). */
    data class Chapter(val title: String) : PageLook {
        override fun toString() = "Chapter"
    }

    /** Running text; [heading] is its first line when that is set as a heading (a chapter named only by its title). */
    data class Prose(val heading: String?) : PageLook {
        override fun toString() = "Prose(headed=${heading != null})"
    }
}

class FrontMatterPage(private val config: ContentStartConfig) {
    /**
     * Contents come first: a contents page's first line is often "Chapter 1 … 13", which would otherwise read as the
     * chapter itself. Headings come before the copyright words, so a first chapter that names its publisher and
     * edition is still the first chapter.
     */
    fun look(units: List<ReadingUnit>): PageLook {
        val words = units.flatMap { unit -> unit.text.split(WHITESPACE).filter { it.any(Char::isLetterOrDigit) } }
        return if (isContents(words)) PageLook.Contents else headingLook(units) ?: bodyLook(units, words)
    }

    /**
     * The page holds running text. On a book's first page that settles it: a document whose title, a "Preface" or
     * "মুখবন্ধ" heading and its text share page 1 starts there; real front matter gives the title a page of its own.
     */
    fun hasRunningText(units: List<ReadingUnit>): Boolean = units.sumOf(::letterWords) >= config.proseWords

    private fun letterWords(unit: ReadingUnit): Int = unit.text.split(WHITESPACE).count { it.any(Char::isLetter) }

    private fun bodyLook(units: List<ReadingUnit>, words: List<String>): PageLook = when {
        isCopyright(units, words.size) -> PageLook.Copyright
        words.count { word -> word.any(Char::isLetter) } < config.proseWords -> PageLook.Short
        else -> proseLook(units)
    }

    private fun isContents(words: List<String>): Boolean {
        val numbers = words.count { PAGE_NUMBER.matches(it) }
        return numbers >= config.contentsNumbers && numbers >= words.size * config.contentsNumberShare
    }

    private fun isCopyright(units: List<ReadingUnit>, words: Int): Boolean {
        if (words > config.copyrightMaxWords) return false
        val raw = units.joinToString(" ") { it.text }
        val text = FrontMatterWords.normalize(raw)
        val cues = COPYRIGHT_CUES.count { it in text } + (if ('©' in raw) 1 else 0)
        return cues >= config.copyrightCues
    }

    /** The page's first one or two short lines, when one names front matter or a chapter. */
    private fun headingLook(units: List<ReadingUnit>): PageLook? = units
        .take(LEAD_UNITS)
        .filter { it.kind == UnitKind.HEADING || it.wordCount() <= config.headingWords }
        .firstNotNullOfOrNull { unit ->
            FrontMatterWords.kindOf(unit.text)?.let(PageLook::FrontHeading)
                ?: unit.text.takeIf(FrontMatterWords::isChapterHeading)?.let { PageLook.Chapter(it.trim()) }
        }

    private fun proseLook(units: List<ReadingUnit>): PageLook.Prose {
        val first = units.firstOrNull { unit -> unit.text.any(Char::isLetter) }
        val heading = first?.takeIf { it.kind == UnitKind.HEADING && it.wordCount() <= config.headingWords }
        return PageLook.Prose(heading?.text?.trim())
    }

    private fun ReadingUnit.wordCount() = text.split(WHITESPACE).count { it.isNotBlank() }

    private companion object {
        /**
         * A contents line's page number: "13", "১২৩", "४५", or a small Roman one ("xi"), with leaders or a full stop
         * stuck to it. Capital "I" is the pronoun far more often than page one, so Roman numbers count in lower case.
         */
        val PAGE_NUMBER = Regex("""^[.…·]*([0-9০-৯०-९]{1,4}|[ivxlc]{1,6})[.,]?$""")
        val WHITESPACE = Regex("""\s+""")

        /** The first two lines are enough: a heading sits at the top of its page. */
        const val LEAD_UNITS = 2

        /** Words of a copyright page, matched in the page's [FrontMatterWords.normalize]d text. */
        val COPYRIGHT_CUES = listOf(
            "copyright", "isbn", "all rights reserved", "published by", "first published", "printed in",
            "printed by", "printed at", "publisher", "edition", "reprint", "library of congress", "catalog",
            "no part of this", "typeset", "cover design", "सर्वाधिकार", "प्रकाशक", "मुद्रक", "संस्करण", "मूल्य",
            "आवरण", "প্রকাশক", "মুদ্রক", "মুদ্রণ", "সংস্করণ", "প্রথম প্রকাশ", "স্বত্ব", "প্রচ্ছদ", "মূল্য",
        ).map(FrontMatterWords::normalize)
    }
}
