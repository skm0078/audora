// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/ChapterMarkers.kt
// Purpose: Finds "Chapter 12 …" lines in a book's own text, for a book whose table of contents names next to nothing.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.config.TextBookConfig

/**
 * A scanned book turned into an EPUB (the Internet Archive's, for one) lists only a "Notice" in its contents and has
 * no headings: each scanned page is one paragraph, and "CHAPTER 38 The Ascent of Mount Suvela HAVING resolved…" sits
 * in the middle of one. Two kinds of line count as a chapter start:
 * - a short block (a heading-like line, at most `headingMaxChars`) that starts with Chapter, Part, Book, Canto,
 *   Section, अध्याय, परिच्छेद, भाग, অধ্যায়, পরিচ্ছেদ, খণ্ড or পর্ব and a number ("Chapter 3", "BOOK I BALA KANDA",
 *   "অধ্যায় ৩"); it is listed as written;
 * - "CHAPTER" in capitals and a number followed by a capital letter, anywhere in a block. A contents page's
 *   "CHAPTER 30. Vishwamitra…" (a full stop after the number) is not a chapter start, nor is "Chapter 3 shows that…"
 *   in a long paragraph.
 *
 * Numbers may be digits of any script, Roman numerals, or a scan's misreadings of digits ("I2", "4I", "I§"). A line
 * repeated on every page (a running header that names the chapter) is listed once.
 */
internal object ChapterMarkers {
    private const val NUMBER = """[\p{Nd}IVXLCl§]{1,6}"""
    private val ROMAN = Regex("[IVXLC]+")
    private val AT_START = Regex(
        """^(chapter|part|book|canto|section|अध्याय|परिच्छेद|भाग|অধ্যায়|পরিচ্ছেদ|খণ্ড|পর্ব)\s*[-–—:.]?\s*($NUMBER)""" +
            """(?=$|[\s.:,;–—-])""",
        RegexOption.IGNORE_CASE,
    )

    // Starts with the word itself, so the regex engine skips ahead to each "CHAPTER" (a scanned book is megabytes of
    // text); that no letter or digit comes right before it is checked after ([standsAlone]).
    private val IN_TEXT = Regex("""(CHAPTER)\s+($NUMBER)\s+(?=\p{Lu})""")
    private const val LEAD_WORD = 1
    private const val NUMBER_GROUP = 2
    private val TRAILING = Regex("""[\s,;:–—\-“”"‘’'*]+$""")

    fun find(document: TextDocument, config: TextBookConfig): List<ContentsEntry> {
        val all = document.chapters.flatMapIndexed { chapter, content ->
            content.blocks.flatMapIndexed { block, text ->
                markers(text.text, config).map { it to ContentsEntry(it.title, chapter, block, it.at) }
            }
        }
        // A running header repeats its chapter's line on every page: keep the first of a run.
        return all.filterIndexed { i, (marker, _) -> i == 0 || all[i - 1].first.key != marker.key }.map { it.second }
    }

    private class Marker(val title: String, val at: Int, val key: String)

    private fun markers(text: String, config: TextBookConfig): List<Marker> {
        val found = IN_TEXT.findAll(text).filter { isNumber(it.groupValues[NUMBER_GROUP]) && standsAlone(text, it) }
            .map { Marker(titleOf(it, text, config), it.range.first, keyOf(it)) }
            .toMutableList()
        if (text.length <= config.headingMaxChars && found.none { it.at == 0 }) {
            AT_START.find(text)?.takeIf { isNumber(it.groupValues[NUMBER_GROUP]) }?.let { match ->
                found.add(0, Marker(text, 0, keyOf(match)))
            }
        }
        return found
    }

    private fun standsAlone(text: String, match: MatchResult) =
        match.range.first == 0 || !text[match.range.first - 1].isLetterOrDigit()

    /** Digits (a misread "I2" counts), or a Roman numeral. */
    private fun isNumber(token: String) = token.any { it.isDigit() } || ROMAN.matches(token)

    private fun keyOf(match: MatchResult) =
        match.groupValues[LEAD_WORD].lowercase() + " " + match.groupValues[NUMBER_GROUP]

    /**
     * "Chapter 38: The Ascent of Mount Suvela" when the words after the number stop at a word in capitals (a chapter's
     * first word is often printed so: "HAVING resolved…") within `chapterTitleMaxWords`, or the text ends there
     * ("CHAPTER 3 The Storm" as a line of its own); else "Chapter 38".
     */
    private fun titleOf(match: MatchResult, text: String, config: TextBookConfig): String {
        val number = "Chapter " + match.groupValues[NUMBER_GROUP]
        val words = text.substring(match.range.last + 1).split(' ').filter { it.isNotEmpty() }
            .take(config.chapterTitleMaxWords + 1)
        val stop = words.indexOfFirst(::isCapitalsWord).takeIf { it >= 0 }
            ?: words.size.takeIf { it <= config.chapterTitleMaxWords }
            ?: 0
        if (stop == 0) return number
        // "…hermitage of Kama A LITTLE before dawn": the opening "A" goes with the capitals word after it.
        val opensWithLoneCapital = stop < words.size && stop > 1 && words[stop - 1].isLoneCapital()
        val title = words.take(if (opensWithLoneCapital) stop - 1 else stop).joinToString(" ").replace(TRAILING, "")
        return if (title.isEmpty()) number else "$number: $title"
    }

    /**
     * A chapter's opening word printed in capitals or small capitals: "HAVING", "SUMANTRA", and as a scan misreads
     * small capitals, "HavinG", "KiNG", "THe" (a capital first and at least one more). "I", "Rama's" and "Shri" are
     * not.
     */
    private fun isCapitalsWord(word: String): Boolean {
        val letters = word.filter { it.isLetter() }
        return letters.length >= 2 && letters.first().isUpperCase() && letters.drop(1).any { it.isUpperCase() }
    }

    /** "A", or "“O" (a speech opening with "O King"): one capital letter, quote marks aside. */
    private fun String.isLoneCapital() = filter { it.isLetter() }.let { it.length == 1 && it.first().isUpperCase() }
}
