// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderModelTest.kt
// Purpose: Reader rules: opening, passwords, page picking, start from page N / a sentence, following, speed.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.FocusSound
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.speech.kws.VoiceCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderModelTest {
    private val port = FakePlaybackPort()
    private val document = FakeDocument(pageCount = 10, failingPages = setOf(9))
    private val books = FakeBooks()
    private val speeds = FakeSpeeds()

    private fun TestScope.model(
        opener: ReaderDocumentOpener = FakeOpener(document),
        scope: CoroutineScope = backgroundScope,
    ) = ReaderModel(BOOK, books, opener, port, speeds, ReaderConfig(), scope).also { it.start() }

    @Test
    fun `opening shows the first page and records the open`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        val state = model.state.value
        assertEquals("Moby Dick", state.title)
        assertEquals(DocumentState.Open(10), state.document)
        assertEquals(0, state.page)
        assertEquals(PageContent.Ready(unitsOf(0)), state.content)
        assertEquals(listOf(BOOK to 10), books.opened)
    }

    @Test
    fun `a book removed from the library says so`() = runTest(UnconfinedTestDispatcher()) {
        val model =
            ReaderModel(BOOK, FakeBooks(book = null), FakeOpener(), port, speeds, ReaderConfig(), backgroundScope)
        model.start()
        assertEquals(DocumentState.NotInLibrary, model.state.value.document)
    }

    @Test
    fun `password flow, and the password goes with the start request`() = runTest(UnconfinedTestDispatcher()) {
        val model = model(FakeOpener(document, password = "secret"))
        assertEquals(DocumentState.NeedsPassword(wrongPassword = false), model.state.value.document)

        model.submitPassword("wrong")
        assertEquals(DocumentState.NeedsPassword(wrongPassword = true), model.state.value.document)

        model.dismissPassword()
        assertEquals(DocumentState.Locked, model.state.value.document)
        model.askForPassword()
        assertEquals(DocumentState.NeedsPassword(wrongPassword = false), model.state.value.document)

        model.submitPassword("secret")
        assertEquals(DocumentState.Open(10), model.state.value.document)
        port.connect()
        model.playOrPause()
        assertEquals(listOf(ReadingPosition(BOOK, 0, 0) to "secret"), port.starts)
    }

    @Test
    fun `open failures map to what the screen shows`() = runTest(UnconfinedTestDispatcher()) {
        val cases = mapOf(
            PdfOpenError.Damaged("x") to ReaderFailure.DAMAGED,
            PdfOpenError.Unreadable("x") to ReaderFailure.UNREADABLE,
            PdfOpenError.EngineUnavailable("x") to ReaderFailure.ENGINE_UNAVAILABLE,
        )
        cases.forEach { (error, expected) ->
            val model = model(FakeOpener(failure = error))
            assertEquals(DocumentState.Failed(expected), model.state.value.document)
        }
    }

    @Test
    fun `page picker shows page N and clamps to the book`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.showPage(4)
        assertEquals(4, model.state.value.page)
        assertEquals(PageContent.Ready(unitsOf(4)), model.state.value.content)
        model.showPage(99)
        assertEquals(9, model.state.value.page)
        assertEquals(PageContent.Failed, model.state.value.content)
        model.showPage(-3)
        assertEquals(0, model.state.value.page)
        model.previousPage()
        assertEquals(0, model.state.value.page)
        model.nextPage()
        assertEquals(1, model.state.value.page)
    }

    @Test
    fun `start from page N reads from the top of the page shown`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        model.showPage(6)
        model.playOrPause()
        assertEquals(listOf(ReadingPosition(BOOK, 6, 0) to null), port.starts)
        assertTrue(model.state.value.following)
    }

    @Test
    fun `tapping a sentence reads from that sentence`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        model.readFrom(unitsOf(0)[2])
        assertEquals(listOf(ReadingPosition(BOOK, 0, 40) to null), port.starts)
        assertEquals("a tap names its unit", listOf<Int?>(2), port.startIndexes)
    }

    @Test
    fun `nothing starts before the player is connected`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.playOrPause()
        model.readFrom(unitsOf(0)[0])
        assertTrue(port.starts.isEmpty())
    }

    @Test
    fun `play pauses while reading and resumes on the reading page`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        port.readingAt(ReadingPosition(BOOK, 0, 20))
        assertTrue(model.state.value.isReading)
        model.playOrPause()
        assertEquals(listOf("pause"), port.calls)

        port.readingAt(ReadingPosition(BOOK, 0, 20), playing = false)
        model.playOrPause()
        assertEquals(listOf("pause", "play"), port.calls)
        assertTrue("resume, not restart", port.starts.isEmpty())
    }

    @Test
    fun `play after browsing elsewhere starts at the page shown`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        port.readingAt(ReadingPosition(BOOK, 0, 20), playing = false)
        model.showPage(5)
        model.playOrPause()
        assertEquals(listOf(ReadingPosition(BOOK, 5, 0) to null), port.starts)
    }

    @Test
    fun `the screen follows reading until the listener browses away`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        port.readingAt(ReadingPosition(BOOK, 2, 0))
        assertEquals(2, model.state.value.page)
        assertEquals(0, model.state.value.spokenUnitIndex)

        model.showPage(7)
        assertFalse(model.state.value.following)
        port.readingAt(ReadingPosition(BOOK, 3, 0))
        assertEquals("browsing is not interrupted", 7, model.state.value.page)
        assertTrue(model.state.value.readingElsewhere)
        assertNull(model.state.value.spokenUnitIndex)

        model.showReadingPosition()
        assertEquals(3, model.state.value.page)
        assertTrue(model.state.value.following)
        port.readingAt(ReadingPosition(BOOK, 4, 0))
        assertEquals(4, model.state.value.page)
    }

    @Test
    fun `browsing back to the reading page follows again`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        port.readingAt(ReadingPosition(BOOK, 2, 0))
        model.showPage(5)
        model.showPage(2)
        assertTrue(model.state.value.following)
    }

    @Test
    fun `another book in the player is not this book's position`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        port.readingAt(ReadingPosition(OTHER_BOOK, 6, 0))
        assertEquals(0, model.state.value.page)
        assertNull(model.state.value.readingPosition)
        assertFalse(model.state.value.isReading)
        model.playOrPause()
        assertEquals(listOf(ReadingPosition(BOOK, 0, 0) to null), port.starts)
    }

    @Test
    fun `opening a book that is already playing shows the reading page`() = runTest(UnconfinedTestDispatcher()) {
        port.connect()
        port.readingAt(ReadingPosition(BOOK, 4, 40))
        val model = model()
        assertEquals(4, model.state.value.page)
        assertEquals(2, model.state.value.spokenUnitIndex)
    }

    @Test
    fun `the saved speed is applied when the player connects`() = runTest(UnconfinedTestDispatcher()) {
        speeds.saved = 1.33f // between the slider's steps: snapped to 1.35
        model()
        port.connect(speed = 1f)
        assertEquals(listOf("speed 1.35"), port.calls)
    }

    @Test
    fun `the saved speed waits for the service's own style, so a default is never taken for it`() =
        runTest(UnconfinedTestDispatcher()) {
            speeds.saved = 1.5f
            model()
            port.connect(speed = 1f, styleKnown = false)
            assertTrue(port.calls.isEmpty())

            port.connect(speed = 1.5f)
            assertTrue("the service already reads at the saved speed", port.calls.isEmpty())
        }

    @Test
    fun `no speed call when the player already has it`() = runTest(UnconfinedTestDispatcher()) {
        speeds.saved = 1.5f
        model()
        port.connect(speed = 1.5f)
        assertTrue(port.calls.isEmpty())
    }

    @Test
    fun `changing speed sets it at once and saves it`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        model.setSpeed(2f)
        assertEquals(listOf("speed 2.0"), port.calls)
        assertEquals(2f, speeds.saved)
    }

    @Test
    fun `a voice command's speed change plays but is not saved, and pause acts only while reading`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            port.connect(speed = 1f)
            model.onVoiceCommand(VoiceCommand.FASTER, ReaderConfig())
            model.onVoiceCommand(VoiceCommand.PAUSE, ReaderConfig())
            assertEquals(listOf("speed 1.25"), port.calls)
            assertNull(speeds.saved)
        }

    @Test
    fun `speeds between the steps snap to the slider, and pace goes to the player`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            port.connect()
            model.setSpeed(1.33f)
            model.setPace(Pace.RELAXED)
            assertEquals(listOf("speed 1.35", "pace RELAXED"), port.calls)
            assertEquals(1.35f, speeds.saved)
        }

    @Test
    fun `controls pass through to the player`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        model.skipForward()
        model.skipBack()
        model.setSleepTimer(SleepTimerSetting.EndOfPage)
        model.setSleepTimer(SleepTimerSetting.AfterChapters(2))
        model.setSleepOptions(SleepOptions(bedtime = true))
        model.setFocusSound(FocusSoundOptions(FocusSound.RAIN, level = 0.3f))
        assertEquals(
            listOf(
                "next",
                "previous",
                "sleep EndOfPage",
                "sleep AfterChapters(count=2)",
                "sleep options ${SleepOptions(bedtime = true).encode()}",
                "focus sound sound=RAIN;level=0.3",
            ),
            port.calls,
        )
    }

    @Test
    fun `a paragraph skip with no paragraph that way says so once`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        model.skipParagraph(forward = true)
        assertNull(model.state.value.skipRefused)

        port.refuseParagraphs = true
        model.skipParagraph(forward = true)
        assertEquals(ParagraphSkipRefusal.NO_NEXT, model.state.value.skipRefused)
        model.skipRefusalShown()
        assertNull(model.state.value.skipRefused)
        model.skipParagraph(forward = false)
        assertEquals(ParagraphSkipRefusal.NO_PREVIOUS, model.state.value.skipRefused)
        assertEquals(listOf("paragraph forward=true", "paragraph forward=true", "paragraph forward=false"), port.calls)
    }

    @Test
    fun `close releases the PDF`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        model.close()
        assertTrue(document.closed)
    }

    @Test
    fun `a sentence is found by containment`() {
        val units = unitsOf(0)
        val state = ReaderState(
            bookId = BOOK,
            document = DocumentState.Open(10),
            content = PageContent.Ready(units),
            playback = PlaybackSnapshot(PlaybackSnapshot.Connection.CONNECTED, position = ReadingPosition(BOOK, 0, 23)),
        )
        assertEquals(1, state.spokenUnitIndex)
        assertNull(state.copy(playback = state.playback.copy(position = ReadingPosition(BOOK, 0, 17))).spokenUnitIndex)
    }

    @Test
    fun `a position at a continued table's first row is that row, not the empty summary before it`() {
        val units = continuedTableOf(0)
        val state = ReaderState(
            bookId = BOOK,
            document = DocumentState.Open(10),
            content = PageContent.Ready(units),
            playback = PlaybackSnapshot(
                PlaybackSnapshot.Connection.CONNECTED,
                position = ReadingPosition(BOOK, 0, units[2].charStart),
            ),
        )
        assertEquals(2, state.spokenUnitIndex)
    }
}
