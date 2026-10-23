// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderLineTest.kt
// Purpose: One sentence's extras: "This sounded wrong" reports (RI-8b) and greyed unreadable sentences (RI-9).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import io.github.skm0078.audora.playback.report.SoundedWrongReport
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
class ReaderLineTest {
    private val port = FakePlaybackPort()

    private val document = FakeDocument(pageCount = 3, unitsOnPage = ::scoredUnitsOf)

    private fun TestScope.model() =
        ReaderModel(BOOK, FakeBooks(), FakeOpener(document), port, FakeSpeeds(), ReaderConfig(), backgroundScope)
            .also { it.start() }

    private fun ReaderModel.units() = (state.value.content as PageContent.Ready).units

    @Test
    fun `a long-press reports the sentence with the picked reason, and cancel sends nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            port.connect()
            val unit = model.units()[2]
            model.askReport(unit)
            assertEquals(unit, model.state.value.reporting)
            model.report(SoundedWrongReason.WRONG_WORD)
            assertNull(model.state.value.reporting)
            assertEquals(listOf(SoundedWrongReport.of(BOOK, unit, SoundedWrongReason.WRONG_WORD)), port.reports)

            model.askReport(unit)
            model.dismissReport()
            model.report(SoundedWrongReason.OTHER)
            assertEquals("nothing is reported once dismissed", 1, port.reports.size)
        }

    @Test
    fun `junk is greyed only while Skip unreadable text is on`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        val units = model.units()
        assertEquals(listOf(false, true, false), units.map(model.state.value::isUnreadable))
        port.state.value = port.state.value.copy(readingModes = ReadingModes(skipUnreadable = false))
        assertFalse(units.any(model.state.value::isUnreadable))
    }

    @Test
    fun `tapping a greyed sentence explains first, and reading it anyway turns the skipping off`() =
        runTest(UnconfinedTestDispatcher()) {
            val model = model()
            port.connect()
            val junk = model.units()[1]
            model.readFrom(junk)
            assertEquals(junk, model.state.value.unreadableTapped)
            assertTrue("nothing starts before the listener chooses", port.starts.isEmpty())

            model.dismissUnreadable()
            assertNull(model.state.value.unreadableTapped)
            assertTrue(port.starts.isEmpty())

            model.readFrom(junk)
            model.readUnreadable()
            assertNull(model.state.value.unreadableTapped)
            assertEquals(listOf("reading modes ${ReadingModes(skipUnreadable = false).encode()}"), port.calls)
            assertEquals(listOf(ReadingPosition(BOOK, 0, junk.charStart) to null), port.starts)
        }

    @Test
    fun `a clean sentence reads at once`() = runTest(UnconfinedTestDispatcher()) {
        val model = model()
        port.connect()
        val clean = model.units()[2]
        model.readFrom(clean)
        assertNull(model.state.value.unreadableTapped)
        assertEquals(listOf(ReadingPosition(BOOK, 0, clean.charStart) to null), port.starts)
    }

    private companion object {
        const val JUNK = 0.1f
        const val CLEAN = 0.9f

        /** Sentence 1 of every page scores as junk (below SpeakabilityConfig.skipBelow), the others as clean. */
        fun scoredUnitsOf(page: Int) = unitsOf(page).map { it.copy(speakScore = if (it.index == 1) JUNK else CLEAN) }
    }
}
