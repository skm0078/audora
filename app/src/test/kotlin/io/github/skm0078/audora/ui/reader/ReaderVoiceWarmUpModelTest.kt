// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderVoiceWarmUpModelTest.kt
// Purpose: The reader asks the player to load the book's voice a moment after it opens, only while nothing plays
//          (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.Lang
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderVoiceWarmUpModelTest {
    private val port = FakePlaybackPort()
    private val config = ReaderConfig()

    private fun TestScope.open() = ReaderModel(
        BOOK,
        FakeBooks(),
        FakeOpener(FakeDocument(pageCount = 3)),
        port,
        FakeSpeeds(),
        config,
        backgroundScope,
    ).also { it.start() }

    private fun TestScope.waitForWarmUp() {
        advanceTimeBy(config.voiceWarmUpDelayMs)
        runCurrent()
    }

    @Test
    fun `an open book asks for its page's voice once the wait is over`() = runTest(UnconfinedTestDispatcher()) {
        port.connect()
        open()
        assertEquals(emptyList<Any>(), port.warmed)

        waitForWarmUp()
        assertEquals(listOf(BOOK to Lang.EN), port.warmed)
    }

    @Test
    fun `nothing is asked while something plays`() = runTest(UnconfinedTestDispatcher()) {
        port.connect()
        port.readingAt(ReadingPosition(OTHER_BOOK, page = 0, charStart = 0), playing = true)
        open()
        waitForWarmUp()

        assertEquals(emptyList<Any>(), port.warmed)
    }

    @Test
    fun `a player that connects after the book opened still gets asked`() = runTest(UnconfinedTestDispatcher()) {
        open()
        waitForWarmUp()
        assertEquals(emptyList<Any>(), port.warmed)

        port.connect()
        waitForWarmUp()
        assertEquals(listOf(BOOK to Lang.EN), port.warmed)
    }

    @Test
    fun `coming back to the book after the player went away asks again`() = runTest(UnconfinedTestDispatcher()) {
        port.connect()
        open()
        waitForWarmUp()
        port.disconnect()
        port.connect()
        waitForWarmUp()

        assertEquals(listOf(BOOK to Lang.EN, BOOK to Lang.EN), port.warmed)
    }

    @Test
    fun `a book closed before the wait ends asks nothing`() = runTest(UnconfinedTestDispatcher()) {
        port.connect()
        open().close()
        waitForWarmUp()

        assertEquals(emptyList<Any>(), port.warmed)
    }
}
