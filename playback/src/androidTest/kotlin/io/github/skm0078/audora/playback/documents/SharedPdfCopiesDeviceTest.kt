// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/documents/SharedPdfCopiesDeviceTest.kt
// Purpose: Shared PDF copies on a device: a restored copy opens everywhere, and the prune keeps what books use.

package io.github.skm0078.audora.playback.documents

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPdfCopiesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dir = File(context.filesDir, SHARED_COPIES_DIR)
    private val made = mutableListOf<File>()

    @After
    fun cleanUp() {
        made.forEach { it.deleteRecursively() }
    }

    @Test
    fun aCopyRestoredFromAnotherUserOpensInEveryReader() {
        val file = copyIn(HINDI_NAME)
        // The URI a backup from another user (or the debug build) carries: the same last segments, another root.
        val restored = Uri.fromFile(File("$OTHER_FILES/$SHARED_COPIES_DIR/${file.parentFile!!.name}/$HINDI_NAME"))
        val copies = SharedPdfCopies(context)
        val access = SafDocumentAccess(context, copies)

        assertEquals(Uri.fromFile(file), SharedPdfCopies.readableUri(context, restored.toString()))
        assertEquals(BookAvailability.AVAILABLE, access.availability(restored.toString()))
        val stream = access.openStream(restored.toString())
        assertTrue("$stream", stream is Outcome.Success)
        val read = (stream as Outcome.Success).value.use { it.readBytes() }
        assertArrayEquals(file.readBytes(), read)
        // What the player, the reader, the chapter list and the covers open.
        val engine = PdfiumEngine.forUri(context, SharedPdfCopies.readableUri(context, restored.toString()))
        val book = PdfBook.open(engine, logContext = "test=restored-copy").getOrNull()
        assertNotNull(book)
        book!!.use { assertTrue(it.pageCount > 0) }
    }

    @Test
    fun aContentUriIsReadAsItIs() {
        val uri = "content://com.android.providers.downloads.documents/document/42"
        assertEquals(Uri.parse(uri), SharedPdfCopies.readableUri(context, uri))
    }

    @Test
    fun pruneKeepsCopiesBooksUseAndNewOnesAndDeletesOldStrays() {
        val used = copyIn(BENGALI_NAME, ageMs = DAY_MS)
        val stray = copyIn(HINDI_NAME, ageMs = DAY_MS)
        val fresh = copyIn("fresh.pdf")
        val restoredUse = "file:///data/user/0/io.github.skm0078.audora.debug/files/$SHARED_COPIES_DIR/" +
            "${used.parentFile!!.name}/${Uri.encode(BENGALI_NAME)}"

        SharedPdfCopies(context).prune(setOf(restoredUse, "content://elsewhere/document/1"))

        assertTrue("a book's copy is kept", used.isFile)
        assertTrue("a copy newer than this process is kept", fresh.isFile)
        assertFalse("an old copy no book uses is deleted", stray.parentFile!!.exists())
    }

    @Test
    fun aCopyMadeByThisProcessIsNeverPrunedWhateverItsTime() = runBlocking {
        val source = copyIn("source.pdf")
        val copies = SharedPdfCopies(context)
        val copied = copies.copy(Uri.fromFile(source).toString())
        assertNotNull(copied)
        val file = copies.fileOf(Uri.parse(copied))
        made += file.parentFile!!
        // As if the clock had jumped forward since: the copy's time now looks older than the process.
        file.setLastModified(System.currentTimeMillis() - DAY_MS)
        file.parentFile!!.setLastModified(System.currentTimeMillis() - DAY_MS)

        copies.prune(emptySet())

        assertTrue(file.isFile)
    }

    /** A test PDF copied into a new copy folder, its times set [ageMs] in the past. */
    private fun copyIn(name: String, ageMs: Long = 0L): File {
        val folder = File(dir, UUID.randomUUID().toString()).also { it.mkdirs() }
        made += folder
        val file = File(folder, name)
        instrumentation.context.assets.open("pdfs/$PDF").use { input -> file.outputStream().use(input::copyTo) }
        if (ageMs > 0) {
            val time = System.currentTimeMillis() - ageMs
            file.setLastModified(time)
            folder.setLastModified(time)
        }
        return file
    }

    private companion object {
        const val PDF = "enhi-udhr-bilingual.pdf"
        const val OTHER_FILES = "/data/user/10/io.other.app/files"
        const val HINDI_NAME = "मानव अधिकार.pdf"
        const val BENGALI_NAME = "মানবাধিকার.pdf"
        const val DAY_MS = 86_400_000L
    }
}
