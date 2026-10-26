// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrLanguagesTest.kt
// Purpose: A book's OCR languages follow most of its pages: one English page in a Hindi book does not turn the Hindi
//          scans into English OCR; a page's own trusted letters decide for that page; detection runs once.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.RouteReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrLanguagesTest {
    private val languages = OcrLanguages(QualityConfig())
    private var detections = 0
    private val ocr = object : PageOcr {
        override fun recognize(pageIndex: Int, langs: Set<Lang>) = error("not used")

        override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> {
            detections++
            return Outcome.Success(setOf(Lang.HI))
        }
    }

    private fun scannedPage(): Set<Lang>? =
        (languages.forPage(0, RouteReason.TOO_LITTLE_TEXT, emptySet(), ocr) as Outcome.Success).value

    @Test
    fun `one English page in a Hindi book leaves the scans in Hindi`() {
        repeat(5) { languages.learn(setOf(Lang.HI)) }
        languages.learn(setOf(Lang.EN))

        assertEquals(setOf(Lang.HI), scannedPage())
        assertEquals(0, detections)
    }

    @Test
    fun `a book half in each language keeps both, the most seen first`() {
        repeat(3) { languages.learn(setOf(Lang.EN)) }
        repeat(4) { languages.learn(setOf(Lang.HI)) }

        assertEquals(listOf(Lang.HI, Lang.EN), scannedPage()?.toList())
    }

    @Test
    fun `a page without letters changes nothing`() {
        languages.learn(setOf(Lang.BN))
        languages.learn(emptySet())

        assertEquals(setOf(Lang.BN), languages.known)
    }

    @Test
    fun `a book that showed nothing detects once, then remembers`() {
        assertNull(languages.known)

        assertEquals(setOf(Lang.HI), scannedPage())
        assertEquals(setOf(Lang.HI), scannedPage())
        assertEquals(1, detections)
    }

    @Test
    fun `a page whose own letters are trusted reads in them`() {
        repeat(3) { languages.learn(setOf(Lang.HI)) }

        val page = languages.forPage(0, RouteReason.UNICODE_MAP_ERRORS, setOf(Lang.BN), ocr)

        assertEquals(Outcome.Success(setOf(Lang.BN)), page)
    }
}
