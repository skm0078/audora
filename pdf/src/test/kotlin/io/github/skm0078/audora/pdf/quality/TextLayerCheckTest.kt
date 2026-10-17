// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/quality/TextLayerCheckTest.kt
// Purpose: The per-book OCR-sample cross-check with a fake OCR, the error-rate measure and the OCR language choice.

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.ocr.OcrText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextLayerCheckTest {
    private val config = QualityConfig(minSampleLetters = 10, recheckEveryPages = 3)
    private val layer = "मानव अधिकारों की सार्वभौम घोषणा"

    private fun quality(route: PageRoute = PageRoute.TEXT_LAYER, isIndic: Boolean = true, letters: Int = 500) =
        PageQuality(
            route = route,
            reason = if (route == PageRoute.TEXT_LAYER) RouteReason.TEXT_LAYER_OK else RouteReason.GARBAGE,
            signals = PageSignals(letters, if (isIndic) letters else 0, 0f, 0f, 0f, 0f, 0f),
            indicOrder = IndicOrderEvidence.NONE,
            isIndic = isIndic,
        )

    /** Answers each call with the next result, and counts calls. */
    private class FakeOcr(vararg results: Outcome<String, OcrError>) : PageOcr {
        private val queue = ArrayDeque(results.toList())
        var calls = 0

        override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> {
            calls++
            return (queue.removeFirstOrNull() ?: error("unexpected OCR call for page $pageIndex")).map(OcrText::plain)
        }
    }

    private fun TextLayerCheck.route(page: Int, q: PageQuality = quality()) = route(page, q, setOf(Lang.HI)) { layer }

    @Test
    fun `without OCR the text layer is read and marked unverified`() {
        val check = TextLayerCheck(config, ocr = null, logContext = "test")
        val routing = check.route(0)
        assertEquals(PageRouting(PageRoute.TEXT_LAYER, RouteReason.TEXT_LAYER_OK, TextLayerVerdict.UNVERIFIED), routing)
    }

    @Test
    fun `an agreeing sample trusts the book`() {
        val ocr = FakeOcr(Outcome.Success("मानव अधिकारों की सार्वभौम घोषणा।"))
        val check = TextLayerCheck(config, ocr, "test")
        assertEquals(PageRoute.TEXT_LAYER, check.route(0).route)
        assertEquals(PageRoute.TEXT_LAYER, check.route(1).route)
        assertEquals(TextLayerVerdict.TRUSTED, check.verdict)
        assertEquals(0f, check.lastSampleCer)
        assertEquals(1, ocr.calls)
    }

    @Test
    fun `a disagreeing sample sends every Indic page to OCR, other routes are kept`() {
        val check = TextLayerCheck(config, FakeOcr(Outcome.Success(layer)), "test")
        // A mis-mapped layer: the right length, several wrong letters.
        val first = check.route(0, quality(), setOf(Lang.HI)) { "मािव अमिकारों की सावभौम घोर्णा" }
        assertEquals(PageRouting(PageRoute.OCR, RouteReason.SAMPLE_MISMATCH, TextLayerVerdict.REJECTED), first)
        assertEquals(PageRoute.OCR, check.route(1).route)
        assertEquals(PageRoute.TEXT_LAYER, check.route(2, quality(isIndic = false)).route)
        assertEquals(PageRoute.LEGACY_FONT, check.route(3, quality(route = PageRoute.LEGACY_FONT)).route)
    }

    @Test
    fun `pages with too little text are not sampled and the layer text is not built`() {
        val check = TextLayerCheck(config, FakeOcr(), "test")
        val routing = check.route(0, quality(letters = 5), setOf(Lang.HI)) { error("not a sample") }
        assertEquals(TextLayerVerdict.UNCHECKED, routing.verdict)
        assertNull(check.lastSampleCer)
    }

    @Test
    fun `a trusted book is checked again every N Indic pages, and a failed re-check rejects it`() {
        val good = Outcome.Success(layer)
        val bad = Outcome.Success("कुछ और ही लिखा है यहाँ पर")
        val ocr = FakeOcr(good, bad)
        val check = TextLayerCheck(config, ocr, "test")
        check.route(0)
        check.route(1)
        check.route(2, quality(isIndic = false))
        check.route(2)
        assertEquals(1, ocr.calls)
        assertEquals(TextLayerVerdict.TRUSTED, check.verdict)
        assertEquals(PageRoute.OCR, check.route(3).route)
        assertEquals(2, ocr.calls)
        assertEquals(TextLayerVerdict.REJECTED, check.verdict)
        check.route(4)
        check.route(5)
        check.route(6)
        assertEquals("a rejected book is not sampled again", 2, ocr.calls)
    }

    @Test
    fun `a failed OCR leaves the book unverified and is tried again later`() {
        val ocr = FakeOcr(Outcome.Failure(OcrError.ModelMissing(Lang.HI)), Outcome.Success(layer))
        val check = TextLayerCheck(config, ocr, "test")
        assertEquals(PageRoute.TEXT_LAYER, check.route(0).route)
        assertEquals(TextLayerVerdict.UNVERIFIED, check.verdict)
        check.route(1)
        check.route(2)
        assertEquals(1, ocr.calls)
        check.route(3)
        assertEquals(2, ocr.calls)
        assertEquals(TextLayerVerdict.TRUSTED, check.verdict)
    }

    @Test
    fun `error rate ignores spacing, punctuation, joiners and normalization form`() {
        assertEquals(0f, CharacterErrorRate.of("मानव अधिकार।", "मानव\nअधि‍कार"))
        assertEquals(0f, CharacterErrorRate.of("কো", "কো"))
        assertEquals(0.1f, CharacterErrorRate.of("abcdefghij", "abcdefghiX"), 1e-6f)
        assertEquals(0f, CharacterErrorRate.of("", "  "))
        assertEquals(1f, CharacterErrorRate.of("", "x"))
    }

    @Test
    fun `OCR languages follow the page's scripts`() {
        val config = QualityConfig()
        assertEquals(setOf(Lang.HI), ocrLangs("मानव अधिकार 1948", config))
        assertEquals(setOf(Lang.BN), ocrLangs("মানবাধিকার", config))
        assertEquals(setOf(Lang.HI, Lang.EN), ocrLangs("मानव अधिकार human rights", config))
        assertEquals(setOf(Lang.HI), ocrLangs("मानव अधिकारों की सार्वभौम घोषणा UN", config))
    }
}
