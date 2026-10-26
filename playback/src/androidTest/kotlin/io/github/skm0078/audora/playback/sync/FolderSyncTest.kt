// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/sync/FolderSyncTest.kt
// Purpose: Two devices sync through one folder (CF-16): a book copied in by one is added by the other with its place,
//          bookmark and highlight; a removal on one removes on the other; a removed book is not added back.

package io.github.skm0078.audora.playback.sync

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.library.AddResult
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookFingerprinter
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.DocumentAccess
import io.github.skm0078.audora.core.library.DocumentError
import io.github.skm0078.audora.core.library.DocumentInfo
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.sync.SyncSettings
import io.github.skm0078.audora.playback.storage.AudoraStorage
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderSyncTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var phone: Device
    private lateinit var tablet: Device
    private var now = 1_000_000L

    /** One device: its own database, library and sync state, syncing through the shared folder. */
    private inner class Device(name: String) {
        val storage = AudoraStorage.inMemory(context)
        val library = Library(storage.books, FileDocuments, BookFingerprinter(), clock = { now })
        val sync = FolderSync(
            context,
            SyncStores(library, storage.resumeAnchors, storage.bookmarks, storage.highlights, FileDocuments),
            clock = { now },
            directories = { FileDirectory(File(root, "folder")) },
            stateDir = File(root, name).apply { mkdirs() },
        )

        init {
            File(root, "$name/folder-sync.txt").writeText(SyncSettings(treeUri = "test:folder").encode())
        }

        suspend fun books() = library.books().first()
    }

    @Before
    fun setUp() {
        root = File(context.cacheDir, "folder-sync-test").apply { deleteRecursively() }
        File(root, "folder").mkdirs()
        phone = Device("phone")
        tablet = Device("tablet")
    }

    @After
    fun tearDown() {
        phone.storage.close()
        tablet.storage.close()
        root.deleteRecursively()
    }

    @Test
    fun aBookItsPlaceAndItsMarksReachTheOtherDevice() = runBlocking {
        val book = addBook(phone, "book.pdf")
        phone.storage.resumeAnchors.put(anchor(book.id, page = 4, at = 10L))
        phone.storage.bookmarks.add(Bookmark(0, anchor(book.id, page = 2, at = 20L), "my note"))
        phone.storage.highlights.add(Highlight(0, anchor(book.id, page = 3, at = 30L), 3, 40, "a quote"))

        assertTrue(phone.sync.sync(force = true) is SyncResult.Synced)
        val result = tablet.sync.sync(force = true) as SyncResult.Synced

        assertEquals(1, result.added)
        val copy = tablet.books().single()
        assertEquals(book.fingerprint, copy.fingerprint)
        assertEquals(4, tablet.storage.resumeAnchors.get(copy.id)?.page)
        assertEquals("my note", tablet.storage.bookmarks.observe(copy.id).first().single().note)
        assertEquals("a quote", tablet.storage.highlights.observe(copy.id).first().single().quote)
    }

    @Test
    fun aRemovedBookmarkIsRemovedOnTheOtherDeviceToo() = runBlocking {
        val book = addBook(phone, "book.pdf")
        phone.storage.bookmarks.add(Bookmark(0, anchor(book.id, page = 2, at = 20L)))
        phone.sync.sync(force = true)
        tablet.sync.sync(force = true)
        val copy = tablet.books().single()
        val mark = tablet.storage.bookmarks.observe(copy.id).first().single()

        tablet.storage.bookmarks.delete(mark.id)
        tablet.sync.sync(force = true)
        phone.sync.sync(force = true)

        assertTrue(phone.storage.bookmarks.observe(book.id).first().isEmpty())
    }

    @Test
    fun theNewestPlaceWins() = runBlocking {
        val book = addBook(phone, "book.pdf")
        phone.storage.resumeAnchors.put(anchor(book.id, page = 4, at = 10L))
        phone.sync.sync(force = true)
        tablet.sync.sync(force = true)
        val copy = tablet.books().single()

        tablet.storage.resumeAnchors.put(anchor(copy.id, page = 9, at = 50L))
        tablet.sync.sync(force = true)
        phone.sync.sync(force = true)

        assertEquals(9, phone.storage.resumeAnchors.get(book.id)?.page)
    }

    @Test
    fun aBookRemovedHereIsNotAddedBackFromTheFolder() = runBlocking {
        addBook(phone, "book.pdf")
        phone.sync.sync(force = true)
        tablet.sync.sync(force = true)
        tablet.library.remove(tablet.books().single().id)

        tablet.sync.sync(force = true)
        tablet.sync.sync(force = true)

        assertTrue(tablet.books().isEmpty())
        assertEquals(1, phone.books().size)
    }

    @Test
    fun aSyncWithinAMinuteIsSkippedUnlessAsked() = runBlocking {
        phone.sync.sync(force = true)
        now += 1_000L
        assertEquals(SyncResult.Skipped, phone.sync.sync(force = false))
        assertTrue(phone.sync.sync(force = true) is SyncResult.Synced)
    }

    private suspend fun addBook(device: Device, name: String) = File(root, name).run {
        writeBytes("%PDF-1.4\n% $name\n".toByteArray() + ByteArray(PADDING) { it.toByte() })
        val added = device.library.add(Uri.fromFile(this).toString())
        ((added as Outcome.Success).value as AddResult.Added).book
    }

    private fun anchor(bookId: BookId, page: Int, at: Long) =
        ResumeAnchor(bookId, page, charStart = 5, fingerprint = 11L, tailFingerprint = 12L, 13, savedAtMillis = at)

    private companion object {
        const val PADDING = 4_096
    }
}

/** Plain files by path, standing in for the provider's documents. */
private object FileDocuments : DocumentAccess {
    override fun keepAccess(documentUri: String): Outcome<Unit, DocumentError> = Outcome.Success(Unit)

    override fun releaseAccess(documentUri: String) = Unit

    override fun describe(documentUri: String): Outcome<DocumentInfo, DocumentError> =
        fileOf(documentUri).let { Outcome.Success(DocumentInfo(it.name, it.length())) }

    override fun openStream(documentUri: String): Outcome<InputStream, DocumentError> = try {
        Outcome.Success(FileInputStream(fileOf(documentUri)))
    } catch (e: IOException) {
        Outcome.Failure(DocumentError.NotFound(e.javaClass.simpleName))
    }

    override fun availability(documentUri: String) = BookAvailability.AVAILABLE

    private fun fileOf(documentUri: String) = File(requireNotNull(Uri.parse(documentUri).path))
}

/** The sync folder as a plain directory; an entry's id is its path below [root]. */
private class FileDirectory(private val root: File) : SyncDirectory {
    override fun rootId() = ""

    override fun folder(parentId: String, name: String): String = "$parentId/$name".also { File(root, it).mkdirs() }

    override fun list(folderId: String): List<SyncEntry> = File(root, folderId).listFiles().orEmpty().map {
        SyncEntry("$folderId/${it.name}", it.name, it.isDirectory, if (it.isDirectory) null else it.length())
    }

    override fun readText(id: String) = File(root, id).readText()

    override fun writeText(folderId: String, name: String, text: String) = File(root, "$folderId/$name").writeText(text)

    override fun copyInto(folderId: String, name: String, mimeType: String, from: InputStream) {
        File(root, "$folderId/$name").outputStream().use { from.copyTo(it) }
    }

    override fun documentUri(id: String): String = Uri.fromFile(File(root, id)).toString()
}
