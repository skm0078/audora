// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/ChapterMarkersTest.kt
// Purpose: Chapter lines in a scanned book's text are found and titled; contents pages, prose and headers are not.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.config.TextBookConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterMarkersTest {
    private val config = TextBookConfig()

    private fun found(vararg pages: String) = ChapterMarkers.find(
        TextDocument(null, null, pages.map { TextChapter(null, listOf(TextBlock(BlockKind.BODY, it))) }),
        config,
    )

    @Test
    fun `a chapter line in the middle of a scanned page is found and titled`() {
        val page = "the people rejoiced. 91 CHAPTER 38 The Ascent of Mount Suvela HAVING resolved to stay, Rama spoke."
        val rows = found("Notice text that names no chapter.", page)
        assertEquals(
            listOf(ContentsEntry("Chapter 38: The Ascent of Mount Suvela", 1, 0, page.indexOf("CHAPTER"))),
            rows,
        )
    }

    @Test
    fun `a title ends at the chapter's first word even when the scan misread its small capitals`() {
        val rows = found(
            "end. CHAPTER 3 The deeds of Rama HavinG heard the story, the sage spoke.",
            "end. CHAPTER 23 They reach the hermitage of Kama A LITTLE before dawn they rose.",
            "end. CHAPTER 46 Diti undergoes austerities for a son “O BROTHER, said she, hear me.",
        )
        assertEquals(
            listOf(
                "Chapter 3: The deeds of Rama",
                "Chapter 23: They reach the hermitage of Kama",
                "Chapter 46: Diti undergoes austerities for a son",
            ),
            rows.map { it.title },
        )
    }

    @Test
    fun `a title with no capitals word after it within the limit is just the number`() {
        val rows = found(
            "CHAPTER 4 Shri Rama’s sons chant the poem Wauie Shri Rama was still on the throne " +
                "and ruled the land well for many years.",
        )
        assertEquals(listOf("Chapter 4"), rows.map { it.title })
    }

    @Test
    fun `a scan's misread digits and Roman numerals count, a contents page does not`() {
        val rows = found(
            "CONTENTS Book I BALA KANDA CHAPTER I. Shri Narada relates to Valmiki - 2. Valmiki creates the form",
            "CONTENTS CHAPTER 55. 56. 57. Vishwamitra performs another thousand years' austerities",
            "CHAPTER I2 Rishyasringa agrees to assist TIME passed and the king waited.",
            "CHAPTER IV The Storm THE wind rose over the hills that night.",
        )
        assertEquals(
            listOf("Chapter I2: Rishyasringa agrees to assist", "Chapter IV: The Storm"),
            rows.map {
                it.title
            },
        )
    }

    @Test
    fun `heading-like lines count in every language, prose that names a chapter does not`() {
        val rows = found(
            "BOOK I BALA KANDA",
            "अध्याय ५",
            "অধ্যায় ৩: বাড়ি ফেরা",
            "Chapter 3 shows that the method works for every case we tried, and Chapter 4 extends it to all others.",
            "Part of it was lost in the fire, as the old men of the village still remember to this day.",
            "भाग जाओ!",
        )
        assertEquals(listOf("BOOK I BALA KANDA", "अध्याय ५", "অধ্যায় ৩: বাড়ি ফেরা"), rows.map { it.title })
    }

    @Test
    fun `a running header that repeats the chapter on every page is listed once`() {
        val rows = found(
            "CHAPTER 3 The Storm THE wind rose.",
            "CHAPTER 3 The Storm IT rained all night.",
            "CHAPTER 4 The Calm MORNING came.",
        )
        assertEquals(listOf(0, 2), rows.map { it.chapter })
    }

    @Test
    fun `chapters found in the text are used only when the book's own contents are too short`() {
        val pages = (1..3).map { n ->
            TextChapter(null, listOf(TextBlock(BlockKind.BODY, "x CHAPTER $n Title THE end.")))
        }
        val sparse = TextDocument(null, null, pages, contents = listOf(ContentsEntry("Notice", 0)))
        assertEquals(
            listOf("Notice", "Chapter 1: Title", "Chapter 2: Title", "Chapter 3: Title"),
            BookContents.of(sparse, config).map { it.title },
        )
        val own = (0..2).map { ContentsEntry("Own $it", it) }
        assertEquals(own, BookContents.of(sparse.copy(contents = own), config))
        val twoFound = TextDocument(null, null, pages.take(2), contents = emptyList())
        assertEquals(emptyList<ContentsEntry>(), BookContents.of(twoFound, config))
    }
}
