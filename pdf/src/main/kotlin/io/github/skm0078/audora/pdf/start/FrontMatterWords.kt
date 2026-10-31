// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/start/FrontMatterWords.kt
// Purpose: The names front matter and chapters go by in English, Hindi and Bengali, for finding where a book's
//          reading starts (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.pdf.start

import java.text.Normalizer
import java.util.Locale

/** What a skipped page was, as the reader's notice names it ("Skipped 14 pages: contents, copyright, preface"). */
enum class FrontMatterKind {
    /** Cover, title and half-title pages, blank pages, epigraphs, "Also by", "About the author", maps. */
    TITLE_PAGES,
    COPYRIGHT,
    CONTENTS,
    DEDICATION,
    THANKS,

    /** Preface, foreword, the author's, translator's or publisher's note. */
    PREFACE,
}

/**
 * Titles are matched whole or as their first words ("Preface to the second edition", "Contents at a glance") after
 * [normalize]: lower case, NFC (so "অধ্যায়" matches whichever way its য় was typed), no numbering or end punctuation.
 * "Introduction" and "Prologue" are NOT front matter: in most books they are the first thing the author wants read.
 */
object FrontMatterWords {
    // Declared first: the word lists below are normalized with them as the object starts.
    private val LEADING_NUMBER = Regex("""^[\s0-9০-৯०-९ivxlc]{0,6}[.):\-–—]\s+""")
    private val EDGE_PUNCTUATION = Regex("""^[\p{Punct}\s“”‘’«»।॥]+|[\p{Punct}\s“”‘’«»।॥]+$""")
    private val SPACES = Regex("""\s+""")

    private val BY_KIND: Map<FrontMatterKind, List<String>> = mapOf(
        FrontMatterKind.CONTENTS to listOf(
            "contents", "table of contents", "index", "विषय सूची", "विषय-सूची", "विषयसूची",
            "अनुक्रमणिका", "अनुक्रम", "सूची", "সূচিপত্র", "সূচীপত্র", "সূচিপত্ৰ", "সূচি", "সূচী", "বিষয়সূচি",
            "বিষয়সূচী",
        ),
        FrontMatterKind.COPYRIGHT to listOf(
            "copyright", "copyright page", "imprint", "colophon", "publication data", "कॉपीराइट", "सर्वाधिकार",
            "কপিরাইট", "স্বত্ব",
        ),
        FrontMatterKind.DEDICATION to listOf("dedication", "dedicated to", "समर्पण", "উৎসর্গ", "উত্সর্গ"),
        FrontMatterKind.THANKS to listOf(
            "acknowledgements", "acknowledgments", "acknowledgement", "acknowledgment", "आभार", "कृतज्ञता",
            "धन्यवाद", "কৃতজ্ঞতা", "কৃতজ্ঞতা স্বীকার", "ঋণ স্বীকার", "ঋণস্বীকার",
        ),
        FrontMatterKind.PREFACE to listOf(
            "preface", "foreword", "forward", "author's note", "authors note", "a note from the author",
            "note from the author", "translator's note", "a note on the translation", "a note on the text",
            "editor's note", "publisher's note", "प्रस्तावना", "भूमिका", "प्राक्कथन", "आमुख", "दो शब्द", "पुरोवाक्",
            "लेखकीय", "अपनी बात", "मेरी बात", "संपादकीय", "प्रकाशकीय", "ভূমিকা", "মুখবন্ধ", "প্রাক্কথন",
            "নিবেদন", "লেখকের কথা", "লেখকের নিবেদন", "প্রকাশকের কথা", "প্রকাশকের নিবেদন", "সম্পাদকের কথা",
            "সম্পাদকীয়", "প্রসঙ্গত",
        ),
        FrontMatterKind.TITLE_PAGES to listOf(
            "front matter", "frontmatter", "prelims", "cover", "front cover", "title page", "half title",
            "half-title", "frontispiece", "epigraph",
            "also by", "other books by", "books by", "by the same author", "praise for", "about the author",
            "about the book", "map", "maps", "आवरण", "मुखपृष्ठ", "लेखक परिचय", "লেখক পরিচিতি", "প্রচ্ছদ",
        ),
    ).mapValues { (_, words) -> words.map(::normalize) }

    /** Words that name a chapter when a number follows ("Chapter 3", "অধ্যায় ১") or an ordinal comes first. */
    private val CHAPTER_WORDS: Set<String> = setOf(
        "chapter", "chap", "part", "canto", "lesson", "unit", "अध्याय", "परिच्छेद", "भाग", "प्रकरण", "खंड", "खण्ड",
        "पाठ", "सर्ग", "অধ্যায়", "পরিচ্ছেদ", "পর্ব", "খণ্ড", "খন্ড", "ভাগ", "পাঠ",
    ).mapTo(HashSet(), ::normalize)

    /** Openings that start the reading on their own, whatever follows ("Prologue: The night"). */
    private val OPENING_WORDS: Set<String> = setOf("prologue").mapTo(HashSet(), ::normalize)

    /** "First chapter" in the Hindi and Bengali way: the ordinal before the chapter word. */
    private val ORDINALS: Set<String> = setOf("first", "प्रथम", "पहला", "প্রথম").mapTo(HashSet(), ::normalize)

    /** A chapter's number, alone or after its word: "1", "IV", "১", "One", "एक", "প্রথম". */
    private val NUMBER_HEADING = Regex(
        "^([0-9০-৯०-९]{1,3}|[ivxlc]{1,7}|one|two|three|first|एक|पहला|प्रथम|এক|প্রথম)$",
    )

    /** The front matter [title] names, or null for anything else (a chapter, "Introduction", a story's name). */
    fun kindOf(title: String): FrontMatterKind? {
        val text = normalize(title)
        if (text.isEmpty()) return null
        return BY_KIND.entries.firstOrNull { (_, words) -> words.any { text.startsWithWord(it) } }?.key
    }

    /**
     * [heading] reads as a chapter's start: only a number, an opening such as "Prologue", or a chapter word with a
     * number after it or an ordinal before it. A bare "Part of the reason…" is not one.
     */
    fun isChapterHeading(heading: String): Boolean {
        val words = normalize(heading).split(' ').map { it.trimEnd('.', ':', ',') }.filter { it.isNotEmpty() }
        val first = words.firstOrNull()
        return when {
            first == null -> false

            words.size == 1 && NUMBER_HEADING.matches(first) -> true

            first in OPENING_WORDS -> true

            else -> words.take(CHAPTER_WORD_REACH).withIndex().any { (i, word) ->
                word in CHAPTER_WORDS &&
                    (words.getOrNull(i + 1)?.let(NUMBER_HEADING::matches) == true || words.getOrNull(i - 1) in ORDINALS)
            }
        }
    }

    /** Lower case, NFC, curly apostrophes made straight, numbering ("1. ", "IV - ") and edge punctuation gone. */
    fun normalize(text: String): String {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(SPACES, " ")
            .trim()
        return plain.replace(LEADING_NUMBER, "").replace(EDGE_PUNCTUATION, "")
    }

    private fun String.startsWithWord(word: String): Boolean =
        this == word || (startsWith(word) && this[word.length].let { !it.isLetterOrDigit() && !it.isMark() })

    private fun Char.isMark(): Boolean = Character.getType(this).let {
        it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
    }

    /** "प्रथम अध्याय", "Part One: Chapter 1": the chapter word may come third at the latest. */
    private const val CHAPTER_WORD_REACH = 3
}
