// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/OcrInUseTest.kt
// Purpose: The reader knows when its book really runs OCR, so a slow text page never claims to be a scan.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.ocr.OcrMargins
import io.github.skm0078.audora.pdf.ocr.OcrText
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.pdf.quality.PageRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrInUseTest {
    private val pages = PageRenderer { page, _, _ -> error("page $page is never drawn in this test") }

    @Test
    fun `recognising a page is marked for exactly as long as it runs`() {
        val inUse = OcrInUse()
        val seen = mutableListOf<Boolean>()
        val ocr = inUse.tracking(factory { seen += inUse.recognizing.value }).create(pages)

        assertFalse(inUse.recognizing.value)
        ocr.recognize(3, setOf(Lang.BN))
        ocr.detectLangs(4)
        ocr.margins(5, setOf(Lang.BN), quarterTurns = 0, share = 0.1f)

        assertEquals(listOf(true, true, true), seen)
        assertFalse(inUse.recognizing.value)
    }

    @Test
    fun `a failing call still ends the mark`() {
        val inUse = OcrInUse()
        val ocr = inUse.tracking(factory { throw InterruptedException("the reader turned the page") }).create(pages)

        runCatching { ocr.recognize(0, setOf(Lang.EN)) }

        assertFalse(inUse.recognizing.value)
    }

    @Test
    fun `overlapping calls keep the mark until the last one ends`() {
        val inUse = OcrInUse()
        inUse.during {
            inUse.during { assertTrue(inUse.recognizing.value) }
            assertTrue("the outer call is still recognising", inUse.recognizing.value)
        }
        assertFalse(inUse.recognizing.value)
    }

    @Test
    fun `cache-only reads are never marked`() {
        val inUse = OcrInUse()
        val seen = mutableListOf<Boolean>()
        val ocr = inUse.tracking(factory { seen += inUse.recognizing.value }).create(pages)
        val cached = requireNotNull(ocr.cachedOnly())

        cached.recognize(1, setOf(Lang.HI))

        assertEquals(listOf(false), seen)
    }

    @Test
    fun `time spent in OCR is added up for the slow-page log`() {
        var now = 0L
        val inUse = OcrInUse(clock = { now })
        inUse.during { now += 2_500_000_000L }
        inUse.during { now += 500_000_000L }
        assertEquals(3_000L, inUse.spentMs)
    }

    /** An OCR whose every call runs [onCall]; its cache-only copy runs it too, unmarked by the wrapper. */
    private fun factory(onCall: () -> Unit): PageOcrFactory = PageOcrFactory { _ -> RecordingOcr(onCall) }

    private class RecordingOcr(private val onCall: () -> Unit) : PageOcr {
        override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> {
            onCall()
            return Outcome.Failure(OcrError.Failed("test"))
        }

        override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> {
            onCall()
            return Outcome.Success(emptySet())
        }

        override fun margins(
            pageIndex: Int,
            langs: Set<Lang>,
            quarterTurns: Int,
            share: Float,
        ): Outcome<OcrMargins, OcrError> {
            onCall()
            return Outcome.Failure(OcrError.Failed("test"))
        }

        override fun cachedOnly(): PageOcr = this
    }
}
