// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/SkipFrontMatterTest.kt
// Purpose: An unread book opens past its front matter and says so; "Read from the start", the switch and a saved
//          place all win over the skip (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ResumePoint
import io.github.skm0078.audora.pdf.document.OutlineEntry
import io.github.skm0078.audora.pdf.start.FrontMatterKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkipFrontMatterTest {
    private val port = FakePlaybackPort()
    private val store = MemoryStartStore()
    private val outline = listOf(
        OutlineEntry("Cover", 0),
        OutlineEntry("Contents", 1),
        OutlineEntry("Preface", 2),
        OutlineEntry("Chapter 1", 4),
        OutlineEntry("Chapter 2", 7),
    )
    private val document = FakeDocument(pageCount = 40, outline = outline)

    private fun TestScope.model(resume: ReaderResume = ReaderResume.None) = ReaderModel(
        BOOK,
        FakeBooks(),
        FakeOpener(document),
        port,
        FakeSpeeds(),
        ReaderConfig(),
        backgroundScope,
        resume,
        bookStart = FrontMatterBookStart(store, io = UnconfinedTestDispatcher(testScheduler), nowMs = { 0L }),
    ).also { it.start() }

    @Test
    fun `an unread book opens at its first chapter, says what it skipped and plays from there`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            val state = model.state.value

            assertEquals(4, state.page)
            val skipped = setOf(FrontMatterKind.TITLE_PAGES, FrontMatterKind.CONTENTS, FrontMatterKind.PREFACE)
            assertEquals(SkippedFrontMatter(4, "Chapter 1", skipped), state.skippedFrontMatter)
            port.connect()
            model.playOrPause()
            assertEquals(ReadingPosition(BOOK, 4, 0), port.starts.single().first)
        }

    @Test
    fun `read from the start goes to page 1 and the book keeps opening there`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.readFromStart()

        assertEquals(0, model.state.value.page)
        assertNull(model.state.value.skippedFrontMatter)
        assertEquals(0, store.saved(BOOK)?.page)
        assertEquals(0, model().state.value.page)
    }

    @Test
    fun `OK keeps the page and clears the notice`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.frontMatterNoticeShown()

        assertEquals(4, model.state.value.page)
        assertNull(model.state.value.skippedFrontMatter)
    }

    @Test
    fun `a book is searched once and its start kept`() = runTest(UnconfinedTestDispatcher()) {
        model()
        model()
        assertEquals(1, store.saves)
        assertEquals(4, model().state.value.page)
    }

    @Test
    fun `with the switch off every book opens on page 1`() = runTest(UnconfinedTestDispatcher()) {
        store.on = false
        val model = model()

        assertEquals(0, model.state.value.page)
        assertNull(model.state.value.skippedFrontMatter)
        assertEquals(0, store.saves)
    }

    @Test
    fun `a book already being read opens where it was left, not at its first chapter`() =
        runTest(UnconfinedTestDispatcher()) {
            val savedOnPage7 = ReaderResume { _, _, _ -> ResumePoint(7, 0, AnchorMatch.EXACT) }
            val model = model(savedOnPage7)

            assertEquals(7, model.state.value.page)
            assertNull(model.state.value.skippedFrontMatter)
            assertEquals(0, store.saves)
        }

    @Test
    fun `the opening line comes from the page the book opened on`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        assertEquals(unitsOf(4).first(), model.state.value.openingLine)
    }

    @Test
    fun `the notice comes after a page problem and before everything else`() {
        assertEquals(ReaderNotice.FRONT_MATTER, readerNotice(null, true, true, true, frontMatterSkipped = true))
        assertEquals(
            ReaderNotice.PAGE_PROBLEM,
            readerNotice(PageOrigin.OCR_FAILED, false, false, false, frontMatterSkipped = true),
        )
    }

    @Test
    fun `a kept start survives being written and read back`() {
        val start = SkippedFrontMatter(14, "অধ্যায় ১ | শুরু", setOf(FrontMatterKind.CONTENTS, FrontMatterKind.PREFACE))
        assertEquals(start, BookStartPreferences.decode(BookStartPreferences.encode(start)))
        assertEquals(
            SkippedFrontMatter(0, null, emptySet()),
            BookStartPreferences.decode(BookStartPreferences.encode(SkippedFrontMatter(0, null, emptySet()))),
        )
        assertNull(BookStartPreferences.decode("garbage"))
        assertNull(BookStartPreferences.decode("-3||"))
        assertEquals(setOf(FrontMatterKind.CONTENTS), BookStartPreferences.decode("5|NOT_A_KIND,CONTENTS|")?.skipped)
    }

    private class MemoryStartStore : BookStartStore {
        var on = true
        var saves = 0
        private val starts = HashMap<BookId, SkippedFrontMatter>()

        override fun skipFrontMatter() = on

        override fun setSkipFrontMatter(on: Boolean) {
            this.on = on
        }

        override fun saved(id: BookId) = starts[id]

        override fun save(id: BookId, start: SkippedFrontMatter) {
            saves++
            starts[id] = start
        }
    }
}
