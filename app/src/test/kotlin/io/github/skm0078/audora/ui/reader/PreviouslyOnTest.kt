// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/PreviouslyOnTest.kt
// Purpose: "Previously, N days ago": the two sentences before the place, only after days away, and the reader shows
//          it instead of the one-line toast; "Hear them again" reads from the first of them, "Go on" from the place.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ResumePoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviouslyOnTest {
    private val book = BookId("b")

    private fun unit(index: Int, charStart: Int, text: String) =
        ReadingUnit(3, index, charStart, charStart + text.length, text, Lang.EN, 0, null)

    private val page = listOf(
        unit(0, 0, "Call me Ishmael."),
        unit(1, 17, "Some years ago, never mind how long."),
        unit(2, 54, ""),
        unit(3, 54, "I thought I would sail about a little."),
        unit(4, 93, "It is a way I have."),
    )
    private val at = ReadingPosition(book, 3, 93)

    @Test
    fun `after days away, the two sentences before the place, read from the first`() {
        val recap = previouslyOn(at, page, savedAtMillis = 0, nowMillis = 9 * DAY)

        assertEquals(
            PreviouslyOn(
                9,
                listOf("Some years ago, never mind how long.", "I thought I would sail about a little."),
                ReadingPosition(book, 3, 17),
            ),
            recap,
        )
    }

    @Test
    fun `a day or two away, or a clock gone back, has no recap`() {
        assertNull(previouslyOn(at, page, savedAtMillis = 0, nowMillis = 3 * DAY - 1))
        assertNull(previouslyOn(at, page, savedAtMillis = 9 * DAY, nowMillis = 0))
    }

    @Test
    fun `the page's first sentence has nothing before it to recall`() {
        assertNull(previouslyOn(ReadingPosition(book, 3, 0), page, savedAtMillis = 0, nowMillis = 9 * DAY))
    }

    @Test
    fun `a long sentence is cut to its first words`() {
        val recap = previouslyOn(at, page, savedAtMillis = 0, nowMillis = 9 * DAY, maxWords = 3)

        assertEquals(listOf("Some years ago,…", "I thought I…"), recap?.lines)
    }

    private val port = FakePlaybackPort()

    /** Left on page 7 at its third sentence, [savedAt] ms after the epoch; the test clock says nine days on. */
    private fun TestScope.model(savedAt: Long) = ReaderModel(
        BOOK,
        FakeBooks(),
        FakeOpener(FakeDocument(pageCount = 10)),
        port,
        FakeSpeeds(),
        ReaderConfig(),
        backgroundScope,
        resume = object : ReaderResume {
            override suspend fun locate(id: BookId, pageCount: Int, unitsOf: suspend (Int) -> List<ReadingUnit>?) =
                requireNotNull(unitsOf(7))[2].let { ResumePoint(it.page, it.charStart, AnchorMatch.EXACT) }

            override suspend fun savedAtMillis(id: BookId) = savedAt
        },
        now = { 9 * DAY },
    ).also { it.start() }

    @Test
    fun `a book opened after days away shows the recap instead of the toast`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(savedAt = 0)

        assertNotNull(model.state.value.previouslyOn)
        assertNull(model.state.value.resumedAt)
    }

    @Test
    fun `a book opened the next day only says where it resumed`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(savedAt = 8 * DAY)

        assertNull(model.state.value.previouslyOn)
        assertNotNull(model.state.value.resumedAt)
    }

    @Test
    fun `hear them again reads from the recap's first sentence`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(savedAt = 0)
        port.connect()

        model.hearRecapAgain()

        assertEquals(listOf(ReadingPosition(BOOK, 7, 0) to null), port.starts)
        assertNull(model.state.value.previouslyOn)
    }

    @Test
    fun `go on reads on from the place`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(savedAt = 0)
        port.connect()

        model.goOnFromRecap()

        assertEquals(listOf(ReadingPosition(BOOK, 7, 40) to null), port.starts)
        assertNull(model.state.value.previouslyOn)
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
