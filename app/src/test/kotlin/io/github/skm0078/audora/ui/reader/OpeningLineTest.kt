// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/OpeningLineTest.kt
// Purpose: The opening line card: only on a book's first open, the first page's first sentence with words, and once.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ResumePoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpeningLineTest {
    private fun TestScope.model(
        lastOpenedAt: Long? = null,
        resume: ReaderResume = ReaderResume.None,
        firstPage: List<ReadingUnit> = unitsOf(0),
    ) = ReaderModel(
        BOOK,
        FakeBooks(book().copy(lastOpenedAtMillis = lastOpenedAt)),
        FakeOpener(FakeDocument(pageCount = 10, unitsOnPage = { if (it == 0) firstPage else unitsOf(it) })),
        FakePlaybackPort(),
        FakeSpeeds(),
        ReaderConfig(),
        backgroundScope,
        resume,
    ).also { it.start() }

    @Test
    fun `a book opened for the first time shows its first sentence`() = runTest(UnconfinedTestDispatcher()) {
        assertEquals("Page 0 sentence 0.", model().state.value.openingLine?.text)
    }

    @Test
    fun `a sentence without letters is passed over`() = runTest(UnconfinedTestDispatcher()) {
        val page = listOf(
            ReadingUnit(0, 0, 0, 3, "12.", Lang.EN, 0, null),
            ReadingUnit(0, 1, 4, 20, "Chapter One.", Lang.EN, 0, null),
        )

        assertEquals("Chapter One.", model(firstPage = page).state.value.openingLine?.text)
    }

    @Test
    fun `it shows once`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()

        model.openingLineShown()

        assertNull(model.state.value.openingLine)
    }

    @Test
    fun `a book opened before has no opening line`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(model(lastOpenedAt = 5).state.value.openingLine)
    }

    @Test
    fun `a book with a saved place has no opening line`() = runTest(UnconfinedTestDispatcher()) {
        val saved = ReaderResume { _, _, _ -> ResumePoint(0, 20, AnchorMatch.EXACT) }

        assertNull(model(resume = saved).state.value.openingLine)
    }

    @Test
    fun `turning away from the first page hides it`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()

        model.showPage(1)

        assertNull(model.state.value.openingLine)
    }
}
