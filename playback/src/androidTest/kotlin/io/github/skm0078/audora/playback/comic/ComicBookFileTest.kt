// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/comic/ComicBookFileTest.kt
// Purpose: Comic archives on the device: a damaged or oversize page is left blank instead of losing the book or
//          running out of memory, a comic of only broken pages is still refused, and page names that are not UTF-8
//          are read and found again (ZipNames).

package io.github.skm0078.audora.playback.comic

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.playback.text.ZipNames
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicBookFileTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var folder: File

    @Before
    fun setUp() {
        folder = File(context.cacheDir, "comic-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun comic(config: PhotoConfig = PhotoConfig()) = ComicBookFile(context.contentResolver, folder, config)

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        return out.toByteArray()
    }

    private fun archive(pages: Map<String, ByteArray>, charset: Charset = Charsets.UTF_8): File {
        val file = File(folder, "comic.cbz")
        ZipOutputStream(file.outputStream(), charset).use { zip ->
            pages.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun isPdf(file: File): Boolean =
        file.isFile && file.readBytes().copyOfRange(0, PDF_MAGIC.length).decodeToString() == PDF_MAGIC

    @Test
    fun aDamagedPageAndAnOversizePageAreLeftBlankAndTheRestIsKept() {
        val config = PhotoConfig(maxComicPageBytes = PAGE_LIMIT_BYTES)
        val copy = archive(
            mapOf(
                "p1.png" to png(),
                "p2.png" to "not a picture".toByteArray(),
                "p3.png" to ByteArray(PAGE_LIMIT_BYTES + 1),
                "p4.png" to png(),
            ),
        )
        val target = File(folder, "book.pdf")
        val file = comic(config)

        val blank = file.write(copy, file.pages(copy), target)

        assertEquals(2, blank)
        assertTrue("A PDF should be written", isPdf(target))
    }

    @Test
    fun aComicOfOnlyBrokenPagesIsStillRefused() {
        val copy = archive(mapOf("p1.png" to "x".toByteArray(), "p2.png" to "y".toByteArray()))
        val file = comic()

        assertThrows(IOException::class.java) { file.write(copy, file.pages(copy), File(folder, "book.pdf")) }
    }

    @Test
    fun pageNamesThatAreNotUtf8AreReadAndFoundAgain() {
        val copy = archive(mapOf("página1.png" to png(), "página2.png" to png()), Charsets.ISO_8859_1)
        val file = comic()

        val pages = file.pages(copy)
        val blank = file.write(copy, pages, File(folder, "book.pdf"))

        assertEquals(ZipNames.FALLBACK, pages.charset)
        assertEquals(listOf("página1.png", "página2.png"), pages.names)
        assertEquals(0, blank)
    }

    private companion object {
        const val PAGE_WIDTH = 40
        const val PAGE_HEIGHT = 60
        const val PNG_QUALITY = 100
        const val PAGE_LIMIT_BYTES = 64 * 1024
        const val PDF_MAGIC = "%PDF"
    }
}
