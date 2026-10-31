// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/start/FrontMatterWordsTest.kt
// Purpose: Front-matter and chapter titles in English, Hindi and Bengali are told apart from a book's own chapters.

package io.github.skm0078.audora.pdf.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontMatterWordsTest {
    @Test
    fun `front matter titles name their kind in each language`() {
        val cases = mapOf(
            "Contents" to FrontMatterKind.CONTENTS,
            "TABLE OF CONTENTS" to FrontMatterKind.CONTENTS,
            "Contents at a glance" to FrontMatterKind.CONTENTS,
            "विषय सूची" to FrontMatterKind.CONTENTS,
            "अनुक्रमणिका" to FrontMatterKind.CONTENTS,
            "সূচিপত্র" to FrontMatterKind.CONTENTS,
            "Copyright" to FrontMatterKind.COPYRIGHT,
            "Copyright © 2021" to FrontMatterKind.COPYRIGHT,
            "Preface to the Second Edition" to FrontMatterKind.PREFACE,
            "Foreword." to FrontMatterKind.PREFACE,
            "Author’s Note" to FrontMatterKind.PREFACE,
            "प्रस्तावना" to FrontMatterKind.PREFACE,
            "भूमिका :" to FrontMatterKind.PREFACE,
            "ভূমিকা" to FrontMatterKind.PREFACE,
            "লেখকের কথা" to FrontMatterKind.PREFACE,
            "Acknowledgments" to FrontMatterKind.THANKS,
            "कृतज्ञता" to FrontMatterKind.THANKS,
            "Dedication" to FrontMatterKind.DEDICATION,
            "উৎসর্গ" to FrontMatterKind.DEDICATION,
            "समर्पण" to FrontMatterKind.DEDICATION,
            "Cover" to FrontMatterKind.TITLE_PAGES,
            "Title Page" to FrontMatterKind.TITLE_PAGES,
            "About the Author" to FrontMatterKind.TITLE_PAGES,
            "1. Preface" to FrontMatterKind.PREFACE,
        )
        cases.forEach { (title, kind) -> assertEquals(title, kind, FrontMatterWords.kindOf(title)) }
    }

    @Test
    fun `chapters, introductions and story names are not front matter`() {
        listOf(
            "Introduction", "Prologue", "Chapter 1", "The Boy Who Lived", "Indexing large tables", "Covering fire",
            "Contentment", "অধ্যায় ১", "पहला अध्याय", "Titles and names", "",
        ).forEach { assertEquals(it, null, FrontMatterWords.kindOf(it)) }
    }

    @Test
    fun `chapter headings are found in each language`() {
        listOf(
            "Chapter 1", "CHAPTER ONE", "Chapter IV: The Storm", "Part One", "1", "IV", "1.", "Prologue",
            "अध्याय 1", "प्रथम अध्याय", "पहला अध्याय", "परिच्छेद २", "अध्याय १", "অধ্যায় ১", "প্রথম পরিচ্ছেদ",
            "পর্ব ২", "১", "এক",
        ).forEach { assertTrue(it, FrontMatterWords.isChapterHeading(it)) }
    }

    @Test
    fun `ordinary lines are not chapter headings`() {
        listOf(
            "It was a bright cold day in April.",
            "The Boy Who Lived",
            "Partly cloudy",
            "Preface",
            "",
            "1 2 3 4 5 6",
            "Chapters of my life are many and long",
        ).forEach { assertFalse(it, FrontMatterWords.isChapterHeading(it)) }
    }

    @Test
    fun `bengali ya with nukta matches whichever way it was typed`() {
        val composed = "অধ্যায় ১"
        val decomposed = "অধ্যায় ১"
        assertTrue(FrontMatterWords.isChapterHeading(composed))
        assertTrue(FrontMatterWords.isChapterHeading(decomposed))
    }
}
