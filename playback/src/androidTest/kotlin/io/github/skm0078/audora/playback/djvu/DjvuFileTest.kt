// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/djvu/DjvuFileTest.kt
// Purpose: DjVuLibre on the device (ADR 2026-10-27-djvu-books): the native library loads, a text-layer DjVu reads as
//          a text book with its outline and Hindi text, a scan draws its pages, and a file that is not a DjVu is
//          refused with an exception, not a crash.

package io.github.skm0078.audora.playback.djvu

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DjvuFileTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var folder: File

    @Before
    fun setUp() {
        folder = File(context.cacheDir, "djvu-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun asset(name: String): Uri {
        val file = File(folder, name)
        instrumentation.context.assets.open("djvu/$name").use { input -> file.outputStream().use(input::copyTo) }
        return Uri.fromFile(file)
    }

    private fun open(uri: Uri) = DjvuFile.open(context, uri, MAX_COPY_BYTES)

    @Test
    fun aTextLayerDjvuReadsAsATextBook() {
        open(asset("text.djvu")).use { djvu ->
            assertEquals(3, djvu.pageCount)
            assertTrue(DjvuParser.hasTextLayer(djvu))
            val document = (DjvuParser.parse(djvu, Long.MAX_VALUE) as Outcome.Success).value
            assertEquals("A DjVu Journey Book", document.title)
            assertEquals(listOf("Chapter One", "Chapter Two", "Hindi page"), document.chapters.map { it.title })
            val first = document.chapters[0].blocks[1].text
            assertTrue(first, first.startsWith("The first page of a small DjVu book has a remarkable sentence"))
            assertEquals("मन जहाँ डर से परे है।", document.chapters[2].blocks.single().text)
            assertEquals("ListenLock Tests", DjvuParser.metadata(djvu.annotations(), "author"))
        }
    }

    @Test
    fun aScanHasNoTextLayerAndDrawsItsPages() {
        open(asset("scan.djvu")).use { djvu ->
            assertEquals(2, djvu.pageCount)
            assertFalse(DjvuParser.hasTextLayer(djvu))
            val picture = djvu.pagePicture(0, maxLongSidePx = 550)
            try {
                assertEquals(Bitmap.Config.ARGB_8888, picture.config)
                assertEquals(550, maxOf(picture.width, picture.height))
                assertEquals(Color.WHITE, picture.getPixel(0, 0))
                assertTrue("no ink drawn on the page", darkPixels(picture) > 0)
            } finally {
                picture.recycle()
            }
        }
    }

    @Test
    fun aPictureIsNeverLargerThanThePage() {
        open(asset("scan.djvu")).use { djvu ->
            val picture = djvu.pagePicture(1, maxLongSidePx = 10_000)
            assertEquals(1100, picture.height)
            picture.recycle()
        }
    }

    @Test
    fun aFileThatIsNotADjvuIsRefused() {
        val file = File(folder, "bad.djvu").apply { writeText("not a djvu file at all") }
        assertThrows(DjvuException::class.java) { open(Uri.fromFile(file)) }
    }

    @Test
    fun aClosedFileIsNotUsedAgain() {
        val djvu = open(asset("text.djvu"))
        djvu.close()
        djvu.close()
        assertThrows(IllegalStateException::class.java) { djvu.pageText(0) }
    }

    private fun darkPixels(picture: Bitmap): Int {
        var dark = 0
        for (y in 0 until picture.height step 2) {
            for (x in 0 until picture.width step 2) if (Color.red(picture.getPixel(x, y)) < DARK) dark++
        }
        return dark
    }

    private companion object {
        const val MAX_COPY_BYTES = 16L * 1024 * 1024
        const val DARK = 128
    }
}
