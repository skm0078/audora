// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/pdfium/LargeBookMemoryTest.kt
// Purpose: P1-02 done-when, part 2: a 500-page PDF read page at a time stays within a memory bound.

package io.github.skm0078.audora.pdf.pdfium

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeBookMemoryTest {
    @Test
    fun fiveHundredPagesReadInOrderReopenEveryHundredAndStayBounded() {
        val run = readWholeBook(reopenEveryPages = REOPEN_EVERY)
        assertEquals("opens", PAGES / REOPEN_EVERY, run.opens)
        assertTrue("native heap grew ${run.nativeGrowthMb} MB", run.nativeGrowthMb <= MAX_NATIVE_GROWTH_MB)
        assertTrue("Java heap grew ${run.javaGrowthMb} MB", run.javaGrowthMb <= MAX_JAVA_GROWTH_MB)
    }

    @Test
    fun reopeningHoldsLessNativeMemoryThanKeepingTheDocumentOpen() {
        // Not a bound, a regression signal: if this inverts, the reopen rule stopped doing its job.
        val withReopen = readWholeBook(reopenEveryPages = REOPEN_EVERY)
        val withoutReopen = readWholeBook(reopenEveryPages = PAGES + 1)
        assertEquals(1, withoutReopen.opens)
        assertTrue(
            "with reopen ${withReopen.nativeGrowthMb} MB, without ${withoutReopen.nativeGrowthMb} MB",
            withReopen.nativeGrowthMb <= withoutReopen.nativeGrowthMb + NOISE_MB,
        )
    }

    private data class Run(val opens: Int, val nativeGrowthMb: Double, val javaGrowthMb: Double)

    private fun readWholeBook(reopenEveryPages: Int): Run {
        val engine = TestPdfs.engine(book, ExtractionConfig(reopenEveryPages = reopenEveryPages))
        val builder = ReadingUnitBuilder()
        val opened = requireNotNull(PdfBook.open(engine, logContext = "test=memory").getOrNull())
        return opened.use { pdf ->
            assertEquals(PAGES, pdf.pageCount)
            var nativeBaseline = 0L
            var javaBaseline = 0L
            for (page in 0 until PAGES) {
                // Measure after a warm-up page, so library start-up is not counted as growth.
                if (page == WARM_UP_PAGES) {
                    nativeBaseline = settledNativeHeap()
                    javaBaseline = settledJavaHeap()
                }
                val units = requireNotNull(pdf.readingUnits(page, builder).getOrNull()) { "page $page failed" }
                assertEquals(GeneratedPdf.firstSentence(page), units.first().text)
                assertEquals(LINES_PER_PAGE, units.size)
            }
            val nativeGrowth = (settledNativeHeap() - nativeBaseline) / BYTES_PER_MB
            val javaGrowth = (settledJavaHeap() - javaBaseline) / BYTES_PER_MB
            Log.i(
                TAG,
                "reopenEvery=$reopenEveryPages opens=${pdf.opens} " +
                    "nativeGrowthMb=$nativeGrowth javaGrowthMb=$javaGrowth",
            )
            Run(pdf.opens, nativeGrowth, javaGrowth)
        }
    }

    private fun settledNativeHeap(): Long {
        settle()
        return Debug.getNativeHeapAllocatedSize()
    }

    private fun settledJavaHeap(): Long {
        settle()
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun settle() {
        repeat(2) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
    }

    companion object {
        private const val TAG = "AudoraMemoryTest"
        private const val PAGES = 500
        private const val REOPEN_EVERY = 100
        private const val LINES_PER_PAGE = 40
        private const val WARM_UP_PAGES = 5
        private const val MAX_NATIVE_GROWTH_MB = 24.0
        private const val MAX_JAVA_GROWTH_MB = 16.0
        private const val NOISE_MB = 2.0
        private const val BYTES_PER_MB = 1024.0 * 1024.0

        private lateinit var book: File

        @BeforeClass
        @JvmStatic
        fun writeBook() {
            val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            book = File(cache, "generated-$PAGES-pages.pdf")
            GeneratedPdf.write(book, PAGES, LINES_PER_PAGE)
        }
    }
}
