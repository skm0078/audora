// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/pdfium/PdfiumExtractionTest.kt
// Purpose: P1-02 done-when, part 1: real PDFium + our JNI library on the test PDFs, on a device or emulator.

package io.github.skm0078.audora.pdf.pdfium

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.extract.ActualTextRebuild
import io.github.skm0078.audora.pdf.extract.RawChars
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfiumExtractionTest {
    private val builder = ReadingUnitBuilder()

    @Test
    fun englishPdfReadsExactlyWithFontsBoxesAndSentences() {
        val book = requireNotNull(TestPdfs.open(EN).getOrNull())
        book.use {
            assertEquals(8, it.pageCount)
            assertPagesMatchGroundTruth(it, EN)
            val page = requireNotNull(it.pageText(0).getOrNull())
            assertEquals(0, page.stats.unicodeMapErrors)
            assertTrue(page.fontRuns.any { run -> run.fontName.contains("NotoSerif") && run.fontSize > 0f })
            val units = builder.build(page)
            assertTrue("sentences: ${units.size}", units.size >= MIN_SENTENCES_ON_PAGE)
            units.forEach { unit ->
                assertEquals(unit.text, collapseWhitespace(page.text.substring(unit.charStart, unit.charEnd)))
                assertEquals(Lang.EN, unit.lang)
                assertTrue("unit ${unit.index} has no bounds", !unit.bounds.isNullOrEmpty())
            }
        }
    }

    @Test
    fun hindiPdfActualTextSpansAreRebuilt() {
        val book = requireNotNull(TestPdfs.open(HI).getOrNull())
        book.use {
            assertEquals(12, it.pageCount)
            assertPagesMatchGroundTruth(it, HI, requireRebuild = true)
            assertTrue(builder.build(requireNotNull(it.pageText(1).getOrNull())).all { unit -> unit.lang == Lang.HI })
        }
    }

    @Test
    fun bengaliPdfActualTextSpansAreRebuilt() {
        val book = requireNotNull(TestPdfs.open(BN).getOrNull())
        book.use {
            assertEquals(10, it.pageCount)
            assertPagesMatchGroundTruth(it, BN, requireRebuild = true)
        }
    }

    @Test
    fun jniLibraryReportsRenderModesAndFlags() {
        val handle = requireNotNull(TestPdfs.engine(TestPdfs.file(EN)).open(null).getOrNull())
        handle.use {
            val chars = requireNotNull(it.readPage(0).getOrNull()).chars
            assertTrue(chars.size > 0)
            // Visible fill text is render mode 0; nothing on these pages is invisible.
            assertTrue((0 until chars.size).any { i -> chars.renderMode(i) == 0 })
            assertTrue((0 until chars.size).none { i -> chars.renderMode(i) == RawChars.RENDER_MODE_INVISIBLE })
            assertTrue((0 until chars.size).any { i -> chars.isGenerated(i) })
            assertTrue((0 until chars.size).none { i -> chars.hasUnicodeMapError(i) })
        }
    }

    @Test
    fun passwordProtectedPdfAsksForThePasswordAndOpensWithIt() {
        assertEquals(PdfOpenError.PasswordRequired, TestPdfs.open(ENCRYPTED).errorOrNull())
        assertEquals(PdfOpenError.IncorrectPassword, TestPdfs.open(ENCRYPTED, password = "wrong").errorOrNull())
        val book = requireNotNull(TestPdfs.open(ENCRYPTED, password = ENCRYPTED_PASSWORD).getOrNull())
        book.use {
            assertEquals(1, it.pageCount)
            val text = requireNotNull(it.pageText(0).getOrNull()).text
            assertTrue(text.startsWith("Lorem ipsum dolor sit amet"))
        }
    }

    @Test
    fun damagedAndMissingFilesAreTypedErrors() {
        val garbage = File(TestPdfs.file(EN).parentFile, "garbage.pdf").apply { writeText("this is not a PDF") }
        val damaged = PdfBook.open(TestPdfs.engine(garbage)).errorOrNull()
        assertTrue("$damaged", damaged is PdfOpenError.Damaged)

        val missing = PdfiumEngine(openFile = { throw FileNotFoundException("gone") }).open(null).errorOrNull()
        assertTrue("$missing", missing is PdfOpenError.Unreadable)
    }

    private fun assertPagesMatchGroundTruth(book: PdfBook, name: String, requireRebuild: Boolean = false) {
        val expected = TestPdfs.groundTruthPages(name)
        assertEquals(expected.size, book.pageCount)
        expected.forEachIndexed { index, truth ->
            val page = requireNotNull(book.pageText(index).getOrNull()) { "page $index of $name failed" }
            if (requireRebuild) assertEquals("page $index", ActualTextRebuild.APPLIED, page.stats.actualTextRebuild)
            val cer = characterErrorRate(comparable(truth), comparable(page.text))
            assertTrue("$name page $index CER=$cer", cer <= MAX_CER)
        }
    }

    private companion object {
        const val EN = "en-udhr-noto.pdf"
        const val HI = "hi-udhr-noto.pdf"
        const val BN = "bn-udhr-noto.pdf"
        const val ENCRYPTED = "encrypted-userpassword-libreoffice.pdf"
        const val ENCRYPTED_PASSWORD = "openpassword"
        const val MAX_CER = 0.01
        const val MIN_SENTENCES_ON_PAGE = 3
    }
}
