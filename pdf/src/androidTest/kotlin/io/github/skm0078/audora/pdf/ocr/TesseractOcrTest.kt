// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/ocr/TesseractOcrTest.kt
// Purpose: P3-05 done-when on a device: scanned English, Hindi and Bengali pages read by the real Tesseract + PDFium.

package io.github.skm0078.audora.pdf.ocr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PageSource
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.ocr.tesseract.TessdataSource
import io.github.skm0078.audora.pdf.ocr.tesseract.TesseractEngine
import io.github.skm0078.audora.pdf.pdfium.TestPdfs
import io.github.skm0078.audora.pdf.pdfium.characterErrorRate
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageRoute
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The scans are page 1 of the degraded 200 dpi UDHR scans (rotated, blurred, noisy, JPEG q45; SOURCES.md). Each book
 * is opened with no text layer and no hint of its language, so the language probe runs too. The models come from the
 * build (TessdataAssetsTask): the same pinned tessdata_best files the app downloads.
 */
@RunWith(AndroidJUnit4::class)
class TesseractOcrTest {
    @Test
    fun englishScanReadsIntelligibly() = readsScan("en", Lang.EN)

    @Test
    fun hindiScanReadsIntelligibly() = readsScan("hi", Lang.HI)

    @Test
    fun bengaliScanReadsIntelligibly() = readsScan("bn", Lang.BN)

    @Test
    fun aMissingModelFallsBackToTheTextLayerWithTheReason() {
        // Listed as installed but deleted underneath: the probe needs Hindi and cannot load it.
        val engine = TesseractEngine(AssetTessdata(root, Lang.entries.toSet(), available = setOf(Lang.EN, Lang.BN)))
        val book = openScan("hi", engine)
        val reading = requireNotNull(book.readPage(0, ReadingUnitBuilder()).getOrNull())
        book.close()
        assertEquals(PageRoute.OCR, reading.routing.route)
        assertEquals(PageSource.OcrUnavailable(OcrError.ModelMissing(Lang.HI)), reading.source)
        assertTrue(reading.units.isEmpty())
    }

    private fun readsScan(lang: String, expected: Lang) {
        val book = openScan(lang, TesseractEngine(AssetTessdata(root, Lang.entries.toSet())))
        val started = System.nanoTime()
        val reading = requireNotNull(book.readPage(0, ReadingUnitBuilder()).getOrNull())
        val ms = (System.nanoTime() - started) / NANOS_PER_MS
        book.close()

        val text = reading.units.joinToString(" ") { it.text }
        val truth = TestPdfs.groundTruthPages("$lang-udhr-noto.pdf").first()
        val cer = characterErrorRate(normalized(truth), normalized(withoutFooter(text)))
        Log.i(TAG, "OCR $lang: source=${reading.source} units=${reading.units.size} cer=$cer firstReadMs=$ms")
        assertEquals(PageRoute.OCR, reading.routing.route)
        assertEquals(PageSource.Ocr(setOf(expected)), reading.source)
        val max = MAX_CER.getValue(expected)
        assertTrue("$lang CER $cer over $max", cer <= max)
    }

    private fun openScan(lang: String, engine: TesseractEngine): PdfBook {
        val name = "$lang-udhr-noto-scan200bad-p1.pdf"
        val factory = RenderedPageOcr.factory(engine, cache = null, bookKey = name, logContext = "test=$name")
        return requireNotNull(
            PdfBook.open(TestPdfs.engine(TestPdfs.file(name)), logContext = "test=$name", ocr = factory).getOrNull(),
        )
    }

    /** Serves the test APK's models from a folder laid out as Tesseract wants it. */
    private class AssetTessdata(
        private val root: File,
        private val installed: Set<Lang>,
        private val available: Set<Lang> = installed,
    ) : TessdataSource {
        override val revision = "test"

        override fun installed() = installed

        override fun dataRoot(langs: Set<Lang>): Outcome<File, OcrError> =
            langs.firstOrNull { it !in available }?.let { Outcome.Failure(OcrError.ModelMissing(it)) }
                ?: Outcome.Success(root)
    }

    companion object {
        private const val TAG = "TesseractOcrTest"
        private const val NANOS_PER_MS = 1_000_000L

        /**
         * RI-3's done-when (ADR-035). Desktop Tesseract 5 with the same models, the line retry and the word repair
         * scores 0.1 % (en), 0.4 % (hi) and 1.2 % (bn) on these pages; before RI-3 the Hindi page read its English
         * header as Devanagari noise (3.9 %).
         */
        private val MAX_CER = mapOf(Lang.EN to 0.020, Lang.HI to 0.035, Lang.BN to 0.035)

        /**
         * The header printed on every page, "Audora S2 test corpus - hi-udhr-noto", which the ground truth leaves
         * out. It has no page number: the old pattern required one, never matched, and counted the header as errors.
         */
        private val FOOTER = Regex("Audora\\s+\\S+\\s+test corpus\\s*\\S?\\s*\\S+(?:\\s+\\d+)?")

        private lateinit var root: File

        @BeforeClass
        @JvmStatic
        fun copyModels() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            root = File(instrumentation.targetContext.cacheDir, "tess-test")
            val tessdata = File(root, "tessdata").apply { mkdirs() }
            val assets = instrumentation.context.assets
            for (file in requireNotNull(assets.list("ocr/tessdata"))) {
                assets.open("ocr/tessdata/$file").use { input ->
                    File(tessdata, file).outputStream().use { input.copyTo(it) }
                }
            }
            assertEquals(setOf("ben.traineddata", "eng.traineddata", "hin.traineddata"), tessdata.list()?.toSet())
        }

        @AfterClass
        @JvmStatic
        fun deleteModels() {
            root.deleteRecursively()
        }

        private fun withoutFooter(text: String) = text.replace(FOOTER, " ")

        private fun normalized(text: String) = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
